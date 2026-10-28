package org.companerodeescuela

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.companerodeescuela.core.designsystem.theme.CompaneroTheme
import org.companerodeescuela.feature.coordinator.coordinationAttendanceIndicators
import org.companerodeescuela.shared.contracts.CoordinationOverview
import org.junit.Rule
import org.junit.Test

class CoordinationAttendanceIndicatorsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun missingCountersNeverAppearAsZero() {
        render(CoordinationOverview(activeGroups = 1, observedAtEpochSeconds = 1))
        compose.onNodeWithText("Pases abiertos: no disponible").assertExists()
        compose.onNodeWithText("Revisiones pendientes: no disponible").assertExists()
        compose.onNodeWithText("0 pases abiertos").assertDoesNotExist()
        compose.onNodeWithText("0 revisiones pendientes").assertDoesNotExist()
    }

    @Test fun reportedZeroAndPendingReviewsRemainDistinct() {
        render(CoordinationOverview(1, 1, openAttendancePasses = 0, pendingAttendanceReviews = 3))
        compose.onNodeWithText("0 pases abiertos").assertExists()
        compose.onNodeWithText("3 revisiones pendientes").assertExists()
        compose.onNodeWithText("Revisiones pendientes: no disponible").assertDoesNotExist()
    }

    @Test fun limitedCountersExplainTheirScope() {
        render(
            CoordinationOverview(
                1,
                1,
                openAttendancePasses = 2,
                pendingAttendanceReviews = 5,
                attendanceSessionsSampleLimit = 100,
                attendanceSampleLimited = true,
            ),
        )
        compose.onNodeWithText("2 pases abiertos").assertExists()
        compose.onNodeWithText("5 revisiones pendientes").assertExists()
        compose.onNodeWithText("Vista parcial: hasta 100 sesiones recientes. Los conteos corresponden a esa muestra.").assertExists()
    }

    private fun render(overview: CoordinationOverview) {
        compose.setContent { CompaneroTheme { coordinationAttendanceIndicators(overview) } }
    }
}
