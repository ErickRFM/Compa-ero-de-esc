package org.companerodeescuela.api.integrations.academic

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bson.Document
import org.companerodeescuela.api.academic.InMemoryAcademicScheduleOverrideRepository
import org.companerodeescuela.api.academic.NativeAcademicProjection
import org.companerodeescuela.api.academic.academicRoutes
import org.companerodeescuela.api.academic.groups.InMemoryAcademicGroupRepository
import org.companerodeescuela.api.attendance.AttendanceAccessPolicy
import org.companerodeescuela.api.attendance.AttendanceAdministrationPolicy
import org.companerodeescuela.api.attendance.AttendanceEnrollmentResolver
import org.companerodeescuela.api.attendance.AttendanceReviewService
import org.companerodeescuela.api.attendance.AttendanceSessionService
import org.companerodeescuela.api.attendance.AttendanceStudentService
import org.companerodeescuela.api.attendance.MongoAttendanceRepository
import org.companerodeescuela.api.attendance.ProviderAttendanceOccurrenceResolver
import org.companerodeescuela.api.attendance.SiiiAttendanceAuthority
import org.companerodeescuela.api.attendance.attendanceRoutes
import org.companerodeescuela.api.auth.AuthTokenService
import org.companerodeescuela.api.auth.InMemoryPlatformAccountRepository
import org.companerodeescuela.api.auth.InMemoryRefreshSessionRepository
import org.companerodeescuela.api.auth.PlatformAccount
import org.companerodeescuela.api.auth.PlatformSessionAuthority
import org.companerodeescuela.api.auth.RefreshSession
import org.companerodeescuela.api.auth.SessionIdentitySource
import org.companerodeescuela.api.classroom.ClassroomService
import org.companerodeescuela.api.classroom.InMemoryClassroomRepository
import org.companerodeescuela.api.devices.InMemoryDeviceTokenRepository
import org.companerodeescuela.api.devices.MongoPushOutboxRepository
import org.companerodeescuela.api.devices.PushDeliveryResult
import org.companerodeescuela.api.devices.PushGateway
import org.companerodeescuela.api.devices.PushOutboxService
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.institutions.MongoInstitutionRepository
import org.companerodeescuela.api.integrations.academic.siii.MongoSiiiSectionSnapshotRepository
import org.companerodeescuela.api.integrations.academic.siii.SiiiAcademicService
import org.companerodeescuela.api.integrations.academic.siii.SiiiAuthorityTarget
import org.companerodeescuela.api.integrations.academic.siii.SiiiHttpAcademicAuthority
import org.companerodeescuela.api.integrations.academic.siii.SiiiSectionEventAdapter
import org.companerodeescuela.api.integrations.academic.siii.SiiiSectionProjectionHandler
import org.companerodeescuela.api.integrations.academic.siii.SiiiSectionReader
import org.companerodeescuela.api.integrations.mock.MockAcademicProvider
import org.companerodeescuela.api.integrations.mock.MockIdentityProvider
import org.companerodeescuela.api.integrations.push.InstitutionalEventProjectionWorker
import org.companerodeescuela.api.integrations.push.InstitutionalIdentityLink
import org.companerodeescuela.api.integrations.push.InstitutionalIdentityLinkService
import org.companerodeescuela.api.integrations.push.InstitutionalProjectionIdentityResolver
import org.companerodeescuela.api.integrations.push.InstitutionalPushAuthenticator
import org.companerodeescuela.api.integrations.push.InstitutionalPushService
import org.companerodeescuela.api.integrations.push.InstitutionalSecretProtector
import org.companerodeescuela.api.integrations.push.IntegrationCredential
import org.companerodeescuela.api.integrations.push.MongoInstitutionalIdentityLinkRepository
import org.companerodeescuela.api.integrations.push.MongoInstitutionalPushRepository
import org.companerodeescuela.api.integrations.push.institutionalPushRoutes
import org.companerodeescuela.api.plugins.ApiJson
import org.companerodeescuela.api.plugins.configurePlugins
import org.companerodeescuela.api.presence.MongoSchoolPresenceRepository
import org.companerodeescuela.api.presence.SchoolPresencePolicy
import org.companerodeescuela.api.presence.SchoolPresenceService
import org.companerodeescuela.api.test.TestFixtures
import org.companerodeescuela.shared.contracts.AcademicLoadResponse
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.AttendanceRecordResponse
import org.companerodeescuela.shared.contracts.AttendanceRosterResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse
import org.companerodeescuela.shared.contracts.AttendanceStatus
import org.companerodeescuela.shared.contracts.ClassCallConfirmationRequest
import org.companerodeescuela.shared.contracts.CreateAttendanceSessionRequest
import org.companerodeescuela.shared.contracts.InstitutionalIdentityKind
import org.companerodeescuela.shared.contracts.SchoolNetworkEvidence
import org.companerodeescuela.shared.contracts.StartSchoolPresenceRequest
import org.companerodeescuela.shared.contracts.UserRole
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Synthetic PostgreSQL outbox -> SIII HMAC sender -> loopback Ktor -> Mongo projection -> authorized reader. */
@EnabledIfEnvironmentVariable(named = "COMPANION_CHAIN_PROBE_PROJECT_ROOT", matches = ".+")
@Timeout(300)
class SiiiFullChainLiveContractTest {
    @Test
    fun `postgres outbox is signed delivered projected authorized and revoked end to end`() =
        runBlocking {
            val postgresUrl = requireNotNull(System.getenv("COMPANION_CHAIN_PROBE_DATABASE_URL"))
            require(postgresUrl.startsWith("postgresql://postgres:postgres@127.0.0.1:55439/")) {
                "Chain probe requires the disposable loopback PostgreSQL service"
            }
            require(System.getenv("V12_TEST_MONGODB_URI") == "mongodb://127.0.0.1:27017") {
                "Chain probe requires the disposable loopback Mongo service"
            }
            val tenantId = UUID.randomUUID().toString()
            val keyId = "chain-${UUID.randomUUID()}"
            val secret = ByteArray(32).also(SecureRandom()::nextBytes)
            MongoClient.create("mongodb://127.0.0.1:27017").use { client ->
                val db = client.getDatabase("v12_test_${UUID.randomUUID().toString().replace("-", "")}")
                try {
                    runChain(db, postgresUrl, tenantId, keyId, secret)
                } finally {
                    db.drop()
                    secret.fill(0)
                }
            }
        }

