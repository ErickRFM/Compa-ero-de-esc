package org.companerodeescuela

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.companerodeescuela.feature.settings.AppLanguage
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith

/** Real MainActivity login: Compose semantics are authoritative for Compose UI. */
@RunWith(AndroidJUnit4::class)
class LoginV8SmokeTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun registrationAccountTypeCanBeSelectedByItsLabel() {
        assumeAcademicDistribution()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.appearancePreferences.setLanguage(AppLanguage.SPANISH) }
            waitForText("Bienvenido de nuevo")
            compose.onNodeWithTag("login_registration").performScrollTo().performClick()
            compose.onNodeWithText("Alumno").performScrollTo().assertExists()
            compose.onNodeWithText("Docente").performScrollTo().performClick()
            waitForText("requieren verificación")
            compose.onNodeWithText("Ya tengo cuenta").performScrollTo().performClick()
            waitForText("Bienvenido de nuevo")
        }
    }

    @Test
    fun signedOutUserSeesInstitutionalLogin() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.appearancePreferences.setLanguage(AppLanguage.SPANISH) }
            waitForText("Bienvenido de nuevo")
            compose.onNodeWithTag("login_username").performScrollTo().assertExists()
            compose.onNodeWithTag("login_password").performScrollTo().assertExists()
        }
    }

    @Test
    fun firstAccessOpensInstitutionalIdentityValidation() {
        assumeAcademicDistribution()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.appearancePreferences.setLanguage(AppLanguage.SPANISH) }
            waitForText("Bienvenido de nuevo")
            compose.onNodeWithTag("login_registration").performScrollTo().performClick()
            compose.onNodeWithTag("registration_name").performScrollTo().assertExists()
            compose.onNodeWithTag("registration_email").performScrollTo().assertExists()
        }
    }

    private fun waitForText(text: String) {
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(text, substring = true).assertExists()
    }

    private fun assumeAcademicDistribution() {
        val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        assumeTrue("Registration is available only in the academic distribution", packageName == "org.companerodeescuela")
    }
}
