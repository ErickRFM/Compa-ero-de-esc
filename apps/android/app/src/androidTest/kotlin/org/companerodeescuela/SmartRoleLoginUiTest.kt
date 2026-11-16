@file:Suppress("ktlint:standard:no-wildcard-imports")

package org.companerodeescuela

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.test.platform.app.InstrumentationRegistry
import org.companerodeescuela.core.designsystem.theme.CompaneroTheme
import org.companerodeescuela.core.motion.ProvideCompaneroMotionPreferences
import org.companerodeescuela.core.navigation.AppExperience
import org.companerodeescuela.feature.auth.LoginScreen
import org.companerodeescuela.feature.auth.RegistrationScreen
import org.companerodeescuela.feature.auth.SessionUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale

class SmartRoleLoginUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun registrationUsesReadableLightV8InsideALightAppTheme() {
        compose.setContent {
            CompaneroTheme(darkTheme = false) {
                RegistrationScreen(SessionUiState(checking = false), { _ -> }, {})
            }
        }
        // Registration shares the same theme-aware campus frame as login.
        val background = compose.onRoot().captureToImage().toPixelMap()[0, 0]
        org.junit.Assert.assertTrue("Registration background follows light theme", background.luminance() > 0.5f)
    }

    @Test fun roleChangesPreserveOneFormAndOnlySendDesiredAccess() {
        var submitted: Triple<String, String, AppExperience>? = null
        compose.setContent { loginTestContent(onLogin = { u, p, role -> submitted = Triple(u, p, role) }) }
        typeExactly("login_username", "qa.docente")
        typeExactly("login_password", "test-password")
        selectChipWithRetry("role_TUTOR")
        // The keyboard may obscure the card; assert selected access and the retained shared form.
        compose.onNodeWithTag("role_TUTOR").assertIsSelected()
        compose.onNodeWithTag("login_registration").assertDoesNotExist()
        // The pager locks submission while the role transition is in progress.
        // API 35 can deliver the click before the carousel finishes settling.
        compose.waitUntil(timeoutMillis = 8_000) {
            val submitNodes = compose.onAllNodesWithTag("login_submit").fetchSemanticsNodes()
            submitNodes.firstOrNull()?.config?.contains(SemanticsProperties.Disabled) == false
        }
        compose.onNodeWithTag("login_submit").assertIsEnabled()
        compose.onNodeWithTag("login_submit").performScrollTo()
        // LoginScreen.submit() silently ignores a tap that lands while the pager snap is still
        // settling. Re-click only while no submission was observed at all.
        repeat(3) {
            if (submitted == null) {
                compose.onNodeWithTag("login_submit").performClick()
                runCatching { compose.waitUntil(4_000) { submitted != null } }
            }
        }
        assertNotNull(submitted)
        assertEquals(Triple("qa.docente", "test-password", AppExperience.TUTOR), submitted)
    }

    // API 35 IME batching flushes stray composing characters into the field after typing
    // (observed as "test-passworde" and as a "Conrtase" tail). Verify the exact editable value
    // and retype until it matches; the final Triple assertion stays the hard guard.
    // Password fields expose the visual transformation's bullets through semantics, so an
    // all-bullet run of the exact expected length also counts as a correct value.
    private fun typeExactly(
        tag: String,
        value: String,
    ) {
        val node = compose.onNodeWithTag(tag).performScrollTo()
        var observed = ""
        repeat(3) {
            node.performTextClearance()
            node.performTextInput(value)
            compose.waitForIdle()
            val config = node.fetchSemanticsNode().config
            observed = if (config.contains(SemanticsProperties.EditableText)) config[SemanticsProperties.EditableText].text else value
            if (observed == value || (value.none { it == '\u2022' } && observed.all { it == '\u2022' } && observed.length == value.length)) return
        }
        throw AssertionError("Field $tag kept value \"$observed\" instead of \"$value\" after three typing attempts")
    }

    // The pager can swallow a chip tap mid-snap; re-click only while the chip never selected.
    private fun selectChipWithRetry(tag: String) {
        val chip = compose.onNodeWithTag(tag).performScrollTo()
        repeat(3) {
            chip.performClick()
            val selected =
                runCatching {
                    compose.waitUntil(4_000) {
                        val config = chip.fetchSemanticsNode().config
                        config.contains(SemanticsProperties.Selected) && config[SemanticsProperties.Selected]
                    }
                    true
                }.getOrDefault(false)
            if (selected) return
        }
        chip.assertIsSelected()
    }

    @Test fun swipeArrowAndIndicatorSelectAllSixAccesses() {
        compose.setContent { loginTestContent() }
        compose.onNodeWithTag("role_pager").performScrollTo().performTouchInput { swipeLeft() }
        compose.onNodeWithTag("role_title_TEACHER", useUnmergedTree = true).assertIsDisplayed()
        captureAccess("teacher")
        compose.onNodeWithTag("access_next").performScrollTo().performClick()
        compose.onNodeWithTag("role_pager").performScrollTo()
        compose.onNodeWithTag("role_title_TUTOR", useUnmergedTree = true).assertIsDisplayed()
        captureAccess("tutor")
        selectAccessAndAwait(AppExperience.GUEST)
        compose.onNodeWithTag("login_registration").assertDoesNotExist()
        captureAccess("guest")
        selectAccessAndAwait(AppExperience.ADMIN)
        captureAccess("admin")
        selectAccessAndAwait(AppExperience.SUPER_ADMIN)
        captureAccess("super_admin")
        compose.onNodeWithTag("access_next").assertIsNotEnabled()
        selectAccessAndAwait(AppExperience.STUDENT)
        captureAccess("student")
        compose.onNodeWithTag("access_previous").assertIsNotEnabled()
    }

    // Chip taps schedule a suspend scroll; Compose can observe the old page for a frame.
    // Await both selected semantics and visible pager content for every direct access.
    private fun selectAccessAndAwait(access: AppExperience) {
        val chipTag = "role_" + access.name
        val titleTag = "role_title_" + access.name
        compose.onNodeWithTag(chipTag).performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 8_000) {
            val selected =
                compose.onAllNodesWithTag(chipTag).fetchSemanticsNodes().firstOrNull()?.config?.let { semantics ->
                    semantics.contains(SemanticsProperties.Selected) && semantics[SemanticsProperties.Selected]
                } == true
            selected &&
                runCatching {
                    compose.onNodeWithTag(titleTag, useUnmergedTree = true).assertIsDisplayed()
                    true
                }.getOrDefault(false)
        }
        compose.onNodeWithTag(chipTag).assertIsSelected()
        compose.onNodeWithTag(titleTag, useUnmergedTree = true).assertIsDisplayed()
    }

    private fun captureAccess(name: String) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "v10-evidence")
        directory.mkdirs()
        File(directory, "access-" + name + ".png").outputStream().use {
            val screenshot = compose.onRoot().captureToImage().asAndroidBitmap()
            org.junit.Assert.assertTrue(
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, it),
            )
        }
    }

    @Test fun rotationRestoresIdentifierAndAccessWithoutSavingPassword() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { loginTestContent() }
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
        compose.setContent { loginTestContent(state = state.value) }
        compose.onNodeWithTag("login_username").performScrollTo().performTextInput("qa.alumno")
        compose.onNodeWithTag("login_password").performScrollTo().performTextInput("test-password")
        compose.runOnIdle { state.value = state.value.copy(submitting = true) }
        compose.onNodeWithTag("login_submit").assertIsNotEnabled()
        compose.onNodeWithTag("login_username").assertIsNotEnabled()
        compose.onNodeWithTag("role_TUTOR").performScrollTo().assertIsNotEnabled()
    }

    @Test fun englishHighContrastAndReducedMotionUseExistingPreferences() {
        compose.setContent { loginTestContent(language = "en", highContrast = true, reducedMotion = true, dark = false) }
        compose.onNodeWithTag("role_title_STUDENT", useUnmergedTree = true).assertTextEquals("Student")
        compose.onNodeWithTag("role_TUTOR").performScrollTo().performClick()
        compose.onNodeWithTag("role_title_TUTOR", useUnmergedTree = true).assertTextEquals("Tutor")
        compose.onNodeWithTag("login_username").performScrollTo()
        compose.onNodeWithText("Email or student ID").assertIsDisplayed()
    }
}

@Composable
private fun loginTestContent(
    state: SessionUiState = SessionUiState(checking = false),
    language: String = "es",
    dark: Boolean = true,
    highContrast: Boolean = false,
    reducedMotion: Boolean = false,
    onLogin: (String, String, AppExperience) -> Unit = { _, _, _ -> },
) {
    val context = LocalContext.current
    val configuration = Configuration(LocalConfiguration.current).apply { setLocale(Locale.forLanguageTag(language)) }
    CompositionLocalProvider(
        LocalContext provides context.createConfigurationContext(configuration),
        LocalConfiguration provides configuration,
    ) {
        CompaneroTheme(darkTheme = dark, highContrast = highContrast) {
            ProvideCompaneroMotionPreferences(reducedMotion) {
                LoginScreen(state, onLogin, {})
            }
        }
    }
}
