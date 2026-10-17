package org.companerodeescuela.api.integrations.academic

import com.mongodb.client.model.Filters.eq
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.bson.Document
import org.companerodeescuela.api.auth.AccountIdentityChange
import org.companerodeescuela.api.auth.InMemoryPlatformAccountRepository
import org.companerodeescuela.api.auth.PlatformAccount
import org.companerodeescuela.api.auth.SessionIdentitySource
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.institutions.MongoInstitutionRepository
import org.companerodeescuela.api.integrations.academic.siii.MongoSiiiSectionSnapshotRepository
import org.companerodeescuela.api.integrations.academic.siii.SiiiSectionEventAdapter
import org.companerodeescuela.api.integrations.academic.siii.SiiiSectionProjectionHandler
import org.companerodeescuela.api.integrations.academic.siii.SiiiSectionReader
import org.companerodeescuela.api.integrations.push.InstitutionalEventProjectionWorker
import org.companerodeescuela.api.integrations.push.InstitutionalIdentityLink
import org.companerodeescuela.api.integrations.push.InstitutionalIdentityLinkService
import org.companerodeescuela.api.integrations.push.InstitutionalProjectionCommand
import org.companerodeescuela.api.integrations.push.InstitutionalProjectionCommandHandler
import org.companerodeescuela.api.integrations.push.InstitutionalProjectionIdentityResolver
import org.companerodeescuela.api.integrations.push.InstitutionalPushAuthenticator
import org.companerodeescuela.api.integrations.push.InstitutionalPushResult
import org.companerodeescuela.api.integrations.push.InstitutionalPushService
import org.companerodeescuela.api.integrations.push.InstitutionalSecretProtector
import org.companerodeescuela.api.integrations.push.IntegrationCredential
import org.companerodeescuela.api.integrations.push.MongoInstitutionalIdentityLinkRepository
import org.companerodeescuela.api.integrations.push.MongoInstitutionalPushRepository
import org.companerodeescuela.api.integrations.push.SignedIntegrationRequest
import org.companerodeescuela.api.security.InMemoryMfaRepository
import org.companerodeescuela.api.security.MfaSecretCipher
import org.companerodeescuela.api.security.MfaService
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.InstitutionalEventOperation
import org.companerodeescuela.shared.contracts.InstitutionalIdentityKind
import org.companerodeescuela.shared.contracts.InstitutionalPushEvent
import org.companerodeescuela.shared.contracts.UserRole
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(named = "V12_TEST_MONGODB_URI", matches = ".+")
@Timeout(120)
class MongoSiiiSectionProjectionTest {
    @Test
    fun `inactive source students lose enrolled access while their native role and link remain current`() =
        database { db ->
            val f = Fixture(db).also { it.initialize() }
            f.send()
            f.worker().processNext("tenant-a", "group:v1")
            assertEquals(
                "active",
                f.reader
                    .student(
                        f.student.toUserSummary(),
                        SessionIdentitySource.NATIVE,
                        "tenant-a",
                        SECTION_ID,
                        "STU-01",
                    ).enrollment.studentStatus,
            )
            for ((index, state) in listOf("inactive", "withdrawn", "graduated").withIndex()) {
                val payload = sectionPayload()
                val rows =
                    JsonArray(
                        (payload["enrollments"] as JsonArray).mapIndexed { rowIndex, row ->
                            if (rowIndex == 0) (row as JsonObject).withField("studentStatus", state) else row
                        },
                    )
                f.send(version = index + 2L, payload = JsonObject(payload + ("enrollments" to rows)))
                f.worker().processNext("tenant-a", "group:v1")
                assertEquals(setOf(UserRole.STUDENT), f.accounts.findById(f.student.id)?.roles)
                assertEquals(f.student.id, f.identities.resolve("tenant-a", InstitutionalIdentityKind.STUDENT, "STU-01")?.id)
                assertFailsWith<ApiException.Forbidden> {
                    f.reader.student(f.student.toUserSummary(), SessionIdentitySource.NATIVE, "tenant-a", SECTION_ID, "STU-01")
                }
                val history =
                    f.reader
                        .teacher(
                            f.teacher.toUserSummary(),
                            SessionIdentitySource.NATIVE,
                            "tenant-a",
                            SECTION_ID,
                            "EMP-01",
                        ).enrollments
                        .first()
                assertEquals("enrolled", history.status)
                assertEquals(state, history.studentStatus)
            }
        }