    private suspend fun runChain(
        db: MongoDatabase,
        postgresUrl: String,
        tenantId: String,
        keyId: String,
        secret: ByteArray,
    ) {
        val inbox = MongoInstitutionalPushRepository(db, InstitutionalSecretProtector(randomKey()))
        val snapshots = MongoSiiiSectionSnapshotRepository(db)
        val identities = readerGraph(db, tenantId)
        inbox.putCredential(IntegrationCredential(keyId, tenantId, secret.copyOf(), setOf("group:write"), true, null), Instant.now())
        val server =
            embeddedServer(CIO, host = "127.0.0.1", port = 0) {
                install(ContentNegotiation) { json(ApiJson) }
                routing {
                    institutionalPushRoutes(
                        InstitutionalPushAuthenticator(inbox, inbox),
                        InstitutionalPushService(inbox),
                        inbox,
                        inbox,
                    )
                }
            }.start(wait = false)
        val probe =
            ChainProbe(
                postgresUrl,
                server.engine
                    .resolvedConnectors()
                    .single()
                    .port,
                tenantId,
                keyId,
                secret,
            )
        var closeReads: (() -> Unit)? = null
        try {
            val initialized = probe.start()
            val sectionId = initialized.string("sectionId")
            val studentExternal = initialized.string("studentExternal")
            val teacherExternal = initialized.string("teacherExternal")
            val student = identities.link(tenantId, InstitutionalIdentityKind.STUDENT, studentExternal, UserRole.STUDENT)
            val teacher = identities.link(tenantId, InstitutionalIdentityKind.TEACHER, teacherExternal, UserRole.TEACHER)
            val settings = TestFixtures.settings().copy(jwtSecret = "synthetic-chain-signing-key-32-bytes".toCharArray())
            val sessions = InMemoryRefreshSessionRepository()
            val accessTokens =
                listOf(student, teacher).associate { account ->
                    val user = account.toUserSummary()
                    val session = "chain-session-${account.id}"
                    sessions.create(
                        RefreshSession(
                            session,
                            "synthetic-hash",
                            user,
                            Instant.now(),
                            Instant.now().plusSeconds(3600),
                            identitySource = SessionIdentitySource.NATIVE,
                        ),
                    )
                    account.id to AuthTokenService(settings).issue(user, session).value
                }
            val sourceOrigin = initialized.string("sourceOrigin")
            require(sourceOrigin.matches(Regex("http://127\\.0\\.0\\.1:[0-9]+")))
            val authority =
                SiiiHttpAcademicAuthority(
                    mapOf(
                        tenantId to SiiiAuthorityTarget("https://synthetic-source.invalid", tenantId, keyId),
                    ),
                    inbox,
                    endpoint = { URI(sourceOrigin + SiiiHttpAcademicAuthority.PATH) },
                )
            val academic =
                SiiiAcademicService(identities.accounts, identities.links, identities.reader(snapshots), authority, setOf(tenantId))
            val attendanceClock = java.time.Clock.fixed(Instant.parse("2026-10-22T15:30:00Z"), java.time.ZoneOffset.UTC)
            val attendanceAuthority =
                SiiiAttendanceAuthority(
                    academic,
                    identities.reader(snapshots),
                    identities.links,
                    identities.identityLinks(),
                    identities.accounts,
                )
            val nativeGroups = InMemoryAcademicGroupRepository()
            val nativeClasses = ClassroomService(InMemoryClassroomRepository(), nativeGroups, accounts = identities.accounts)
            val nativeProjection =
                NativeAcademicProjection(
                    identities.accounts,
                    nativeClasses,
                    nativeGroups,
                    InMemoryAcademicScheduleOverrideRepository(),
                    institutionalAttendance = attendanceAuthority,
                )
            val attendanceRepository = MongoAttendanceRepository(db)
            val occurrenceResolver = ProviderAttendanceOccurrenceResolver(MockAcademicProvider())
            val access =
                AttendanceAccessPolicy(
                    attendanceRepository,
                    occurrenceResolver,
                    AttendanceAdministrationPolicy(identities.accounts, nativeClasses, nativeProjection),
                )
            val devices = InMemoryDeviceTokenRepository()
            val deviceToken = "fcm_synthetic_chain_student_123456"
            devices.register("NATIVE:${student.id}", deviceToken, "android", null, "chain-session-${student.id}")
            var notice: Map<String, String>? = null
            val push =
                PushOutboxService(
                    devices,
                    MongoPushOutboxRepository(db),
                    object : PushGateway {
                        override suspend fun send(
                            token: String,
                            title: String,
                            body: String,
                        ) = error("Session action is required")

                        override suspend fun send(
                            token: String,
                            title: String,
                            body: String,
                            data: Map<String, String>,
                        ): PushDeliveryResult {
                            notice = data
                            return PushDeliveryResult.ACCEPTED
                        }
                    },
                    // Mongo enqueue timestamps use wall time; its delivery lease must use the same clock.
                    java.time.Clock.systemUTC(),
                    authorizeRecipient = { it == "NATIVE:${student.id}" },
                    authorizeSession = { owner, token -> owner == "NATIVE:${student.id}" && token == deviceToken },
                )
            val entry = "synthetic-source-chain-school-entry"
            val entryHash =
                java.security.MessageDigest
                    .getInstance(
                        "SHA-256",
                    ).digest(entry.toByteArray())
                    .joinToString("") { "%02x".format(it) }
            val presence =
                SchoolPresenceService(
                    MongoSchoolPresenceRepository(db),
                    SchoolPresencePolicy(entryHash, setOf("CHAIN-QA"), emptySet(), institutionId = tenantId),
                    clock = attendanceClock,
                    accounts = identities.accounts,
                )
            val attendanceSessions =
                AttendanceSessionService(
                    attendanceRepository,
                    occurrenceResolver,
                    access,
                    attendanceClock,
                    nativeProjection = nativeProjection,
                    pushService = push,
                )
            val attendanceStudents =
                AttendanceStudentService(
                    attendanceRepository,
                    AttendanceEnrollmentResolver(MockAcademicProvider(), nativeClasses, attendanceAuthority),
                    schoolPresenceService = presence,
                    clock = attendanceClock,
                    nativeProjection = nativeProjection,
                )
            val readServer =
                embeddedServer(CIO, host = "127.0.0.1", port = 0) {
                    configurePlugins(settings, sessions, PlatformSessionAuthority(identities.accounts, MockIdentityProvider()))
                    routing {
                        academicRoutes(settings, MockAcademicProvider(), institutionalAcademic = academic)
                        attendanceRoutes(
                            settings,
                            attendanceSessions,
                            attendanceStudents,
                            AttendanceReviewService(attendanceRepository, access, attendanceClock),
                        )
                    }
                }.start(wait = false)
            closeReads = { readServer.stop(0, 5000) }
            val readPort =
                readServer.engine
                    .resolvedConnectors()
                    .single()
                    .port

            fun httpRead(
                account: PlatformAccount,
                suffix: String = "/load",
            ): Pair<Int, String> {
                val connection = URI("http://127.0.0.1:$readPort/academic$suffix").toURL().openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 5000
                    connection.readTimeout = 15000
                    connection.setRequestProperty("Authorization", "Bearer ${accessTokens.getValue(account.id)}")
                    val status = connection.responseCode
                    return status to (if (status == 200) connection.inputStream.bufferedReader().use { it.readText() } else "")
                } finally {
                    connection.disconnect()
                }
            }

            fun attendanceHttp(
                account: PlatformAccount,
                path: String,
                body: String? = null,
            ): Pair<Int, String> {
                val connection = URI("http://127.0.0.1:$readPort/attendance$path").toURL().openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 5000
                    connection.readTimeout = 30000
                    connection.setRequestProperty("Authorization", "Bearer ${accessTokens.getValue(account.id)}")
                    if (body != null) {
                        connection.requestMethod = "POST"
                        connection.doOutput = true
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.outputStream.use { it.write(body.toByteArray()) }
                    }
                    val status = connection.responseCode
                    return status to (if (status == 200) connection.inputStream.bufferedReader().use { it.readText() } else "")
                } finally {
                    connection.disconnect()
                }
            }

