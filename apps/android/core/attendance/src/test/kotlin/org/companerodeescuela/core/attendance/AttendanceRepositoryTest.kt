package org.companerodeescuela.core.attendance

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.database.AttendanceLocalStore
import org.companerodeescuela.core.database.LocalAttendanceRecord
import org.companerodeescuela.core.database.LocalAttendanceSyncState
import org.companerodeescuela.core.database.PendingAttendanceOperation
import org.companerodeescuela.core.network.SessionRefreshCoordinator
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.AttendanceReasonCode
import org.companerodeescuela.shared.contracts.AttendanceRecordResponse
import org.companerodeescuela.shared.contracts.SchoolNetworkEvidence

class AttendanceRepositoryTest {
    private val clock = Clock.fixed(
        Instant.parse("2026-10-05T08:02:00Z"),
        ZoneOffset.UTC,
    )

    @Test
    fun `enqueue persists before scheduling network delivery`() = runTest {
        val store = FakeStore()
        val scheduler = FakeScheduler()
        val tokenStore = FakeTokenStore(token("student-1", 2_000_000_000L))
        val repository = AttendanceRepository(
            localStore = store,
            scheduler = scheduler,
            remoteClient = unusedRemoteClient(tokenStore),
            clock = clock,
            networkEvidenceProvider = SchoolNetworkEvidenceProvider {
                SchoolNetworkEvidence(ssid = "UD4-Alumno", bssid = "aa:bb:cc:dd:ee:ff")
            },
            newOperationId = { "op-1" },
        )

        val result = repository.enqueueAttempt("session-1", qrToken = "signed-qr")

        val success = assertIs<Outcome.Success<LocalAttendanceRecord>>(result)
        assertEquals("op-1", success.value.operationId)
        assertEquals("student-1", success.value.ownerId)
        assertEquals("session-1", success.value.sessionId)
        assertEquals("op-1", store.lastOperationId)
        assertEquals("signed-qr", store.lastQrToken)
        assertEquals("UD4-Alumno", store.lastSchoolNetwork?.ssid)
        assertEquals("aa:bb:cc:dd:ee:ff", store.lastSchoolNetwork?.bssid)
        assertEquals(1, scheduler.calls)
    }

    @Test
    fun `QR capture remains successful when immediate inspection network fails`() = runTest {
        val store = FakeStore()
        val scheduler = FakeScheduler()
        val tokenStore = FakeTokenStore(token("student-1", 2_000_000_000L))
        val repository = AttendanceRepository(
            localStore = store,
            scheduler = scheduler,
            remoteClient = unusedRemoteClient(tokenStore),
            clock = clock,
            newOperationId = { "op-offline" },
        )

        val result = repository.captureQrEvidence(
            sessionId = "session-offline",
            qrToken = "signed-offline-qr",
        )

        val success = assertIs<Outcome.Success<QrEvidenceCapture>>(result)
        assertEquals("op-offline", success.value.localRecord.operationId)
        assertEquals("session-offline", success.value.localRecord.sessionId)
        assertEquals("signed-offline-qr", store.lastQrToken)
        assertNull(success.value.inspection)
        assertEquals(1, scheduler.calls)
    }

    @Test
    fun `signed QR pack is readable without a network call and stays scoped to teacher`() = runTest {
        val now = clock.instant().epochSecond
        val signed = org.companerodeescuela.shared.contracts.AttendanceQrResponse(
            token = "server-signed-test-token",
            issuedAtEpochSeconds = now,
            expiresAtEpochSeconds = now + 25,
            rotateAfterSeconds = 15,
        )
        val offlineCache = object : AttendanceQrPackStore {
            override fun read(ownerId: String, sessionId: String) =
                if (ownerId == "teacher-1" && sessionId == "session-1") listOf(signed)
                else emptyList()
            override fun save(ownerId: String, sessionId: String, slots: List<org.companerodeescuela.shared.contracts.AttendanceQrResponse>) = Unit
            override fun rememberSession(ownerId: String, session: org.companerodeescuela.shared.contracts.AttendanceSessionResponse) = Unit
            override fun restoreSession(ownerId: String): org.companerodeescuela.shared.contracts.AttendanceSessionResponse? = null
            override fun clearSession(ownerId: String) = Unit
        }
        val tokenStore = FakeTokenStore(token("teacher-1", 2_000_000_000L))
        val repository = AttendanceRepository(
            localStore = FakeStore(),
            scheduler = FakeScheduler(),
            remoteClient = unusedRemoteClient(tokenStore),
            offlineQrStore = offlineCache,
            clock = clock,
        )

        val cached = repository.issueQr("session-1", "teacher-1")
        assertEquals(signed, assertIs<Outcome.Success<org.companerodeescuela.shared.contracts.AttendanceQrResponse>>(cached).value)
        assertIs<Outcome.Failure>(repository.issueQr("session-1", "another-teacher"))
    }

