package org.companerodeescuela

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import org.junit.Assert.assertTrue
import org.companerodeescuela.feature.settings.AppLanguage
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginV8SmokeTest {
    @Test
    fun registrationAccountTypeCanBeSelectedByItsLabel() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.appearancePreferences.setLanguage(AppLanguage.SPANISH) }
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            assertTrue(device.wait(Until.hasObject(By.textContains("universitaria,")), 10_000))
            findVisible(device, By.text("Crear cuenta")).click()
            findVisible(device, By.text("Alumno"))
            findVisible(device, By.text("Docente")).click()
            findVisible(device, By.textContains("requieren verificación"))
            findVisible(device, By.text("Ya tengo cuenta")).click()
            findVisible(device, By.textContains("universitaria,"), forward = false)
        }
    }

    @Test
    fun signedOutUserSeesInstitutionalLogin() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.appearancePreferences.setLanguage(AppLanguage.SPANISH) }
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

            assertTrue(device.wait(Until.hasObject(By.textContains("Compañero")), 10_000))
            assertTrue(device.hasObject(By.textContains("universitaria,")))
            findVisible(device, By.text("Correo electrónico o matrícula"))
        }
    }

    @Test
    fun firstAccessOpensInstitutionalIdentityValidation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.appearancePreferences.setLanguage(AppLanguage.SPANISH) }
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

            assertTrue(device.wait(Until.hasObject(By.textContains("universitaria,")), 10_000))
            findVisible(device, By.text("Crear cuenta")).click()

            device.waitForIdle()
            findVisible(device, By.text("Nombre"))
            findVisible(device, By.text("Crear cuenta"), forward = false)
        }
    }

    private fun findVisible(device: UiDevice, selector: BySelector, forward: Boolean = true): UiObject2 {
        val scroll = UiScrollable(UiSelector().packageName("org.companerodeescuela").scrollable(true))
            .setAsVerticalList()
        repeat(6) {
            device.waitForIdle()
            device.wait(Until.findObject(selector), 2_000)?.let { return it }
            if (forward) scroll.scrollForward() else scroll.scrollBackward()
        }
        throw AssertionError("Control did not become visible after scrolling: $selector")
    }
}