            val initial = probe.action("deliver")
            assertEquals("accepted", initial.receiptState(sectionId))
            projectAvailable(inbox, snapshots, tenantId)

            val assigned = probe.action("assign")
            val assignedVersion = assigned.long("sourceVersion")
            assertAccepted(probe.action("deliver"), assignedVersion)
            projectThrough(inbox, snapshots, tenantId, sectionId, assignedVersion)

            val enrolled = probe.action("enroll")
            val enrolledVersion = enrolled.long("sourceVersion")
            assertAccepted(probe.action("deliver"), enrolledVersion)
            assertEquals(503, httpRead(student).first, "Source version must be projected before HTTP can read it")
            projectThrough(inbox, snapshots, tenantId, sectionId, enrolledVersion)

            val scheduled = probe.action("schedule")
            assertTrue(scheduled.string("occurrenceId").isNotBlank())
            assertEquals("2026-10-22", scheduled.string("date"))
            val scheduleDelivery = probe.action("deliver")
            val scheduledVersion = scheduleDelivery.latestAcceptedVersionAfter(enrolledVersion)
            projectThrough(inbox, snapshots, tenantId, sectionId, scheduledVersion)

            val reader = identities.reader(snapshots)
            val studentView = reader.student(student.toUserSummary(), SessionIdentitySource.NATIVE, tenantId, sectionId, studentExternal)
            val teacherView = reader.teacher(teacher.toUserSummary(), SessionIdentitySource.NATIVE, tenantId, sectionId, teacherExternal)
            assertEquals(studentExternal, studentView.enrollment.studentExternalId)
            assertTrue(teacherView.enrollments.any { it.studentExternalId == studentExternal })
            assertEquals(sectionId, studentView.section.sectionId)
            val httpLoad = httpRead(student)
            assertEquals(200, httpLoad.first)
            val mobile = Json.decodeFromString<ApiResponse<AcademicLoadResponse>>(httpLoad.second).data
            assertEquals("SIII", mobile.institutionalSource)
            assertEquals("Synthetic subject", mobile.institutionalSections.single().subjectName)
            assertEquals(listOf("Synthetic Teacher"), mobile.institutionalSections.single().teacherNames)
            assertEquals(sectionId, mobile.institutionalSections.single().id)
            assertTrue(mobile.schedule.entries.isNotEmpty())
            assertEquals(200, httpRead(teacher).first)
            val meeting =
                mobile.institutionalSections
                    .single()
                    .occurrences
                    .single()
            val opened =
                attendanceHttp(teacher, "/sessions", Json.encodeToString(CreateAttendanceSessionRequest(meeting.id, meeting.date, 10)))
            assertEquals(200, opened.first)
            val pass = Json.decodeFromString<ApiResponse<AttendanceSessionResponse>>(opened.second).data
            assertEquals("SIII", pass.academicSource)
            assertEquals(pass, MongoAttendanceRepository(db).findSession(pass.id), "Session must survive a new Mongo repository")
            val discovery = attendanceHttp(student, "/sessions/active")
            assertEquals(200, discovery.first)
            assertEquals(listOf(pass), Json.decodeFromString<ApiResponse<List<AttendanceSessionResponse>>>(discovery.second).data)
            assertEquals(null, notice)
            assertTrue(push.dispatchNext())
            assertEquals(mapOf("type" to "attendance_session_opened", "sessionId" to pass.id), notice)
            presence.start(
                student.id,
                StartSchoolPresenceRequest(
                    "synthetic-entry",
                    entry,
                    SchoolNetworkEvidence(ssid = "CHAIN-QA"),
                    attendanceClock.instant().epochSecond,
                ),
                student.toUserSummary(),
            )
            val confirmed =
                attendanceHttp(
                    student,
                    "/sessions/${pass.id}/confirm",
                    Json.encodeToString(ClassCallConfirmationRequest("synthetic-confirm", SchoolNetworkEvidence(ssid = "CHAIN-QA"))),
                )
            assertEquals(200, confirmed.first)
            val record = Json.decodeFromString<ApiResponse<AttendanceRecordResponse>>(confirmed.second).data
            assertEquals(AttendanceStatus.REVIEW_REQUIRED, record.status, "Synthetic evidence cannot certify physical presence")
            val roster = attendanceHttp(teacher, "/sessions/${pass.id}/roster")
            assertEquals(200, roster.first)
            assertEquals(listOf(record), Json.decodeFromString<ApiResponse<AttendanceRosterResponse>>(roster.second).data.records)