    @Test
    fun `institutional and legacy sources never gain access through a colliding native account ID`() =
        database { db ->
            val f = Fixture(db).also { it.initialize() }
            f.send()
            f.worker().processNext("tenant-a", "group:v1")
            for (source in listOf(SessionIdentitySource.INSTITUTIONAL, SessionIdentitySource.LEGACY)) {
                assertFailsWith<ApiException.Forbidden> {
                    f.reader.student(
                        f.student.toUserSummary(),
                        source,
                        "tenant-a",
                        SECTION_ID,
                        "STU-01",
                    )
                }
                assertFailsWith<ApiException.Forbidden> {
                    f.reader.teacher(
                        f.teacher.toUserSummary(),
                        source,
                        "tenant-a",
                        SECTION_ID,
                        "EMP-01",
                    )
                }
            }
        }

    @Test
    fun `open section without teacher preserves own student access and denies unassigned teacher`() =
        database { db ->
            val f = Fixture(db).also { it.initialize() }
            f.send(payload = JsonObject(sectionPayload() + ("teachers" to JsonArray(emptyList()))))
            f.worker().processNext("tenant-a", "group:v1")
            assertEquals("PROJECTED", f.receiptStatus("event-1"))
            assertEquals(
                emptyList(),
                f.reader
                    .student(f.student.toUserSummary(), SessionIdentitySource.NATIVE, "tenant-a", SECTION_ID, "STU-01")
                    .section.teachers,
            )
            assertFailsWith<ApiException.Forbidden> {
                f.reader.teacher(f.teacher.toUserSummary(), SessionIdentitySource.NATIVE, "tenant-a", SECTION_ID, "EMP-01")
            }
        }

    @Test
    fun `signed inbox worker and Mongo reader preserve own enrollment and isolate colliding tenants`() =
        database { db ->
            val f = Fixture(db).also { it.initialize() }
            val first = f.send()
            assertFalse(first.duplicate)
            assertTrue(f.send().duplicate)
            assertTrue(f.worker().processNext("tenant-a", "group:v1"))
            assertEquals("PROJECTED", f.receiptStatus("event-1"))
            assertEquals(1L, f.snapshots.find("tenant-a", SECTION_ID)?.sourceVersion)
            val studentView = f.reader.student(f.student.toUserSummary(), SessionIdentitySource.NATIVE, "tenant-a", SECTION_ID, "STU-01")
            assertEquals("STU-01", studentView.enrollment.studentExternalId)
            val wire = Json.encodeToString(studentView)
            listOf("STU-02", "Private Peer", "enrollments", "digest", "eventId", "credential").forEach { assertFalse(wire.contains(it)) }
            assertEquals(
                2,
                f.reader
                    .teacher(f.teacher.toUserSummary(), SessionIdentitySource.NATIVE, "tenant-a", SECTION_ID, "EMP-01")
                    .enrollments.size,
            )
            assertFailsWith<ApiException.Forbidden> {
                f.reader.student(f.student.toUserSummary(), SessionIdentitySource.NATIVE, "tenant-a", SECTION_ID, "STU-02")
            }
            assertFailsWith<ApiException.Forbidden> {
                f.reader.student(f.unlinked.toUserSummary(), SessionIdentitySource.NATIVE, "tenant-a", SECTION_ID, "STU-02")
            }
            f.send(tenant = "tenant-b", payload = sectionPayload().withField("sectionCode", "TENANT-B"))
            assertTrue(f.worker().processNext("tenant-b", "group:v1"))
            assertEquals(
                "TENANT-B",
                f.snapshots
                    .find("tenant-b", SECTION_ID)
                    ?.snapshot
                    ?.sectionCode,
            )
            assertEquals(
                "SEC-01",
                f.snapshots
                    .find("tenant-a", SECTION_ID)
                    ?.snapshot
                    ?.sectionCode,
            )
            assertFailsWith<ApiException.Forbidden> {
                f.reader.student(f.student.toUserSummary(), SessionIdentitySource.NATIVE, "tenant-b", SECTION_ID, "STU-01")
            }
            assertEquals(2L, db.getCollection<Document>("siii_institutional_section_snapshots").countDocuments())
            assertEquals(0L, db.getCollection<Document>("native_classrooms").countDocuments())
            assertEquals(0L, db.getCollection<Document>("platform_accounts").countDocuments())
            f.send(version = 3, payload = sectionPayload().withField("sectionCode", "NEWER"))
            f.worker().processNext("tenant-a", "group:v1")
            f.send(version = 2, payload = sectionPayload().withField("sectionCode", "STALE"))
            f.worker().processNext("tenant-a", "group:v1")
            assertEquals("SUPERSEDED", f.receiptStatus("event-2"))
            assertEquals(
                "NEWER",
                f.snapshots
                    .find("tenant-a", SECTION_ID)
                    ?.snapshot
                    ?.sectionCode,
            )
        }

