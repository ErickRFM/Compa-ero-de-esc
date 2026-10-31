package org.companerodeescuela

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.companerodeescuela.core.designsystem.theme.CompaneroTheme
import org.companerodeescuela.feature.admin.AdminUiState
import org.companerodeescuela.feature.admin.adminHomeScreenContent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Regression test for D1: admin onOpenSchedule must navigate to Schedule, not Classrooms.
 *
 * The button label comes from R.string.admin_schedule = "Horarios" in feature/home strings.xml.
 */
class AdminScheduleNavigationRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun adminScheduleButtonInvokesOnOpenScheduleNotOnOpenClassrooms() {
        var scheduleClicked = false
        var classroomsClicked = false
        compose.setContent {
            AuthTestLocale {
                CompaneroTheme {
                    adminHomeScreenContent(
                        state = AdminUiState(loading = false),
                        onRefresh = {},
                        onApprove = { _, _, _, _ -> },
                        onLifecycle = { _, _ -> },
                        onRevokeAssignment = {},
                        onAuthorize = { _, _ -> },
                        onCancel = {},
                        onOpenSchedule = { scheduleClicked = true },
                        onOpenChannel = {},
                        onOpenClassrooms = { classroomsClicked = true },
                        onOpenSecurity = {},
                    )
                }
            }
        }
        // The button text comes from R.string.admin_schedule = "Horarios"
        compose.onNodeWithText("Horarios").performScrollTo().performClick()
        assertTrue("onOpenSchedule must be called when tapping Horarios", scheduleClicked)
        assertFalse("onOpenClassrooms must NOT be called for the schedule button", classroomsClicked)
    }
}
