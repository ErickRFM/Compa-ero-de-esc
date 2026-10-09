package org.companerodeescuela

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.viewModelScope
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.*
import java.time.LocalDate
import java.util.Base64
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.companerodeescuela.core.attendance.*
import org.companerodeescuela.core.database.*
import org.companerodeescuela.core.designsystem.theme.CompaneroTheme
import org.companerodeescuela.core.network.*
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.feature.attendance.AttendanceScreen
import org.companerodeescuela.feature.attendance.AttendanceMode
import org.companerodeescuela.feature.attendance.AttendanceViewModel
import org.companerodeescuela.shared.contracts.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Actual screen, ViewModel, repository and HTTP boundary; no production school data. */
class TeacherAttendanceReviewUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var fixture: Fixture

    @After fun close() {
        if (::fixture.isInitialized) {
            compose.runOnIdle { fixture.model.viewModelScope.cancel() }
            fixture.client.close()
        }
    }

    @Test fun validationFailurePreservesSubmittedReasonAndDecisionUntilSuccess() {
        openEditor()
        submitAndAwait(HttpStatusCode.BadRequest, 1)
        compose.onNodeWithText("Motivo del ajuste").assertExists()
        compose.onNodeWithText("QA correction reason").assertExists()
        submitAndAwait(HttpStatusCode.OK, 2)
        compose.onNodeWithText("Motivo del ajuste").assertDoesNotExist()
        assertEquals(listOf(AttendanceDisposition.PRESENT, AttendanceDisposition.PRESENT), fixture.decisions)
        assertEquals(listOf("QA correction reason", "QA correction reason"), fixture.reasons)
    }

    @Test fun permissionLossAfterValidationFailureDropsSubmittedPrivateDraft() {
        openEditor()
        submitAndAwait(HttpStatusCode.BadRequest, 1)
        compose.onNodeWithText("QA correction reason").assertExists()
        submitAndAwait(HttpStatusCode.Forbidden, 2)
        compose.onNodeWithText("Motivo del ajuste").assertDoesNotExist()
        compose.onNodeWithText("QA correction reason").assertDoesNotExist()
        assertEquals(null, fixture.model.state.value.roster)
    }

    private fun openEditor() {
        fixture = Fixture()
        compose.setContent { CompaneroTheme { AttendanceScreen(requestedMode = AttendanceMode.TEACHER, viewModel = fixture.model) } }
        compose.waitUntil(10_000) { fixture.model.state.value.roster != null }
        compose.onNodeWithText("Revisar registro").performScrollTo().performClick()
        compose.onNodeWithText("Marcar presente").performScrollTo().performClick()
        compose.onNodeWithText("Motivo").performTextInput("QA correction reason")
    }

    private fun submitAndAwait(status: HttpStatusCode, count: Int) {
        compose.runOnIdle { fixture.reviewStatus = status }
        compose.onNodeWithText("Confirmar ajuste").performClick()
        compose.waitUntil(10_000) { fixture.reviewRequests == count && !fixture.model.state.value.actionInProgress }
        compose.waitForIdle()
    }

    private class Fixture {
        @Volatile var reviewStatus = HttpStatusCode.BadRequest
        @Volatile var reviewRequests = 0
        val decisions = mutableListOf<AttendanceDisposition?>()
        val reasons = mutableListOf<String?>()
        private val now = System.currentTimeMillis() / 1_000
        private val date = LocalDate.now().toString()
        private val session = AttendanceSessionResponse("session", "occurrence", "course", "group", date,
            "08:00", "08:50", "teacher", now - 60, now + 3600, status = AttendanceSessionStatus.OPEN)
        private var record = AttendanceRecordResponse("record", "op", "session", "occurrence", "QA student",
            AttendanceStatus.REVIEW_REQUIRED, AttendanceReasonCode.QR_VALID, now, now)
        private val occurrence = ClassOccurrenceContract("occurrence", null, "course", "group", "QA", "QA subject", "QA teacher",
            date, "08:00", "08:50", ClassOccurrenceStatusContract.SCHEDULED)
        val client = createApiClient(ApiEnvironment("https://example.test/", "test"), MockEngine { request ->
            val path = request.url.encodedPath
            val status = if (path.endsWith("/review")) reviewStatus else HttpStatusCode.OK
            if (path.endsWith("/review")) {
                val input = Json.decodeFromString<ReviewAttendanceRequest>(request.body.toByteArray().decodeToString())
                decisions += input.disposition
                reasons += input.note
                reviewRequests++
                if (status == HttpStatusCode.OK) record = record.copy(disposition = input.disposition,
                    reviewedBy = "teacher", reviewedAtEpochSeconds = now)
            }
            val body = when {
                status != HttpStatusCode.OK -> """{"code":"bad_request","message":"QA adjustment unavailable"}"""
                path == "/academic/schedule/v2" -> Json.encodeToString(ApiResponse(AcademicWeekResponse("teacher", date, date, listOf(occurrence))))
                path == "/attendance/sessions/mine" -> Json.encodeToString(ApiResponse(listOf(session)))
                path.endsWith("/qr") -> Json.encodeToString(ApiResponse(AttendanceQrResponse("qa-qr", now, now + 300, 60)))
                path.endsWith("/campus-roster") -> Json.encodeToString(ApiResponse(TeacherCampusRosterResponse("occurrence", "group", "session", emptyList())))
                path.endsWith("/roster") -> Json.encodeToString(ApiResponse(AttendanceRosterResponse(session, listOf(record))))
                path.endsWith("/review") -> Json.encodeToString(ApiResponse(record))
                else -> error("Unexpected fixture request: $path")
            }
            respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        })
        private val store = object : SessionTokenStore {
            private var token: String? = "e30." + Base64.getUrlEncoder().withoutPadding().encodeToString(
                """{"sub":"teacher","exp":2000000000,"roles":["teacher"],"session_id":"qa-session"}""".toByteArray()) + ".test"
            override suspend fun readAccessToken() = token
            override suspend fun writeAccessToken(token: String) { this.token = token }
            override suspend fun clear() { token = null }
        }
        val model = AttendanceViewModel(AttendanceRepository(EmptyLocalStore(), object : AttendanceSyncEnqueuer {
            override fun schedule() = Unit
        }, AttendanceRemoteClient(client, SessionRefreshCoordinator(client, store))))
    }

    private class EmptyLocalStore : AttendanceLocalStore {
        override fun observe(ownerId: String): Flow<List<LocalAttendanceRecord>> = flowOf(emptyList())
        override suspend fun enqueue(operationId: String, ownerId: String, sessionId: String, deviceTimestampEpochSeconds: Long,
            qrToken: String?, schoolNetwork: SchoolNetworkEvidence?): LocalAttendanceRecord = error("No student capture")
        override suspend fun nextReady(nowEpochSeconds: Long): PendingAttendanceOperation? = null
        override suspend fun recordRetry(operationId: String, nextAttemptAtEpochSeconds: Long, errorCode: String) = Unit
        override suspend fun markAuthRequired(operationId: String) = Unit
        override suspend fun markRejected(operationId: String, reasonCode: AttendanceReasonCode) = Unit
        override suspend fun markSynced(operationId: String, response: AttendanceRecordResponse) = Unit
        override suspend fun findLocal(operationId: String): LocalAttendanceRecord? = null
        override suspend fun clearOwner(ownerId: String) = Unit
    }
}
