package org.companerodeescuela

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import org.companerodeescuela.core.designsystem.theme.CompaneroTheme
import org.companerodeescuela.feature.attendance.AttendanceMode
import org.companerodeescuela.feature.attendance.AttendanceUiState
import org.companerodeescuela.feature.attendance.teacherAttendanceContent
import org.companerodeescuela.shared.contracts.AttendanceReasonCode
import org.companerodeescuela.shared.contracts.AttendanceRecordResponse
import org.companerodeescuela.shared.contracts.AttendanceRosterResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionStatus
import org.companerodeescuela.shared.contracts.AttendanceStatus
import org.junit.Rule
import org.junit.Test

/** Actual shared teacher content; synthetic inputs carry no API authority. */
class TeacherAttendanceScopeUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lossOfScopeDismissesPendingCloseConfirmation() {
        val current = mutableStateOf(state())
        render(current)
        compose.onNodeWithText("Cerrar pase").performScrollTo().performClick()
        compose.onNodeWithText("¿Cerrar el pase?").assertExists()
        compose.runOnIdle { current.value = AttendanceUiState(mode = AttendanceMode.TEACHER, loading = false, teacherScopeGeneration = 1) }
        compose.onNodeWithText("¿Cerrar el pase?").assertDoesNotExist()
    }

    @Test fun revalidatedScopeDropsOldPrivateReviewDraftEvenForSameRecordId() {
        val current = mutableStateOf(state())
        render(current)
        compose
            .onNode(hasScrollToNodeAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToNode(hasText("Revisar registro"))
        compose.onNodeWithText("Revisar registro").performScrollTo().performClick()
        compose.onNodeWithText("Validar por excepción docente").performScrollTo().performClick()
        compose.onNodeWithText("Motivo").performTextInput("QA private draft")
        compose.onNodeWithText("Motivo del ajuste").assertExists()
        compose.runOnIdle { current.value = current.value.copy(teacherScopeGeneration = 1) }
        compose.onNodeWithText("Motivo del ajuste").assertDoesNotExist()
        compose.onNodeWithText("Revisar registro").performScrollTo().performClick()
        compose.onNodeWithText("Validar por excepción docente").performScrollTo().performClick()
        compose.onNodeWithText("QA private draft").assertDoesNotExist()
    }

    private fun render(current: androidx.compose.runtime.MutableState<AttendanceUiState>) {
        compose.setContent {
            CompaneroTheme {
                teacherAttendanceContent(current.value, {}, {}, {}, {}, {}, {}, { _, _, _ -> }, { _, _, _ -> }, Modifier)
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
        val record =
            AttendanceRecordResponse(
                "record",
                "op",
                "session",
                "occurrence",
                "QA student",
                AttendanceStatus.REVIEW_REQUIRED,
                AttendanceReasonCode.QR_VALID,
                now,
                now,
            )
        return AttendanceUiState(
            mode = AttendanceMode.TEACHER,
            loading = false,
            teacherSession = session,
            roster = AttendanceRosterResponse(session, listOf(record)),
        )
    }
}
