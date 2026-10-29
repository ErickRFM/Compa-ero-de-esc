package org.companerodeescuela.api.academic

import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import org.companerodeescuela.api.academic.groups.InMemoryAcademicGroupRepository
import org.companerodeescuela.api.attendance.AttendanceEnrollmentResolver
import org.companerodeescuela.api.attendance.AttendanceStudentService
import org.companerodeescuela.api.attendance.InMemoryAttendanceRepository
import org.companerodeescuela.api.attendance.SiiiAttendanceAuthority
import org.companerodeescuela.api.auth.PlatformSessionAuthority
import org.companerodeescuela.api.classroom.ClassroomService
import org.companerodeescuela.api.classroom.InMemoryClassroomRepository
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.mock.MockAcademicProvider
import org.companerodeescuela.api.integrations.mock.MockIdentityProvider
import org.companerodeescuela.api.integrations.push.INTEGRATION_KEY_ID_HEADER
import org.companerodeescuela.api.integrations.push.INTEGRATION_NONCE_HEADER
import org.companerodeescuela.api.integrations.push.INTEGRATION_SIGNATURE_HEADER
import org.companerodeescuela.api.integrations.push.INTEGRATION_TIMESTAMP_HEADER
import org.companerodeescuela.api.integrations.push.InstitutionalAttendanceHistoryService
import org.companerodeescuela.api.integrations.push.InstitutionalEventAcceptance
import org.companerodeescuela.api.integrations.push.InstitutionalEventRecord
import org.companerodeescuela.api.integrations.push.InstitutionalEventRepository
import org.companerodeescuela.api.integrations.push.InstitutionalIdentityLinkRepository
import org.companerodeescuela.api.integrations.push.InstitutionalIdentityLinkService
import org.companerodeescuela.api.integrations.push.InstitutionalPushAuthenticator
import org.companerodeescuela.api.integrations.push.InstitutionalPushRateLimiter
import org.companerodeescuela.api.integrations.push.InstitutionalPushService
import org.companerodeescuela.api.integrations.push.IntegrationCredential
import org.companerodeescuela.api.integrations.push.IntegrationCredentialRepository
import org.companerodeescuela.api.integrations.push.IntegrationNonceRepository
import org.companerodeescuela.api.integrations.push.institutionalAttendanceHistoryRoutes
import org.companerodeescuela.api.integrations.push.institutionalPushRoutes
import org.companerodeescuela.api.plugins.ApiJson
import org.companerodeescuela.api.plugins.configurePlugins
import org.companerodeescuela.api.security.InMemoryMfaRepository
import org.companerodeescuela.api.security.MfaService
import org.companerodeescuela.shared.contracts.AttendanceIdentitySource
import org.companerodeescuela.shared.contracts.AttendanceReasonCode
import org.companerodeescuela.shared.contracts.AttendanceRecordResponse
import org.companerodeescuela.shared.contracts.AttendanceReviewEntry
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionStatus
import org.companerodeescuela.shared.contracts.AttendanceStatus
import org.companerodeescuela.shared.contracts.InstitutionalAttendanceHistoryContract
import org.companerodeescuela.shared.contracts.InstitutionalAttendanceHistoryRequest
import org.companerodeescuela.shared.contracts.InstitutionalAttendanceHistoryResponse
import org.companerodeescuela.shared.contracts.InstitutionalIdentityKind
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InstitutionalAttendanceHistoryRoutesTest {
    private class Fixture {
        val source = SiiiAcademicRoutesTest.Fixture()
        val repository = InMemoryAttendanceRepository()
        val authority = SiiiAttendanceAuthority(source.service, source.reader, source.links, source.identities, source.accounts)
        val groups = InMemoryAcademicGroupRepository()
        val classrooms = ClassroomService(InMemoryClassroomRepository(), groups, accounts = source.accounts)
        val projection =
            NativeAcademicProjection(
                source.accounts,
                classrooms,
                groups,
                InMemoryAcademicScheduleOverrideRepository(),
                institutionalAttendance = authority,
            )
        val students =
            AttendanceStudentService(
                repository,
                AttendanceEnrollmentResolver(MockAcademicProvider(), classrooms, authority),
                nativeProjection = projection,
            )
        var lookupCount = 0
        var hideLinkAfter: Int? = null
        private val links =
            object : InstitutionalIdentityLinkRepository by source.links {
                override suspend fun findByExternalId(
                    institutionId: String,
                    entityType: InstitutionalIdentityKind,
                    externalId: String,
                ) = if (++lookupCount >=
                    (hideLinkAfter ?: Int.MAX_VALUE)
                ) {
                    null
                } else {
                    source.links.findByExternalId(institutionId, entityType, externalId)
                }
            }
        val identities =
            InstitutionalIdentityLinkService(
                source.accounts,
                source.directory,
                links,
                MfaService(source.accounts, InMemoryMfaRepository(), null),
            )
        val service = InstitutionalAttendanceHistoryService(identities, students, authority, Clock.fixed(source.now, ZoneOffset.UTC))
        var acceptedWrites = 0
        val writes =
            InstitutionalPushService(
                object : InstitutionalEventRepository {
                    override suspend fun accept(record: InstitutionalEventRecord): InstitutionalEventAcceptance {
                        acceptedWrites++
                        return InstitutionalEventAcceptance.ACCEPTED
                    }
                },
                Clock.fixed(source.now, ZoneOffset.UTC),
            )
        private val secret = "synthetic-history-secret-at-least-thirty-two-bytes".toByteArray()
        private val credentials =
            mapOf(
                "read-key" to IntegrationCredential("read-key", "tenant-a", secret, setOf("attendance:read"), true, null),
                "write-key" to IntegrationCredential("write-key", "tenant-a", secret, setOf("group:write"), true, null),
                "other-key" to IntegrationCredential("other-key", "tenant-b", secret, setOf("attendance:read"), true, null),
                "revoked-key" to IntegrationCredential("revoked-key", "tenant-a", secret, setOf("attendance:read"), false, null),
                "expired-key" to
                    IntegrationCredential("expired-key", "tenant-a", secret, setOf("attendance:read"), true, source.now.epochSecond - 1),
            )
        private val credentialStore =
            object : IntegrationCredentialRepository {
                override suspend fun findCredential(keyId: String): IntegrationCredential? = credentials[keyId]
            }
        private val nonceStore =
            object : IntegrationNonceRepository {
                val nonces = mutableSetOf<Pair<String, String>>()

                override suspend fun reserveNonce(
                    credentialId: String,
                    nonce: String,
                    expiresAt: Instant,
                ): Boolean =
                    nonces.add(
                        credentialId to nonce,
                    )
            }
        val authenticator = InstitutionalPushAuthenticator(credentialStore, nonceStore, Clock.fixed(source.now, ZoneOffset.UTC))
        var retryAfter: Long? = null
        val limiter =
            object : InstitutionalPushRateLimiter {
                override suspend fun acquire(clientAddress: String): Long? = retryAfter
            }
        var nonceNumber = 0

        suspend fun seed(count: Int = 1) {
            source.initialize()
            val occurrences =
                (0 until count).map {
                    source.snapshot.occurrences
                        .single()
                        .copy(sourceId = UUID.randomUUID().toString())
                }
            source.stored = source.stored.copy(snapshot = source.snapshot.copy(occurrences = occurrences))
            for ((index, occurrence) in occurrences.withIndex()) {
                val id = "history-session-$index"
                repository.createSession(
                    AttendanceSessionResponse(
                        id,
                        occurrence.sourceId,
                        source.sectionId,
                        source.snapshot.sectionCode,
                        occurrence.date,
                        occurrence.startsAt,
                        occurrence.endsAt,
                        "teacher",
                        1,
                        2,
                        closedAtEpochSeconds = 2,
                        status = AttendanceSessionStatus.CLOSED,
                        identitySource = AttendanceIdentitySource.NATIVE,
                        institutionId = "tenant-a",
                        groupId = source.sectionId,
                        groupScopeRevision = 1,
                        academicSource = SiiiAttendanceAuthority.SOURCE,
                    ),
                )
                repository.writeAttempt(
                    AttendanceRecordResponse(
                        "student:history-audit-$index",
                        "private-operation-$index",
                        id,
                        occurrence.sourceId,
                        "student",
                        AttendanceStatus.REVIEW_REQUIRED,
                        AttendanceReasonCode.CLASS_CALL_CONFIRMED,
                        index.toLong() + 1,
                        index.toLong() + 1,
                        reviewedBy = "private-native-reviewer",
                        reviewHistory =
                            listOf(
                                AttendanceReviewEntry(
                                    "private-native-reviewer",
                                    1,
                                    "do-not-export-private-note",
                                    AttendanceStatus.REVIEW_REQUIRED,
                                ),
                            ),
                    ),
                )
            }
        }

        fun body(external: String = "STUDENT") =
            ApiJson.encodeToString(
                InstitutionalAttendanceHistoryRequest(InstitutionalAttendanceHistoryContract.REQUEST, external),
            )

        suspend fun ApplicationTestBuilder.read(
            body: String = body(),
            keyId: String = "read-key",
            nonce: String = "history-nonce-${(++nonceNumber).toString().padStart(4, '0')}",
            signedBody: String = body,
            signedPath: String = InstitutionalPushAuthenticator.ATTENDANCE_HISTORY_PATH,
            timestamp: Long = source.now.epochSecond,
            path: String = InstitutionalPushAuthenticator.ATTENDANCE_HISTORY_PATH,
        ) = client.post(path) {
            contentType(ContentType.Application.Json)
            header(INTEGRATION_KEY_ID_HEADER, keyId)
            header(INTEGRATION_TIMESTAMP_HEADER, timestamp.toString())
            header(INTEGRATION_NONCE_HEADER, nonce)
            header(
                INTEGRATION_SIGNATURE_HEADER,
                InstitutionalPushAuthenticator.sign(
                    secret,
                    InstitutionalPushAuthenticator.canonicalMessage(
                        "POST",
                        signedPath,
                        timestamp.toString(),
                        nonce,
                        signedBody.toByteArray(),
                    ),
                ),
            )
            setBody(body)
        }
    }

    private fun scenario(
        count: Int = 1,
        block: suspend ApplicationTestBuilder.(Fixture) -> Unit,
    ) = testApplication {
        val f = Fixture()
        f.seed(count)
        application {
            configurePlugins(f.source.settings, f.source.sessions, PlatformSessionAuthority(f.source.accounts, MockIdentityProvider()))
            routing {
                institutionalAttendanceHistoryRoutes(f.authenticator, f.service, f.limiter)
                institutionalPushRoutes(f.authenticator, f.writes, f.limiter)
            }
        }
        block(f)
    }

    @Test
    fun `signed institution reads closed attendance without native JWT or private identity and notes`() =
        scenario { f ->
            val response = with(f) { read() }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
            val text = response.bodyAsText()
            val history = ApiJson.decodeFromString<InstitutionalAttendanceHistoryResponse>(text)
            assertEquals(InstitutionalAttendanceHistoryContract.RESPONSE, history.contract)
            assertEquals(100, history.sampleLimit)
            assertTrue(
                history.records
                    .single()
                    .auditRecordId
                    .matches(Regex("[0-9a-f]{64}")),
            )
            assertEquals(AttendanceStatus.REVIEW_REQUIRED, history.records.single().status)
            for (sensitive in listOf(
                "studentId",
                "student:history-audit",
                "reviewedBy",
                "reviewHistory",
                "private-native-reviewer",
                "do-not-export-private-note",
                "network",
                "device",
                "qrToken",
                "private-operation",
            )) {
                assertFalse(text.contains(sensitive), "Private attendance evidence must not be exported")
            }
        }

    @Test
    fun `read scope is mandatory and signatures timestamps revocation and replay remain binding`() =
        scenario { f ->
            assertEquals(HttpStatusCode.Forbidden, with(f) { read(keyId = "write-key") }.status)
            assertEquals(0, f.source.authorityReads)
            for (key in listOf("missing-key", "expired-key", "revoked-key")) {
                assertEquals(HttpStatusCode.Unauthorized, with(f) { read(keyId = key) }.status)
            }
            assertEquals(HttpStatusCode.Unauthorized, with(f) { read(timestamp = source.now.epochSecond - 301) }.status)
            assertEquals(HttpStatusCode.Unauthorized, with(f) { read(signedBody = "{}") }.status)
            assertEquals(
                HttpStatusCode.Unauthorized,
                with(f) { read(signedPath = InstitutionalPushAuthenticator.PROJECTION_STATUS_PATH) }.status,
            )
            assertEquals(HttpStatusCode.OK, with(f) { read(nonce = "history-replay-nonce") }.status)
            assertEquals(HttpStatusCode.Unauthorized, with(f) { read(nonce = "history-replay-nonce") }.status)
        }

    @Test
    fun `history read credential cannot submit academic events`() =
        scenario { f ->
            val event =
                """{"schemaVersion":1,"eventId":"history-write-probe","entityType":"group",""" +
                    """"externalId":"group-probe","sourceVersion":1,"occurredAtEpochSeconds":${f.source.now.epochSecond},""" +
                    """"operation":"upsert","payload":{"status":"open"}}"""
            val response =
                with(f) {
                    read(
                        body = event,
                        path = InstitutionalPushAuthenticator.EVENTS_PATH,
                        signedPath = InstitutionalPushAuthenticator.EVENTS_PATH,
                    )
                }
            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertEquals(0, f.acceptedWrites)
        }

    @Test
    fun `strict request rejects tenant overrides malformed references and bounded or rate limited bodies`() =
        scenario { f ->
            assertEquals(HttpStatusCode.BadRequest, with(f) { read(body = body().dropLast(1) + ",\"tenantId\":\"tenant-b\"}") }.status)
            assertEquals(HttpStatusCode.BadRequest, with(f) { read(body = body(" STUDENT")) }.status)
            assertEquals(HttpStatusCode.BadRequest, with(f) { read(body = "{}") }.status)
            assertEquals(HttpStatusCode.PayloadTooLarge, with(f) { read(body = "a".repeat(4097)) }.status)
            f.retryAfter = 30
            val limited = with(f) { read(body = "not-json") }
            assertEquals(HttpStatusCode.TooManyRequests, limited.status)
            assertEquals("30", limited.headers[HttpHeaders.RetryAfter])
        }

    @Test
    fun `external identity resolves only inside signed tenant and revoked links are hidden`() =
        scenario { f ->
            assertEquals(HttpStatusCode.Forbidden, with(f) { read(keyId = "other-key") }.status)
            assertEquals(HttpStatusCode.NotFound, with(f) { read(body = body("UNKNOWN")) }.status)
            f.lookupCount = 0
            f.hideLinkAfter = 2
            assertEquals(HttpStatusCode.Forbidden, with(f) { read() }.status, "Link revoked during source reads must suppress the response")
            f.lookupCount = 0
            f.hideLinkAfter = 1
            assertEquals(HttpStatusCode.NotFound, with(f) { read() }.status)
        }

    @Test
    fun `source withdrawal returns no stale rows and authority outages remain errors`() =
        scenario { f ->
            f.source.sections = emptyList()
            val withdrawn = with(f) { read() }
            assertEquals(HttpStatusCode.OK, withdrawn.status)
            assertTrue(ApiJson.decodeFromString<InstitutionalAttendanceHistoryResponse>(withdrawn.bodyAsText()).records.isEmpty())
            f.source.onAuthorityRead = { throw ApiException.DependencyUnavailable("Synthetic source outage") }
            assertEquals(HttpStatusCode.ServiceUnavailable, with(f) { read() }.status)
        }

    @Test
    fun `export is capped at one hundred records without adding per-row source reads`() =
        scenario(101) { f ->
            f.source.authorityReads = 0
            val response = with(f) { read() }
            assertEquals(HttpStatusCode.OK, response.status)
            val history = ApiJson.decodeFromString<InstitutionalAttendanceHistoryResponse>(response.bodyAsText())
            assertEquals(100, history.records.size)
            assertEquals(101L, history.records.first().receivedAtEpochSeconds)
            assertTrue(history.records.all { it.auditRecordId.matches(Regex("[0-9a-f]{64}")) })
            assertTrue(f.source.authorityReads <= 15)
        }
}
