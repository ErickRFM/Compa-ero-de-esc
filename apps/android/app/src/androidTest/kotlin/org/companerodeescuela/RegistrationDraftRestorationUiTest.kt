package org.companerodeescuela

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.companerodeescuela.core.designsystem.theme.CompaneroTheme
import org.companerodeescuela.feature.auth.*
import org.companerodeescuela.shared.contracts.*
import org.junit.Rule
import org.junit.Test

class RegistrationDraftRestorationUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun registration() {
        compose.activityRule.scenario.onActivity { activity -> activity.setContent {
            AuthTestLocale { CompaneroTheme {
                RegistrationScreen(SessionUiState(checking=false, institutions=listOf(InstitutionSummary("qa-campus","QA campus"))), {}, {})
            } }
        } }
    }
    @Test fun populatedTutorSecretsSurviveActualActivityRecreation() {
        registration()
        compose.onNodeWithTag("registration_type_TUTOR").performScrollTo().performClick()
        compose.onNodeWithTag("registration_password").performScrollTo().performTextInput("draft-password")
        compose.onNodeWithTag("registration_confirmation").performScrollTo().performTextInput("draft-password")
        compose.onNodeWithTag("registration_identity").performScrollTo().performTextInput("professional-reference")
        compose.activityRule.scenario.recreate()
        registration()
        compose.onNodeWithTag("registration_type_TUTOR").performScrollTo().performClick()
        compose.onNodeWithTag("registration_password").performScrollTo().assertTextContains("draft-password")
        compose.onNodeWithTag("registration_confirmation").performScrollTo().assertTextContains("draft-password")
        compose.onNodeWithTag("registration_identity").performScrollTo().assertTextContains("professional-reference")
    }
    private fun onboarding() {
        compose.activityRule.scenario.onActivity { activity -> activity.setContent {
            AuthTestLocale { CompaneroTheme {
                AccountOnboardingScreen(SessionUiState(checking=false,authenticated=true,userId="qa-pending",accountStatus=AccountStatus.PENDING_VERIFICATION),{}, {}, {}, {})
            } }
        } }
    }
    @Test fun pastedVerificationCodeSurvivesActualActivityRecreation() {
        onboarding()
        compose.onNodeWithTag("verification_code").performScrollTo().performTextInput("A".repeat(43))
        compose.activityRule.scenario.recreate()
        onboarding()
        compose.onNodeWithTag("verification_code").performScrollTo().assertTextContains("A".repeat(43))
        compose.onNodeWithTag("verification_confirm").performScrollTo().assertIsEnabled()
    }
}
