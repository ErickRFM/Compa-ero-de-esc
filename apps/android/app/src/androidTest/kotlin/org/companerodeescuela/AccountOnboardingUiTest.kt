package org.companerodeescuela

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.companerodeescuela.core.designsystem.theme.CompaneroTheme
import org.companerodeescuela.feature.auth.AccountOnboardingScreen
import org.companerodeescuela.feature.auth.SessionUiState
import org.companerodeescuela.shared.contracts.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AccountOnboardingUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun pendingEmailExposesConnectedActionsAndHonestUnavailableState() {
        var confirmed: String? = null
        var resent = 0
        var loggedOut = 0
        compose.setContent { AuthTestLocale { CompaneroTheme {
            AccountOnboardingScreen(SessionUiState(checking = false, authenticated = true, email = "qa@example.test",
                accountStatus = AccountStatus.PENDING_VERIFICATION, verificationDelivery = VerificationDeliveryStatus.UNAVAILABLE),
                { confirmed = it }, { resent++ }, {}, { loggedOut++ })
        } } }
        compose.onNodeWithText("El servicio de correo no está configurado. Tu cuenta sigue pendiente.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("verification_confirm").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("verification_code").performScrollTo().performTextInput("A".repeat(43))
        compose.onNodeWithTag("verification_confirm").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("A".repeat(43), confirmed) }
        compose.onNodeWithText("Reenviar correo").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, resent) }
        compose.onNodeWithText("Cerrar sesión").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, loggedOut) }
    }
    @Test fun pendingApprovalCannotUseEmailConfirmationOrAcademicNavigation() {
        var checked = 0
        compose.setContent { AuthTestLocale { CompaneroTheme {
            AccountOnboardingScreen(SessionUiState(checking = false, authenticated = true,
                accountStatus = AccountStatus.PENDING_APPROVAL, emailVerified = true),
                { error("Approval is not email confirmation") }, { error("No email resend") }, { checked++ }, {})
        } } }
        compose.onNodeWithText("Solicitud pendiente").assertIsDisplayed()
        compose.onNodeWithTag("verification_code").assertDoesNotExist()
        compose.onNodeWithText("Pase de lista").assertDoesNotExist()
        compose.onNodeWithText("Comprobar estado").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, checked) }
    }
}
