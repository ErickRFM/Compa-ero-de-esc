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
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginLocaleActivityTest {
    @Test fun languageAndThemePreferencesReachTheRealLoginActivity() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.appearancePreferences.setLanguage(AppLanguage.SPANISH)
                it.appearancePreferences.setThemeMode(AppThemeMode.DARK)
            }
            assertTrue(device.wait(Until.hasObject(By.textContains("universitaria,")), 10_000))
            scenario.onActivity { it.appearancePreferences.setLanguage(AppLanguage.ENGLISH) }
            assertTrue(device.wait(Until.hasObject(By.textContains("university life,")), 10_000))
            scenario.recreate()
            assertTrue(device.wait(Until.hasObject(By.textContains("university life,")), 10_000))
            scenario.onActivity { it.appearancePreferences.setLanguage(AppLanguage.SPANISH) }
            assertTrue(device.wait(Until.hasObject(By.textContains("universitaria,")), 10_000))
        }
    }
}
