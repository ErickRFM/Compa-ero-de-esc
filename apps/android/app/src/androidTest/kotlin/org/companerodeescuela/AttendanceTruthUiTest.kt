package org.companerodeescuela

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.companerodeescuela.core.database.LocalAttendanceRecord
import org.companerodeescuela.core.database.LocalAttendanceSyncState
import org.companerodeescuela.core.designsystem.theme.CompaneroTheme
import org.companerodeescuela.feature.attendance.AttendanceMode
import org.companerodeescuela.feature.attendance.AttendanceUiState
import org.companerodeescuela.feature.attendance.studentAttendanceContent
import org.companerodeescuela.shared.contracts.AttendanceDisposition
import org.companerodeescuela.shared.contracts.AttendanceReasonCode
import org.companerodeescuela.shared.contracts.AttendanceRecordResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionStatus
import org.companerodeescuela.shared.contracts.AttendanceStatus
import org.companerodeescuela.shared.contracts.NetworkVerificationMethod
import org.companerodeescuela.shared.contracts.SchoolPresenceResponse
import org.companerodeescuela.shared.contracts.SchoolPresenceStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The same stateless V8 content used by attendanceScreen; fixtures never grant API authority. */
class AttendanceTruthUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun recordedEntryAllowsReviewCaptureAndDoesNotClaimVerifiedPresence() {
        var confirmations = 0
        render(state()) { confirmations++ }
        compose.onNodeWithText("Jornada escolar").assertExists()
        compose.onNodeWithText("Registrada").assertExists()
        compose.onNodeWithText("Verificada").assertDoesNotExist()
        compose
            .onNodeWithText("Confirmar pase de lista")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        assertEquals(1, confirmations)
    }

    @Test fun legacyAutomaticPresentAndSyncedVerifiedCacheAreNotDisplayedAsVerified() {
        val legacy = record().copy(status = AttendanceStatus.VERIFIED, disposition = AttendanceDisposition.PRESENT)
        val local =
            LocalAttendanceRecord(
                "cached",
                "student",
                "session",
                LocalAttendanceSyncState.SYNCED,
                AttendanceStatus.VERIFIED,
                AttendanceReasonCode.QR_VALID,
                1,
            )
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
        val human =
            record().copy(
                status = AttendanceStatus.VERIFIED,
                reasonCode = AttendanceReasonCode.TEACHER_REVIEW,
                reviewedBy = "teacher",
                reviewedAtEpochSeconds = 1,
            )
        render(state().copy(confirmedClassCalls = mapOf("session" to human)))
        compose.onNodeWithText("Evidencia validada por docente").performScrollTo().assertExists()
        compose.onNodeWithText("Registro recibido · requiere revisión docente").assertDoesNotExist()
        compose.onNodeWithText("Asistencia confirmada").assertDoesNotExist()
    }

    @Test fun closedClassHistoryRemainsVisibleAndRequiresTeacherReview() {
        render(
            state().copy(
                activeSessions = emptyList(),
                studentHistoryLoaded = true,
                studentHistory = listOf(record().copy(sessionId = "closed-session")),
            ),
        )
        compose.onNodeWithText("Mi historial").performScrollTo().assertExists()
        compose.onNodeWithText("Registro recibido · requiere revisión docente").performScrollTo().assertExists()
        compose.onNodeWithText("Asistencia confirmada").assertDoesNotExist()
    }

    @Test fun activeRecordIsNotDuplicatedInHistory() {
        render(
            state().copy(
                studentHistoryLoaded = true,
                studentHistory = listOf(record()),
                confirmedClassCalls = mapOf("session" to record()),
            ),
        )
        compose.onAllNodesWithText("Registro recibido · requiere revisión docente").assertCountEquals(1)
    }

    @Test fun campusQrHasOneEntryPointAndNoDuplicateScannerCard() {
        val noCampusEntry = state().copy(
            activeSessions = emptyList(),
            schoolPresence = null,
            schoolPresenceReceivedRealtime = null,
        )
        render(noCampusEntry)
        compose.onAllNodesWithText("Escanear QR institucional").assertCountEquals(1)
        compose.onNodeWithText("Esperando pase del docente").assertExists()
        compose.onNodeWithText("Confirmar pase de lista").assertDoesNotExist()
    }

    @Test fun teacherContingencyIsHiddenUntilStudentOpensIt() {
        render(state())
        compose.onNodeWithText("Escanear QR del docente").assertDoesNotExist()
        compose.onNodeWithText("¿Problemas con el pase? · Contingencia")
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("Escanear QR del docente").assertExists()
    }

    private fun render(
        state: AttendanceUiState,
        onConfirm: (String) -> Unit = {},
    ) {
        compose.setContent {
            CompaneroTheme {
                studentAttendanceContent(state, {}, {}, {}, {}, onConfirm, {}, {}, {}, Modifier)
            }
        }
    }

    private fun state(): AttendanceUiState {
        val now = System.currentTimeMillis() / 1_000
        val session =
            AttendanceSessionResponse(
                "session",
                "occurrence",
                "QA subject",
                "QA group",
                "2026-10-09",
                "08:00",
                "08:50",
                "teacher",
                now - 30,
                now + 3600,
                status = AttendanceSessionStatus.OPEN,
            )
        return AttendanceUiState(
            mode = AttendanceMode.STUDENT,
            loading = false,
            activeSessions = listOf(session),
            schoolPresence =
                SchoolPresenceResponse(
                    "entry",
                    "student",
                    now - 60,
                    now + 3600,
                    status = SchoolPresenceStatus.ACTIVE,
                    qrVerified = true,
                    networkVerified = true,
                    networkVerificationMethod = NetworkVerificationMethod.SSID,
                    serverTimeEpochSeconds = now,
                ),
            schoolPresenceReceivedRealtime = android.os.SystemClock.elapsedRealtime(),
        )
    }

    private fun record() =
        AttendanceRecordResponse(
            "record",
            "op",
            "session",
            "occurrence",
            "student",
            AttendanceStatus.REVIEW_REQUIRED,
            AttendanceReasonCode.CLASS_CALL_CONFIRMED,
            1,
            1,
        )
}
