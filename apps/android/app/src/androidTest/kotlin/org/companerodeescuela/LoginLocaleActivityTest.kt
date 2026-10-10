package org.companerodeescuela

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.companerodeescuela.feature.settings.AppLanguage
import org.companerodeescuela.feature.settings.AppThemeMode
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginLocaleActivityTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun languageAndThemePreferencesReachTheRealLoginActivity() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.appearancePreferences.setLanguage(AppLanguage.SPANISH)
                it.appearancePreferences.setThemeMode(AppThemeMode.DARK)
            }
            compose.waitForIdle()
            assertLoginVisible(device, By.textContains("universitaria,"), "locale-spanish")
            scenario.onActivity { it.appearancePreferences.setLanguage(AppLanguage.ENGLISH) }
            compose.waitForIdle()
            assertLoginVisible(device, By.textContains("university life,"), "locale-english")
            scenario.recreate()
            compose.waitForIdle()
            assertLoginVisible(device, By.textContains("university life,"), "locale-english")
            scenario.onActivity { it.appearancePreferences.setLanguage(AppLanguage.SPANISH) }
            compose.waitForIdle()
            assertLoginVisible(device, By.textContains("universitaria,"), "locale-restored-spanish")
        }
    }
}
