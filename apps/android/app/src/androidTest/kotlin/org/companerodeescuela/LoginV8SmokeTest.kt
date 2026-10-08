package org.companerodeescuela

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginV8SmokeTest {
    @Test
    fun signedOutUserSeesInstitutionalLogin() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

            assertTrue(device.wait(Until.hasObject(By.textContains("Compañero")), 10_000))
            assertTrue(device.hasObject(By.textContains("universitaria,")))
            assertTrue(device.hasObject(By.text("Correo electrónico o matrícula")))
        }
    }

    @Test
    fun firstAccessOpensInstitutionalIdentityValidation() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

            assertTrue(device.wait(Until.hasObject(By.textContains("universitaria,")), 10_000))
            if (!device.hasObject(By.text("Crear cuenta"))) {
                UiScrollable(UiSelector().scrollable(true)).scrollTextIntoView("Crear cuenta")
            }
            val activate = device.findObject(By.text("Crear cuenta"))
            assertTrue(activate != null)
            activate.click()

            device.waitForIdle()
            assertTrue(device.wait(Until.hasObject(By.text("Nombre")), 5_000))
            assertTrue(device.hasObject(By.text("Crear cuenta")))
        }
    }
}