    @Test
    fun `expired session does not create an outbox row`() = runTest {
        val store = FakeStore()
        val scheduler = FakeScheduler()
        val tokenStore = FakeTokenStore(token("student-1", 1L))
        val repository = AttendanceRepository(
            localStore = store,
            scheduler = scheduler,
            remoteClient = unusedRemoteClient(tokenStore),
            clock = clock,
        )

        val result = repository.enqueueAttempt("session-1")

        val failure = assertIs<Outcome.Failure>(result)
        assertEquals(401, assertIs<AppError.Http>(failure.error).status)
        assertNull(store.lastOperationId)
        assertEquals(0, scheduler.calls)
    }

    @Test
    fun `retry policy grows exponentially and caps at fifteen minutes`() {
        assertEquals(15, AttendanceRetryPolicy.nextDelaySeconds(0))
        assertEquals(30, AttendanceRetryPolicy.nextDelaySeconds(1))
        assertEquals(60, AttendanceRetryPolicy.nextDelaySeconds(2))
        assertEquals(900, AttendanceRetryPolicy.nextDelaySeconds(10))
        assertEquals(900, AttendanceRetryPolicy.nextDelaySeconds(100))
    }

    @Test
    fun `retry policy distinguishes transient and terminal http statuses`() {
        assertEquals(true, AttendanceRetryPolicy.isRetryableHttp(408))
        assertEquals(true, AttendanceRetryPolicy.isRetryableHttp(429))
        assertEquals(true, AttendanceRetryPolicy.isRetryableHttp(503))
        assertEquals(false, AttendanceRetryPolicy.isRetryableHttp(401))
        assertEquals(false, AttendanceRetryPolicy.isRetryableHttp(409))
    }

    private fun unusedRemoteClient(tokenStore: FakeTokenStore): AttendanceRemoteClient {
        val client = HttpClient(
            MockEngine {
                respondError(HttpStatusCode.InternalServerError)
            },
        )
        return AttendanceRemoteClient(
            client = client,
            refreshCoordinator = SessionRefreshCoordinator(client, tokenStore),
        )
    }

    private fun token(subject: String, expiresAt: Long): String {
        val payload = """{"sub":"$subject","exp":$expiresAt}"""
        val encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.toByteArray())
        return "e30.$encoded.signature"
    }

    private class FakeScheduler : AttendanceSyncEnqueuer {
        var calls = 0
        override fun schedule() {
            calls += 1
        }
    }

    private class FakeTokenStore(
        private var token: String?,
    ) : SessionTokenStore {
        override suspend fun readAccessToken(): String? = token
        override suspend fun writeAccessToken(token: String) {
            this.token = token
        }
        override suspend fun clear() {
            token = null
        }
    }

    private class FakeStore : AttendanceLocalStore {
        var lastOperationId: String? = null
        var lastQrToken: String? = null
        var lastSchoolNetwork: SchoolNetworkEvidence? = null

        override suspend fun enqueue(
            operationId: String,
            ownerId: String,
            sessionId: String,
            deviceTimestampEpochSeconds: Long,
            qrToken: String?,
            schoolNetwork: SchoolNetworkEvidence?,
        ): LocalAttendanceRecord {
            lastOperationId = operationId
            lastQrToken = qrToken
            lastSchoolNetwork = schoolNetwork
            return LocalAttendanceRecord(
                operationId = operationId,
                ownerId = ownerId,
                sessionId = sessionId,
                syncState = LocalAttendanceSyncState.PENDING,
                attendanceStatus = null,
                reasonCode = null,
                updatedAtEpochSeconds = deviceTimestampEpochSeconds,
            )
        }

        override suspend fun nextReady(nowEpochSeconds: Long): PendingAttendanceOperation? = null

        override suspend fun recordRetry(
            operationId: String,
            nextAttemptAtEpochSeconds: Long,
            errorCode: String,
        ) = Unit

        override suspend fun markAuthRequired(operationId: String) = Unit

        override suspend fun markRejected(
            operationId: String,
            reasonCode: AttendanceReasonCode,
        ) = Unit

        override suspend fun markSynced(
            operationId: String,
            response: AttendanceRecordResponse,
        ) = Unit

        override suspend fun findLocal(operationId: String): LocalAttendanceRecord? = null

        override fun observe(ownerId: String): Flow<List<LocalAttendanceRecord>> = flowOf(emptyList())

        override suspend fun clearOwner(ownerId: String) = Unit
    }
}
