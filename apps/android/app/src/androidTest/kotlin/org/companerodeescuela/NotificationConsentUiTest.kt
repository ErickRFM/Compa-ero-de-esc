package org.companerodeescuela

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.companerodeescuela.core.designsystem.theme.CompaneroTheme
import org.companerodeescuela.feature.profile.ProfileScreen
import org.companerodeescuela.shared.contracts.UserRole
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Opt-in must be a real existing-profile action, not a second invented settings screen. */
class NotificationConsentUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun notificationRowInvokesOptInCallback() {
        var count = 0
        compose.setContent {
            CompaneroTheme {
                ProfileScreen(
                    "Student",
                    setOf(UserRole.STUDENT),
                    onAppearance = {},
                    onLogout = {},
                    onNotifications = { count++ },
                )
            }
        }
        compose.onNodeWithText("Notificaciones").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, count) }
    }

    @Test fun notificationRowIsAbsentWithoutFirebaseConfig() {
        compose.setContent {
            CompaneroTheme {
                ProfileScreen(
                    "Student",
                    setOf(UserRole.STUDENT),
                    onAppearance = {},
                    onLogout = {},
                )
            }
        }
        compose.onNodeWithText("Notificaciones").assertDoesNotExist()
    }

    @Test fun notificationRegistrationFailureIsVisibleWithoutClaimingSuccess() {
        val message = "No se pudo registrar este dispositivo. Revisa el estado de tu cuenta y vuelve a intentarlo."
        compose.setContent {
            CompaneroTheme {
                ProfileScreen(
                    "Student",
                    setOf(UserRole.STUDENT),
                    onAppearance = {},
                    onLogout = {},
                    onNotifications = {},
                    notificationsStatus = message,
                )
            }
        }
        compose.onNodeWithText(message).performScrollTo().assertExists()
        compose.onNodeWithText("Dispositivo registrado.", substring = true).assertDoesNotExist()
    }
}