    @Test
    fun `signed source changes revoke membership and preserve cancellation`() =
        database { db ->
            val f = Fixture(db).also { it.initialize() }
            f.send()
            f.worker().processNext("tenant-a", "group:v1")
            val valid = sectionPayload()
            val withdrawn = JsonArray((valid["enrollments"] as JsonArray).map { (it as JsonObject).withField("status", "withdrawn") })
            val cancelled = JsonArray((valid["occurrences"] as JsonArray).map { (it as JsonObject).withField("status", "CANCELLED") })
            f.send(version = 2, payload = JsonObject(valid + mapOf("enrollments" to withdrawn, "occurrences" to cancelled)))
            f.worker().processNext("tenant-a", "group:v1")
            assertFailsWith<ApiException.Forbidden> {
                f.reader.student(f.student.toUserSummary(), SessionIdentitySource.NATIVE, "tenant-a", SECTION_ID, "STU-01")
            }
            assertEquals(
                "CANCELLED",
                f.reader
                    .teacher(
                        f.teacher.toUserSummary(),
                        SessionIdentitySource.NATIVE,
                        "tenant-a",
                        SECTION_ID,
                        "EMP-01",
                    ).section.occurrences
                    .single()
                    .status,
            )
            f.send(version = 3, payload = JsonObject(valid.withField("status", "closed") + ("teachers" to JsonArray(emptyList()))))
            f.worker().processNext("tenant-a", "group:v1")
            assertFailsWith<ApiException.Forbidden> {
                f.reader.teacher(f.teacher.toUserSummary(), SessionIdentitySource.NATIVE, "tenant-a", SECTION_ID, "EMP-01")
            }
            f.send(version = 4, operation = InstitutionalEventOperation.DELETE, payload = JsonObject(emptyMap()))
            f.worker().processNext("tenant-a", "group:v1")
            assertNull(f.snapshots.find("tenant-a", SECTION_ID)?.snapshot)
            assertEquals(4L, f.snapshots.find("tenant-a", SECTION_ID)?.sourceVersion)
            assertFailsWith<ApiException.NotFound> {
                f.reader.teacher(f.teacher.toUserSummary(), SessionIdentitySource.NATIVE, "tenant-a", SECTION_ID, "EMP-01")
            }
        }