            val withdrawn = probe.action("withdraw")
            val withdrawnVersion = withdrawn.long("sourceVersion")
            assertEquals(
                emptyList(),
                Json
                    .decodeFromString<ApiResponse<List<AttendanceSessionResponse>>>(
                        attendanceHttp(student, "/sessions/active").second,
                    ).data,
            )
            assertEquals(
                403,
                attendanceHttp(
                    student,
                    "/sessions/${pass.id}/confirm",
                    Json.encodeToString(ClassCallConfirmationRequest("revoked-confirm", SchoolNetworkEvidence(ssid = "CHAIN-QA"))),
                ).first,
            )
            assertEquals(
                404,
                httpRead(student, "/institutional/sections/$sectionId").first,
                "Live source revocation must apply before outbox delivery",
            )
            assertAccepted(probe.action("deliver"), withdrawnVersion)
            projectThrough(inbox, snapshots, tenantId, sectionId, withdrawnVersion)
            assertFailsWith<ApiException.Forbidden> {
                reader.student(student.toUserSummary(), SessionIdentitySource.NATIVE, tenantId, sectionId, studentExternal)
            }

            val unassigned = probe.action("unassign")
            val unassignedVersion = unassigned.long("sourceVersion")
            assertEquals(404, httpRead(teacher, "/institutional/sections/$sectionId").first)
            assertAccepted(probe.action("deliver"), unassignedVersion)
            projectThrough(inbox, snapshots, tenantId, sectionId, unassignedVersion)
            assertFailsWith<ApiException.Forbidden> {
                reader.teacher(teacher.toUserSummary(), SessionIdentitySource.NATIVE, tenantId, sectionId, teacherExternal)
            }
            assertTrue(db.getCollection<Document>("institutional_integration_events").countDocuments(Document("tenantId", tenantId)) >= 6)
        } finally {
            closeReads?.invoke()
            probe.close()
            server.stop(0, 5_000)
        }
    }

    private suspend fun projectAvailable(
        inbox: MongoInstitutionalPushRepository,
        snapshots: MongoSiiiSectionSnapshotRepository,
        tenantId: String,
    ) {
        val worker = worker(inbox, snapshots)
        withTimeout(20_000) {
            while (worker.processNext(tenantId, "group:v1")) Unit
        }
    }

    private suspend fun projectThrough(
        inbox: MongoInstitutionalPushRepository,
        snapshots: MongoSiiiSectionSnapshotRepository,
        tenantId: String,
        sectionId: String,
        version: Long,
    ) {
        val worker = worker(inbox, snapshots)
        withTimeout(20_000) {
            while ((snapshots.find(tenantId, sectionId)?.sourceVersion ?: 0L) < version) {
                if (!worker.processNext(tenantId, "group:v1")) kotlinx.coroutines.delay(10)
            }
        }
        assertEquals(version, snapshots.find(tenantId, sectionId)?.sourceVersion)
    }

    private fun worker(
        inbox: MongoInstitutionalPushRepository,
        snapshots: MongoSiiiSectionSnapshotRepository,
    ) = InstitutionalEventProjectionWorker(
        inbox,
        InstitutionalProjectionIdentityResolver { _, _ -> error("SIII group snapshot must not invent native identity authority") },
        SiiiSectionProjectionHandler(snapshots),
        listOf(SiiiSectionEventAdapter()),
    )

    private fun assertAccepted(
        result: JsonObject,
        version: Long,
    ) {
        assertTrue(result.hasAcceptedVersion(version), "outbox source version $version was not durably accepted")
    }

    private fun readerGraph(
        db: MongoDatabase,
        tenantId: String,
    ): ReaderGraph {
        val accounts = InMemoryPlatformAccountRepository()
        val institutions = MongoInstitutionRepository(db)
        val links = MongoInstitutionalIdentityLinkRepository(db)
        val snapshots = MongoSiiiSectionSnapshotRepository(db)
        return ReaderGraph(db, tenantId, accounts, institutions, links, snapshots)
    }

    private inner class ReaderGraph(
        private val db: MongoDatabase,
        private val tenantId: String,
        val accounts: InMemoryPlatformAccountRepository,
        private val institutions: MongoInstitutionRepository,
        val links: MongoInstitutionalIdentityLinkRepository,
        private val snapshots: MongoSiiiSectionSnapshotRepository,
    ) {
        suspend fun link(
            tenant: String,
            kind: InstitutionalIdentityKind,
            externalId: String,
            role: UserRole,
        ): PlatformAccount {
            db.getCollection<Document>("platform_institutions").replaceOne(
                Document("_id", tenant),
                Document("_id", tenant).append("displayName", "Synthetic chain tenant").append("registrationEnabled", true),
                com.mongodb.client.model
                    .ReplaceOptions()
                    .upsert(true),
            )
            val account =
                PlatformAccount(
                    id = UUID.randomUUID().toString(),
                    displayName = "Synthetic authorized reader",
                    email = "${UUID.randomUUID()}@example.test",
                    passwordHash = "fixture-hash",
                    roles = setOf(role),
                    institutionId = tenant,
                    emailVerifiedAt = Instant.parse("2026-10-01T00:00:00Z"),
                )
            accounts.create(account)
            links.createIfAbsent(
                InstitutionalIdentityLink(
                    tenant,
                    kind,
                    externalId,
                    account.id,
                    "synthetic-test-admin",
                    "synthetic-link-${UUID.randomUUID()}",
                    "a".repeat(64),
                    Instant.now(),
                ),
            )
            return account
        }

        fun reader(snapshots: MongoSiiiSectionSnapshotRepository) =
            SiiiSectionReader(
                accounts,
                institutions,
                identityLinks(),
                snapshots,
            )

        fun identityLinks() =
            InstitutionalIdentityLinkService(
                accounts,
                institutions,
                links,
                org.companerodeescuela.api.security.MfaService(
                    accounts,
                    org.companerodeescuela.api.security
                        .InMemoryMfaRepository(),
                    org.companerodeescuela.api.security
                        .MfaSecretCipher(randomKey()),
                ),
            )
    }

    private class ChainProbe(
        postgresUrl: String,
        receiverPort: Int,
        tenantId: String,
        keyId: String,
        secret: ByteArray,
    ) {
        private val project = Path.of(requireNotNull(System.getenv("COMPANION_CHAIN_PROBE_PROJECT_ROOT"))).toAbsolutePath().normalize()
        private val process: Process
        private val input: BufferedWriter
        private val output: BufferedReader

        init {
            require(Files.isRegularFile(project.resolve("tests/support/companion-chain-live-probe.ts"))) { "SIII chain probe is missing" }
            val builder =
                ProcessBuilder(
                    System.getenv("COMPANION_CHAIN_PROBE_NODE")?.takeIf(String::isNotBlank) ?: "node",
                    "--import",
                    "tsx",
                    "tests/support/companion-chain-live-probe.ts",
                ).directory(project.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD)
            val pathEnvironment =
                builder.environment().filterKeys {
                    it.uppercase() in setOf("PATH", "SYSTEMROOT", "WINDIR", "TEMP", "TMP", "USERPROFILE", "HOME")
                }
            builder.environment().clear()
            builder.environment().putAll(pathEnvironment)
            val database = postgresUrl
            val encoded = Base64.getEncoder().encodeToString(secret)
            builder.environment().putAll(
                mapOf(
                    "SIII_DATABASE_ENV" to "test",
                    "DATABASE_URL" to database,
                    "INTEGRATION_DATABASE_URL" to database,
                    "NODE_ENV" to "test",
                    "NEXT_TELEMETRY_DISABLED" to "1",
                    "COMPANION_CHAIN_SOURCE_INSTITUTION_ID" to tenantId,
                    "COMPANION_CHAIN_ORIGIN" to "http://127.0.0.1:$receiverPort",
                    "COMPANION_CHAIN_KEY_ID" to keyId,
                    "COMPANION_CHAIN_SECRET_BASE64" to encoded,
                ),
            )
            process = builder.start()
            input = BufferedWriter(OutputStreamWriter(process.outputStream, Charsets.UTF_8))
            output = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
        }

        fun start(): JsonObject = readReport()

        fun action(name: String): JsonObject {
            input.write(
                Json.encodeToString(
                    JsonObject.serializer(),
                    kotlinx.serialization.json.buildJsonObject {
                        put("action", kotlinx.serialization.json.JsonPrimitive(name))
                    },
                ),
            )
            input.newLine()
            input.flush()
            return readReport()
        }

        private fun readReport(): JsonObject {
            val line = output.readLine() ?: throw AssertionError("SIII chain probe exited before reporting")
            require(line.length <= 65_536) { "SIII chain probe report exceeded its bound" }
            val report = Json.parseToJsonElement(line).jsonObject
            assertTrue(
                report
                    .getValue("ok")
                    .jsonPrimitive.content
                    .toBoolean(),
                "SIII chain probe reported a synthetic failure",
            )
            return report
        }

        fun close() {
            if (process.isAlive) {
                runCatching { action("stop") }
                process.outputStream.close()
                if (!process.waitFor(15, TimeUnit.SECONDS)) process.destroyForcibly().waitFor(5, TimeUnit.SECONDS)
            }
            input.close()
            output.close()
        }
    }

    private fun JsonObject.string(name: String) = getValue(name).jsonPrimitive.content

    private fun JsonObject.long(name: String) = getValue(name).jsonPrimitive.content.toLong()

    private fun JsonObject.receiptState(sectionId: String): String {
        val events = getValue("events").jsonArray.map { it.jsonObject }
        val sectionEvent = events.lastOrNull { it.getValue("state").jsonPrimitive.content == "accepted" }
        assertNotNull(sectionEvent, "outbox event for synthetic section $sectionId was not durably accepted")
        return sectionEvent.getValue("state").jsonPrimitive.content
    }

    private fun JsonObject.hasAcceptedVersion(version: Long) =
        getValue("events")
            .jsonArray
            .map { it.jsonObject }
            .any {
                it
                    .getValue("sourceVersion")
                    .jsonPrimitive.content
                    .toLong() == version &&
                    it.getValue("state").jsonPrimitive.content == "accepted"
            }

    private fun JsonObject.latestAcceptedVersionAfter(previousVersion: Long): Long {
        val version =
            getValue("events")
                .jsonArray
                .map { it.jsonObject }
                .filter { it.getValue("state").jsonPrimitive.content == "accepted" }
                .maxOfOrNull {
                    it
                        .getValue("sourceVersion")
                        .jsonPrimitive
                        .content
                        .toLong()
                }
        assertNotNull(version, "outbox did not durably accept an event after source version $previousVersion")
        assertTrue(version > previousVersion, "scheduled outbox version $version did not advance past $previousVersion")
        return version
    }

    private fun randomKey() = ByteArray(32) { (it + 31).toByte() }
}
