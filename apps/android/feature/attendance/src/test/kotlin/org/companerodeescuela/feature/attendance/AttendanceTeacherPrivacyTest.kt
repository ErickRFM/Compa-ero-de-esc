package org.companerodeescuela.feature.attendance

import androidx.lifecycle.viewModelScope
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.time.LocalDate
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.companerodeescuela.core.attendance.AttendanceRemoteClient
import org.companerodeescuela.core.attendance.AttendanceRepository
import org.companerodeescuela.core.attendance.AttendanceSyncEnqueuer
import org.companerodeescuela.core.database.AttendanceLocalStore
import org.companerodeescuela.core.database.LocalAttendanceRecord
import org.companerodeescuela.core.database.PendingAttendanceOperation
import org.companerodeescuela.core.network.ApiEnvironment
import org.companerodeescuela.core.network.SessionRefreshCoordinator
import org.companerodeescuela.core.network.createApiClient
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.*

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceTeacherPrivacyTest {
    @Test fun `roster permission denial clears cached teacher data and QR`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            fixture.populate()
            fixture.rosterStatus = HttpStatusCode.Forbidden
            fixture.model.refreshRoster()
            fixture.model.state.first { it.errorMessage != null }
            assertPrivateCleared(fixture.model.state.value)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test fun `unavailable authority hides cached private rows until revalidation`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            fixture.populate()
            fixture.rosterStatus = HttpStatusCode.ServiceUnavailable
            fixture.model.refreshRoster()
            fixture.model.state.first { it.errorMessage != null }
            assertPrivateCleared(fixture.model.state.value)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test fun `QR permission denial also clears cached roster and school entry data`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            fixture.populate()
            fixture.qrStatus = HttpStatusCode.Forbidden
            advanceTimeBy(15_001)
            runCurrent()
            fixture.model.state.first { it.qr == null && it.errorMessage != null }
            assertPrivateCleared(fixture.model.state.value)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test fun `review permission denial removes the private record rather than retaining its editor`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            fixture.populate()
            fixture.reviewStatus = HttpStatusCode.Forbidden
            fixture.model.reviewRecord(fixture.record, AttendanceStatus.VERIFIED, "QA teacher review")
            fixture.model.state.first { it.errorMessage != null }
            assertPrivateCleared(fixture.model.state.value)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test fun `close permission denial clears the session and private data`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            fixture.populate()
            fixture.closeStatus = HttpStatusCode.Forbidden
            fixture.model.closeTeacherSession()
            fixture.model.state.first { it.errorMessage != null }
            assertPrivateCleared(fixture.model.state.value)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test fun `campus permission denial invalidates the shared occurrence scope`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            fixture.populate()
            fixture.campusStatus = HttpStatusCode.Forbidden
            fixture.model.refresh()
            fixture.model.state.first { it.campusRosterError != null || it.errorMessage != null }
            assertPrivateCleared(fixture.model.state.value)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test fun `ordinary review validation error preserves authorized private rows`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            fixture.populate()
            fixture.reviewStatus = HttpStatusCode.BadRequest
            fixture.model.reviewRecord(fixture.record, AttendanceStatus.VERIFIED, "QA teacher review")
            fixture.model.state.first { it.errorMessage != null }
            assertNotNull(fixture.model.state.value.roster)
            assertNotNull(fixture.model.state.value.teacherSession)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test fun `late roster response cannot restore private data after QR denies authority`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            fixture.populate()
            fixture.rosterGate = CompletableDeferred()
            fixture.model.refreshRoster()
            fixture.rosterStarted.await()
            fixture.qrStatus = HttpStatusCode.Forbidden
            advanceTimeBy(15_001)
            runCurrent()
            fixture.model.state.first { it.qr == null && it.errorMessage != null }
            fixture.rosterGate!!.complete(Unit)
            fixture.rosterResponded.await()
            runCurrent()
            assertPrivateCleared(fixture.model.state.value)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    private fun assertPrivateCleared(state: AttendanceUiState) {
        assertNull(state.roster)
        assertNull(state.campusRoster)
        assertNull(state.qr)
        assertNull(state.teacherSession)
        kotlin.test.assertTrue(state.activeSessions.isEmpty())
        kotlin.test.assertTrue(state.occurrences.isEmpty())
    }

    private class Fixture {
        var rosterStatus = HttpStatusCode.OK
        var qrStatus = HttpStatusCode.OK
        var reviewStatus = HttpStatusCode.OK
        var closeStatus = HttpStatusCode.OK
        var campusStatus = HttpStatusCode.OK
        var rosterGate: CompletableDeferred<Unit>? = null
        val rosterStarted = CompletableDeferred<Unit>()
        val rosterResponded = CompletableDeferred<Unit>()
        private val now = System.currentTimeMillis() / 1_000
        private val date = LocalDate.now().toString()
        private val session = AttendanceSessionResponse("session", "occurrence", "course", "group", date,
            "08:00", "08:50", "teacher", now - 60, now + 3600, status = AttendanceSessionStatus.OPEN)
        val record = AttendanceRecordResponse("record", "op", "session", "occurrence", "student",
            AttendanceStatus.REVIEW_REQUIRED, AttendanceReasonCode.QR_VALID, now, now)
        private val occurrence = ClassOccurrenceContract("occurrence", null, "course", "group", "QA", "QA subject", "QA teacher",
            date, "08:00", "08:50", ClassOccurrenceStatusContract.SCHEDULED)
        private val client = createApiClient(ApiEnvironment("https://example.test/", "test"), MockEngine { request ->
            val path = request.url.encodedPath
            val status = when {
                path.endsWith("/roster") -> rosterStatus
                path.endsWith("/qr") -> qrStatus
                path.endsWith("/review") -> reviewStatus
                path.endsWith("/close") -> closeStatus
                path.endsWith("/campus-roster") -> campusStatus
                else -> HttpStatusCode.OK
            }
            if (path.endsWith("/roster")) rosterGate?.let { gate ->
                rosterStarted.complete(Unit)
                withContext(NonCancellable) { gate.await() }
                rosterResponded.complete(Unit)
            }
            val body = when {
                status != HttpStatusCode.OK -> """{"code":"forbidden","message":"QA scope unavailable"}"""
                path == "/academic/schedule/v2" -> Json.encodeToString(ApiResponse(AcademicWeekResponse("teacher", date, date, listOf(occurrence))))
                path == "/attendance/sessions/mine" -> Json.encodeToString(ApiResponse(listOf(session)))
                path.endsWith("/qr") -> Json.encodeToString(ApiResponse(AttendanceQrResponse("qa-qr", now, now + 25, 15)))
                path.endsWith("/campus-roster") -> Json.encodeToString(ApiResponse(TeacherCampusRosterResponse("occurrence", "group", "session", listOf(CampusRosterStudent("student", now, record)))))
                path.endsWith("/roster") -> Json.encodeToString(ApiResponse(AttendanceRosterResponse(session, listOf(record))))
                path.endsWith("/review") -> Json.encodeToString(ApiResponse(record))
                path.endsWith("/close") -> Json.encodeToString(ApiResponse(session.copy(status = AttendanceSessionStatus.CLOSED)))
                else -> error("Unexpected fixture request: $path")
            }
            respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        })
        private val tokenStore = object : SessionTokenStore {
            private var token: String? = "e30." + Base64.getUrlEncoder().withoutPadding().encodeToString(
                """{"sub":"teacher","exp":2000000000,"roles":["teacher"],"session_id":"qa-session"}""".toByteArray()) + ".signature"
            override suspend fun readAccessToken() = token
            override suspend fun writeAccessToken(token: String) { this.token = token }
            override suspend fun clear() { token = null }
        }
        val model = AttendanceViewModel(AttendanceRepository(EmptyLocalStore(), object : AttendanceSyncEnqueuer {
            override fun schedule() = Unit
        }, AttendanceRemoteClient(client, SessionRefreshCoordinator(client, tokenStore))))

        suspend fun populate() {
            model.state.first { it.roster != null && it.campusRoster != null && it.qr != null }
            assertNotNull(model.state.value.teacherSession)
        }
        fun close() { rosterGate?.complete(Unit); model.viewModelScope.cancel(); client.close() }
    }

    private class EmptyLocalStore : AttendanceLocalStore {
        override fun observe(ownerId: String): Flow<List<LocalAttendanceRecord>> = flowOf(emptyList())
        override suspend fun enqueue(operationId: String, ownerId: String, sessionId: String, deviceTimestampEpochSeconds: Long, qrToken: String?, schoolNetwork: SchoolNetworkEvidence?): LocalAttendanceRecord = error("No student capture in teacher fixture")
        override suspend fun nextReady(nowEpochSeconds: Long): PendingAttendanceOperation? = null
        override suspend fun recordRetry(operationId: String, nextAttemptAtEpochSeconds: Long, errorCode: String) = Unit
        override suspend fun markAuthRequired(operationId: String) = Unit
        override suspend fun markRejected(operationId: String, reasonCode: AttendanceReasonCode) = Unit
        override suspend fun markSynced(operationId: String, response: AttendanceRecordResponse) = Unit
        override suspend fun findLocal(operationId: String): LocalAttendanceRecord? = null
        override suspend fun clearOwner(ownerId: String) = Unit
    }
}
