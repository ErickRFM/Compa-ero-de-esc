package org.companerodeescuela

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.companerodeescuela.feature.settings.AppLanguage
import org.companerodeescuela.feature.settings.AppThemeMode
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Captures the actual signed-out Activity, not a mocked composition or flattened image. */
@RunWith(AndroidJUnit4::class)
class LoginVisualEvidenceTest {
    @Test fun actualLoginFitsPhoneAndTabletViewports() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val directory = File(instrumentation.targetContext.filesDir, "v10-evidence")
        directory.mkdirs()
        val originalScale = device.executeShellCommand("settings get system font_scale").trim()
        try {
            device.executeShellCommand("wm density 320")
            listOf(360 to 800, 390 to 844, 430 to 932, 768 to 1024, 1024 to 1366).forEach { (width, height) ->
                device.executeShellCommand("wm size " + width * 2 + "x" + height * 2)
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    scenario.onActivity {
                        it.appearancePreferences.setLanguage(AppLanguage.SPANISH)
                        it.appearancePreferences.setThemeMode(AppThemeMode.DARK)
                        it.appearancePreferences.setHighContrast(false)
                    }
                    assertTrue(device.wait(Until.hasObject(By.textContains("universitaria,")), 10_000))
                    device.waitForIdle()
                    assertTrue(device.takeScreenshot(File(directory, "dark-" + width + "-top.png")))
                    UiScrollable(UiSelector().packageName("org.companerodeescuela").scrollable(true)).setAsVerticalList().scrollToEnd(8)
                    device.waitForIdle()
                    assertTrue(device.hasObject(By.text("Correo electrónico o matrícula")))
                    assertTrue(device.takeScreenshot(File(directory, "dark-" + width + "-form.png")))
                }
            }
            device.executeShellCommand("wm size 780x1688")
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity {
                    it.appearancePreferences.setThemeMode(AppThemeMode.LIGHT)
                    it.appearancePreferences.setHighContrast(true)
                    it.appearancePreferences.setLanguage(AppLanguage.ENGLISH)
                }
                assertTrue(device.wait(Until.hasObject(By.textContains("university life,")), 10_000))
                device.waitForIdle()
                assertTrue(device.takeScreenshot(File(directory, "light-contrast-390-top.png")))
                UiScrollable(UiSelector().packageName("org.companerodeescuela").scrollable(true)).setAsVerticalList().scrollToEnd(8)
                device.waitForIdle()
                assertTrue(device.takeScreenshot(File(directory, "light-contrast-390-form.png")))
                scenario.onActivity {
                    it.appearancePreferences.setLanguage(AppLanguage.SPANISH)
                    it.appearancePreferences.setThemeMode(AppThemeMode.DARK)
                    it.appearancePreferences.setTextScale(1.2f)
                    it.appearancePreferences.setReducedMotion(true)
                }
                device.executeShellCommand("settings put system font_scale 1.6")
                scenario.recreate()
                device.waitForIdle()
                assertTrue(device.takeScreenshot(File(directory, "dark-contrast-large-text-390.png")))
                UiScrollable(UiSelector().packageName("org.companerodeescuela").scrollable(true)).setAsVerticalList().scrollToEnd(12)
                device.waitForIdle()
                assertTrue(device.takeScreenshot(File(directory, "dark-contrast-large-text-390-form.png")))
                scenario.onActivity {
                    it.appearancePreferences.setTextScale(1f)
                    it.appearancePreferences.setReducedMotion(false)
                    it.appearancePreferences.setHighContrast(false)
                }
            }
        } finally {
            device.executeShellCommand("settings put system font_scale " + if (originalScale == "null") "1.0" else originalScale)
            device.executeShellCommand("wm size reset")
            device.executeShellCommand("wm density reset")
        }
    }
}
