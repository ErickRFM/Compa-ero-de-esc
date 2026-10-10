package org.companerodeescuela

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import org.companerodeescuela.feature.auth.RegistrationScreen
import androidx.compose.ui.semantics.SemanticsProperties
import org.companerodeescuela.core.designsystem.theme.CompaneroTheme
import org.companerodeescuela.core.motion.ProvideCompaneroMotionPreferences
import org.companerodeescuela.core.navigation.AppExperience
import org.companerodeescuela.feature.auth.LoginScreen
import org.companerodeescuela.feature.auth.SessionUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class SmartRoleLoginUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun registrationKeepsItsDarkV8ContrastInsideALightAppTheme() {
        compose.setContent {
            CompaneroTheme(darkTheme = false) {
                RegistrationScreen(SessionUiState(checking = false), { _, _, _, _ -> }, {})
            }
        }
        // The existing white registration hero requires the legacy dark frame.
        val background = compose.onRoot().captureToImage().toPixelMap()[0, 0]
        org.junit.Assert.assertTrue("Registration background must remain dark", background.luminance() < 0.1f)
    }
    @Test fun roleChangesPreserveOneFormAndOnlySendDesiredAccess() {
        var submitted: Triple<String, String, AppExperience>? = null
        compose.setContent { LoginTestContent(onLogin = { u, p, role -> submitted = Triple(u, p, role) }) }
        compose.onNodeWithTag("login_username").performScrollTo().performTextInput("qa.docente")
        compose.onNodeWithTag("login_password").performScrollTo().performTextInput("test-password")
        compose.onNodeWithTag("role_TUTOR").performScrollTo().performClick()
        // The keyboard may obscure the card; assert selected access and the retained shared form.
        compose.onNodeWithTag("role_TUTOR").assertIsSelected()
        compose.onNodeWithTag("login_registration").assertDoesNotExist()
        compose.onNodeWithTag("login_submit").performScrollTo().performClick()
        assertEquals(Triple("qa.docente", "test-password", AppExperience.TUTOR), submitted)
    }

    @Test fun swipeArrowAndIndicatorSelectAllFiveAccesses() {
        compose.setContent { LoginTestContent() }
        compose.onNodeWithTag("role_pager").performScrollTo().performTouchInput { swipeLeft() }
        compose.onNodeWithTag("role_title_TEACHER", useUnmergedTree = true).assertIsDisplayed()
        captureAccess("teacher")
        compose.onNodeWithTag("access_next").performScrollTo().performClick()
        compose.onNodeWithTag("role_pager").performScrollTo()
        compose.onNodeWithTag("role_title_TUTOR", useUnmergedTree = true).assertIsDisplayed()
        captureAccess("tutor")
        compose.onNodeWithTag("role_ADMIN").performScrollTo().performClick()
        compose.onNodeWithTag("role_title_ADMIN", useUnmergedTree = true).assertIsDisplayed()
        captureAccess("admin")
        compose.onNodeWithTag("role_SUPER_ADMIN").performClick()
        compose.onNodeWithTag("role_title_SUPER_ADMIN", useUnmergedTree = true).assertIsDisplayed()
        captureAccess("super_admin")
        compose.onNodeWithTag("access_next").assertIsNotEnabled()
        compose.onNodeWithTag("role_STUDENT").performClick()
        compose.onNodeWithTag("role_title_STUDENT", useUnmergedTree = true).assertIsDisplayed()
        captureAccess("student")
        compose.onNodeWithTag("access_previous").assertIsNotEnabled()
    }

    private fun captureAccess(name: String) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "v10-evidence")
        directory.mkdirs()
        File(directory, "access-" + name + ".png").outputStream().use {
            org.junit.Assert.assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
    @Test fun rotationRestoresIdentifierAndAccessWithoutSavingPassword() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { LoginTestContent() }
        compose.onNodeWithTag("login_username").performScrollTo().performTextInput("qa.alumno")
        compose.onNodeWithTag("login_password").performScrollTo().performTextInput("test-password")
        compose.onNodeWithTag("role_SUPER_ADMIN").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("login_username").performScrollTo().assertTextContains("qa.alumno")
        compose.onNodeWithTag("login_password").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithTag("role_SUPER_ADMIN").performScrollTo().assertIsSelected()
    }

    @Test fun submittingDisablesFormAndAccessControls() {
        val state = mutableStateOf(SessionUiState(checking = false))
        compose.setContent { LoginTestContent(state = state.value) }
        compose.onNodeWithTag("login_username").performScrollTo().performTextInput("qa.alumno")
        compose.onNodeWithTag("login_password").performScrollTo().performTextInput("test-password")
        compose.runOnIdle { state.value = state.value.copy(submitting = true) }
        compose.onNodeWithTag("login_submit").assertIsNotEnabled()
        compose.onNodeWithTag("login_username").assertIsNotEnabled()
        compose.onNodeWithTag("role_TUTOR").performScrollTo().assertIsNotEnabled()
    }

    @Test fun englishHighContrastAndReducedMotionUseExistingPreferences() {
        compose.setContent { LoginTestContent(language = "en", highContrast = true, reducedMotion = true, dark = false) }
        compose.onNodeWithTag("role_title_STUDENT", useUnmergedTree = true).assertTextEquals("Student")
        compose.onNodeWithTag("role_TUTOR").performScrollTo().performClick()
        compose.onNodeWithTag("role_title_TUTOR", useUnmergedTree = true).assertTextEquals("Tutor")
        compose.onNodeWithTag("login_username").performScrollTo()
        compose.onNodeWithText("Email or student ID").assertIsDisplayed()
    }
}

@Composable
private fun LoginTestContent(
    state: SessionUiState = SessionUiState(checking = false),
    language: String = "es",
    dark: Boolean = true,
    highContrast: Boolean = false,
    reducedMotion: Boolean = false,
    onLogin: (String, String, AppExperience) -> Unit = { _, _, _ -> },
) {
    val context = LocalContext.current
    val configuration = Configuration(LocalConfiguration.current).apply { setLocale(Locale.forLanguageTag(language)) }
    CompositionLocalProvider(LocalContext provides context.createConfigurationContext(configuration), LocalConfiguration provides configuration) {
        CompaneroTheme(darkTheme = dark, highContrast = highContrast) {
            ProvideCompaneroMotionPreferences(reducedMotion) {
                LoginScreen(state, onLogin, {})
            }
        }
    }
}