    @Test
    fun `live local role account institution and link revocation deny cached actors`() =
        database { db ->
            val f = Fixture(db).also { it.initialize() }
            f.send()
            f.worker().processNext("tenant-a", "group:v1")
            val original = f.student.toUserSummary()
            val changed =
                assertNotNull(
                    f.accounts.changeIdentity(
                        f.student.id,
                        0,
                        AccountIdentityChange(
                            "remove-role",
                            "fixture-admin",
                            AccountStatus.ACTIVE,
                            emptySet(),
                            "tenant-a",
                            f.clock.instant(),
                        ),
                    ),
                )
            assertFailsWith<ApiException.Unauthorized> {
                f.reader.student(
                    original,
                    SessionIdentitySource.NATIVE,
                    "tenant-a",
                    SECTION_ID,
                    "STU-01",
                )
            }
            assertFailsWith<ApiException.Forbidden> {
                f.reader.student(changed.toUserSummary(), SessionIdentitySource.NATIVE, "tenant-a", SECTION_ID, "STU-01")
            }
            val teacher = f.teacher.toUserSummary()
            db
                .getCollection<Document>(
                    "platform_institutions",
                ).updateOne(eq("_id", "tenant-a"), Document("\$set", Document("registrationEnabled", false)))
            assertFailsWith<ApiException.Forbidden> {
                f.reader.teacher(
                    teacher,
                    SessionIdentitySource.NATIVE,
                    "tenant-a",
                    SECTION_ID,
                    "EMP-01",
                )
            }
            db
                .getCollection<Document>(
                    "platform_institutions",
                ).updateOne(eq("_id", "tenant-a"), Document("\$set", Document("registrationEnabled", true)))
            db.getCollection<Document>("institutional_identity_links").deleteMany(Document())
            assertFailsWith<ApiException.Forbidden> {
                f.reader.teacher(
                    teacher,
                    SessionIdentitySource.NATIVE,
                    "tenant-a",
                    SECTION_ID,
                    "EMP-01",
                )
            }
            assertNotNull(
                f.accounts.changeIdentity(
                    f.teacher.id,
                    0,
                    AccountIdentityChange(
                        "suspend",
                        "fixture-admin",
                        AccountStatus.SUSPENDED,
                        setOf(UserRole.TEACHER),
                        "tenant-a",
                        f.clock.instant(),
                    ),
                ),
            )
            assertFailsWith<ApiException.Unauthorized> {
                f.reader.teacher(
                    teacher,
                    SessionIdentitySource.NATIVE,
                    "tenant-a",
                    SECTION_ID,
                    "EMP-01",
                )
            }
        }

    @Test
    fun `target CAS fences concurrent stale versions and treats same-version conflict permanently`() =
        database { db ->
            val f = Fixture(db).also { it.initialize() }
            coroutineScope { (1L..12).map { version -> async { f.handler.apply("group:v1", f.command(version), emptyMap()) } }.awaitAll() }
            assertEquals(12L, f.snapshots.find("tenant-a", SECTION_ID)?.sourceVersion)
            f.handler.apply("group:v1", f.command(1), emptyMap())
            assertEquals(12L, f.snapshots.find("tenant-a", SECTION_ID)?.sourceVersion)
            f.handler.apply("group:v1", f.command(12), emptyMap())
            val second = Fixture(db, "other-section").also { it.initialize() }
            // Simulate a target already committed before its inbox receipt was acknowledged.
            second.handler.apply("group:v1", second.command(13, sectionPayload().withField("sectionCode", "CONFLICT")), emptyMap())
            second.send(version = 13)
            second.worker().processNext("tenant-a", "group:v1")
            assertEquals("FAILED", second.receiptStatus("other-section-13"))
            assertEquals("SOURCE_VERSION_CONFLICT", second.receipt("other-section-13")?.getString("projectionFailureCode"))
            assertFalse(second.worker().processNext("tenant-a", "group:v1"))
        }

