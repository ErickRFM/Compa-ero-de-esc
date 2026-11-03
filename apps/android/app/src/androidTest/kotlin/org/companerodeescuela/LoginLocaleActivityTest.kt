package org.companerodeescuela

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.companerodeescuela.feature.settings.AppLanguage
import org.companerodeescuela.feature.settings.AppThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginLocaleActivityTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun languageAndThemePreferencesReachTheRealLoginActivity() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.appearancePreferences.setLanguage(AppLanguage.SPANISH)
                it.appearancePreferences.setThemeMode(AppThemeMode.DARK)
            }
            awaitHero("Bienvenido de nuevo")
            scenario.onActivity { it.appearancePreferences.setLanguage(AppLanguage.ENGLISH) }
            awaitHero("Welcome back")
            scenario.recreate()
            awaitHero("Welcome back")
            scenario.onActivity { it.appearancePreferences.setLanguage(AppLanguage.SPANISH) }
            awaitHero("Bienvenido de nuevo")
        }
    }

    private fun awaitHero(text: String) {
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(text, substring = true).assertExists()
    }
}
