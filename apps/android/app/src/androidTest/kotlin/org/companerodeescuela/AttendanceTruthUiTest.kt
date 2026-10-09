package org.companerodeescuela

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.companerodeescuela.core.database.LocalAttendanceRecord
import org.companerodeescuela.core.database.LocalAttendanceSyncState
import org.companerodeescuela.core.designsystem.theme.CompaneroTheme
import org.companerodeescuela.feature.attendance.AttendanceMode
import org.companerodeescuela.feature.attendance.AttendanceUiState
import org.companerodeescuela.feature.attendance.StudentAttendanceContent
import org.companerodeescuela.shared.contracts.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The same stateless V8 content used by AttendanceScreen; fixtures never grant API authority. */
class AttendanceTruthUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun recordedEntryAllowsReviewCaptureAndDoesNotClaimVerifiedPresence() {
        var confirmations = 0
        render(state()) { confirmations++ }
        compose.onNodeWithText("Ingreso registrado · presencia sin verificar").assertExists()
        compose.onNodeWithText("Verificada").assertDoesNotExist()
        compose.onNodeWithText("Enviar pase para revisión").performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, confirmations)
    }

    @Test fun legacyAutomaticPresentAndSyncedVerifiedCacheAreNotDisplayedAsVerified() {
        val legacy = record().copy(status = AttendanceStatus.VERIFIED, disposition = AttendanceDisposition.PRESENT)
        val local = LocalAttendanceRecord("cached", "student", "session", LocalAttendanceSyncState.SYNCED,
            AttendanceStatus.VERIFIED, AttendanceReasonCode.QR_VALID, 1)
        render(state().copy(confirmedClassCalls = mapOf("session" to legacy), localRecords = listOf(local)))
        compose.onNodeWithText("Asistencia confirmada").assertDoesNotExist()
        compose.onNodeWithText("Verificada por el servidor").assertDoesNotExist()
        compose.onNodeWithText("Registro recibido · requiere revisión docente").performScrollTo().assertExists()
    }

    @Test fun humanReviewedDispositionRemainsVisible() {
        val human = record().copy(disposition = AttendanceDisposition.PRESENT, reviewedBy = "teacher", reviewedAtEpochSeconds = 1)
        render(state().copy(confirmedClassCalls = mapOf("session" to human)))
        compose.onNodeWithText("Asistencia confirmada").performScrollTo().assertExists()
    }

    @Test fun humanExceptionValidationWithoutDispositionDoesNotRemainPending() {
        val human = record().copy(status = AttendanceStatus.VERIFIED, reasonCode = AttendanceReasonCode.TEACHER_REVIEW,
            reviewedBy = "teacher", reviewedAtEpochSeconds = 1)
        render(state().copy(confirmedClassCalls = mapOf("session" to human)))
        compose.onNodeWithText("Evidencia validada por docente").performScrollTo().assertExists()
        compose.onNodeWithText("Registro recibido · requiere revisión docente").assertDoesNotExist()
        compose.onNodeWithText("Asistencia confirmada").assertDoesNotExist()
    }

    private fun render(state: AttendanceUiState, onConfirm: (String) -> Unit = {}) {
        compose.setContent {
            CompaneroTheme {
                StudentAttendanceContent(state, {}, {}, {}, {}, onConfirm, {}, {}, {}, Modifier)
            }
        }
    }

    private fun state(): AttendanceUiState {
        val now = System.currentTimeMillis() / 1_000
        val session = AttendanceSessionResponse("session", "occurrence", "QA subject", "QA group", "2026-10-09",
            "08:00", "08:50", "teacher", now - 30, now + 3600, status = AttendanceSessionStatus.OPEN)
        return AttendanceUiState(mode = AttendanceMode.STUDENT, loading = false, activeSessions = listOf(session),
            schoolPresence = SchoolPresenceResponse("entry", "student", now - 60, now + 3600,
                status = SchoolPresenceStatus.ACTIVE, qrVerified = true, networkVerified = true,
                networkVerificationMethod = NetworkVerificationMethod.SSID, serverTimeEpochSeconds = now),
            schoolPresenceReceivedRealtime = android.os.SystemClock.elapsedRealtime())
    }

    private fun record() = AttendanceRecordResponse("record", "op", "session", "occurrence", "student",
        AttendanceStatus.REVIEW_REQUIRED, AttendanceReasonCode.CLASS_CALL_CONFIRMED, 1, 1)
}