    @Test
    fun `crash after target write reclaims original receipt idempotently without native effects`() =
        database { db ->
            val f = Fixture(db).also { it.initialize() }
            f.send()
            val crashing =
                InstitutionalProjectionCommandHandler { adapter, command, identities ->
                    f.handler.apply(adapter, command, identities)
                    throw CancellationException("simulated process loss")
                }
            assertFailsWith<CancellationException> { f.worker(crashing).processNext("tenant-a", "group:v1") }
            val persisted = assertNotNull(f.snapshots.find("tenant-a", SECTION_ID))
            assertEquals("PROCESSING", f.receiptStatus("event-1"))
            f.clock.current = f.clock.current.plusSeconds(121)
            assertTrue(f.worker().processNext("tenant-a", "group:v1"))
            assertEquals("PROJECTED", f.receiptStatus("event-1"))
            assertEquals(persisted.syncedAt, f.snapshots.find("tenant-a", SECTION_ID)?.syncedAt)
            assertEquals(1L, db.getCollection<Document>("siii_institutional_section_snapshots").countDocuments())
        }

    @Test
    fun `wrong contract stays unsupported and invalid known payload is permanently failed`() =
        database { db ->
            val f = Fixture(db).also { it.initialize() }
            f.send(payload = sectionPayload().withField("contract", "unknown.v1"))
            f.worker().processNext("tenant-a", "group:v1")
            assertEquals("UNSUPPORTED", f.receiptStatus("event-1"))
            f.send(version = 2, payload = sectionPayload().withField("tenantId", "tenant-b"))
            f.worker().processNext("tenant-a", "group:v1")
            assertEquals("FAILED", f.receiptStatus("event-2"))
            assertNull(f.snapshots.find("tenant-a", SECTION_ID))
        }

    private class MutableClock : Clock() {
        var current = Instant.parse("2026-10-17T12:00:00Z")

        override fun instant(): Instant = current

        override fun getZone(): ZoneId = ZoneId.of("UTC")

        override fun withZone(zone: ZoneId): Clock = this
    }

    private class Fixture(
        val db: MongoDatabase,
        val prefix: String = "event",
    ) {
        val clock = MutableClock()
        val inbox = MongoInstitutionalPushRepository(db, InstitutionalSecretProtector(ByteArray(32) { it.toByte() }), clock::instant)
        val snapshots = MongoSiiiSectionSnapshotRepository(db)
        val handler = SiiiSectionProjectionHandler(snapshots, clock)
        val accounts = InMemoryPlatformAccountRepository()
        val directory = MongoInstitutionRepository(db)
        val links = MongoInstitutionalIdentityLinkRepository(db)
        val identities =
            InstitutionalIdentityLinkService(
                accounts,
                directory,
                links,
                MfaService(
                    accounts,
                    InMemoryMfaRepository(),
                    MfaSecretCipher(
                        ByteArray(32) {
                            (
                                it +
                                    1
                            ).toByte()
                        },
                    ),
                    clock,
                ),
                clock,
            )
        val reader = SiiiSectionReader(accounts, directory, identities, snapshots, clock)
        val student = account("student", UserRole.STUDENT)
        val teacher = account("teacher", UserRole.TEACHER)
        val unlinked = account("unlinked", UserRole.STUDENT)
        val secret = ByteArray(32) { (it + 2).toByte() }

        suspend fun initialize() {
            accounts.create(student)
            accounts.create(teacher)
            accounts.create(unlinked)
            for (tenant in listOf("tenant-a", "tenant-b")) {
                db
                    .getCollection<Document>(
                        "platform_institutions",
                    ).replaceOne(
                        eq("_id", tenant),
                        Document("_id", tenant).append("displayName", "Fixture Institution").append("registrationEnabled", true),
                        com.mongodb.client.model
                            .ReplaceOptions()
                            .upsert(true),
                    )
                inbox.putCredential(IntegrationCredential("key-$tenant", tenant, secret, setOf("group:write"), true, null), clock.instant())
            }
            for ((kind, external, user) in listOf(
                Triple(InstitutionalIdentityKind.STUDENT, "STU-01", student),
                Triple(InstitutionalIdentityKind.TEACHER, "EMP-01", teacher),
            )) {
                links.createIfAbsent(
                    InstitutionalIdentityLink(
                        "tenant-a",
                        kind,
                        external,
                        user.id,
                        "fixture-admin",
                        "fixture-${kind.name}",
                        "a".repeat(64),
                        clock.instant(),
                    ),
                )
            }
        }

        private fun account(
            id: String,
            role: UserRole,
        ) = PlatformAccount(
            id,
            "Fixture $id",
            "$id@example.test",
            "fixture-hash",
            setOf(role),
            institutionId = "tenant-a",
            emailVerifiedAt = clock.instant(),
        )

        fun event(
            version: Long = 1,
            payload: JsonObject = sectionPayload(),
            operation: InstitutionalEventOperation = InstitutionalEventOperation.UPSERT,
        ) = InstitutionalPushEvent(1, "$prefix-$version", "group", SECTION_ID, version, clock.instant().epochSecond, operation, payload)

        fun command(
            version: Long,
            payload: JsonObject = sectionPayload(),
        ): InstitutionalProjectionCommand =
            event(version, payload).let {
                InstitutionalProjectionCommand(
                    "tenant-a",
                    it.entityType,
                    it.externalId,
                    it.eventId,
                    it.sourceVersion,
                    it.schemaVersion,
                    it.operation,
                    it.payload,
                )
            }

        suspend fun send(
            version: Long = 1,
            payload: JsonObject = sectionPayload(),
            operation: InstitutionalEventOperation = InstitutionalEventOperation.UPSERT,
            tenant: String = "tenant-a",
        ): InstitutionalPushResult.Accepted {
            val body = Json.encodeToString(event(version, payload, operation)).toByteArray()
            val nonce = UUID.randomUUID().toString()
            val timestamp = clock.instant().epochSecond.toString()
            val canonical =
                InstitutionalPushAuthenticator.canonicalMessage(
                    "POST",
                    InstitutionalPushAuthenticator.EVENTS_PATH,
                    timestamp,
                    nonce,
                    body,
                )
            val request =
                SignedIntegrationRequest(
                    "key-$tenant",
                    timestamp,
                    nonce,
                    InstitutionalPushAuthenticator.sign(secret, canonical),
                    "POST",
                    InstitutionalPushAuthenticator.EVENTS_PATH,
                    body,
                )
            val verified = assertNotNull(InstitutionalPushAuthenticator(inbox, inbox, clock).authenticate(request))
            return assertIs<InstitutionalPushResult.Accepted>(InstitutionalPushService(inbox, clock).accept(verified, body))
        }

        fun worker(target: InstitutionalProjectionCommandHandler = handler) =
            InstitutionalEventProjectionWorker(
                inbox,
                InstitutionalProjectionIdentityResolver { tenant, ref -> identities.resolve(tenant, ref.kind, ref.externalId)?.id },
                target,
                listOf(SiiiSectionEventAdapter()),
                clock,
                120,
            )

        suspend fun receipt(id: String): Document? =
            db
                .getCollection<Document>(
                    "institutional_integration_events",
                ).find(Document("tenantId", "tenant-a").append("eventId", id))
                .firstOrNull()

        suspend fun receiptStatus(id: String): String? = receipt(id)?.getString("projectionStatus")?.uppercase()
    }

    private fun database(block: suspend (MongoDatabase) -> Unit) =
        runBlocking {
            val uri = System.getenv("V12_TEST_MONGODB_URI")
            require(uri == "mongodb://127.0.0.1:27017")
            MongoClient.create(uri).use { client ->
                val db = client.getDatabase("v12_test_${UUID.randomUUID().toString().replace("-", "")}")
                try {
                    block(db)
                } finally {
                    db.drop()
                }
            }
        }
}
