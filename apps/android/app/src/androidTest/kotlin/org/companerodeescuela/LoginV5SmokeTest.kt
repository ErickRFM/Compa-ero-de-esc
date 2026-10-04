package org.companerodeescuela

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginV5SmokeTest {
    @Test
    fun signedOutUserSeesInstitutionalLogin() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

            assertTrue(device.hasObject(By.text("Compañero de Clase")))
            assertTrue(device.hasObject(By.text("Acceso institucional")))
        }
    }

    @Test
    fun firstAccessOpensInstitutionalIdentityValidation() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

            val activate = device.findObject(By.text("Activar acceso"))
            assertTrue(activate != null)
            activate.click()

            device.waitForIdle()
            assertTrue(device.hasObject(By.text("Activar acceso")))
            assertTrue(device.hasObject(By.text("Identidad institucional")))
            assertTrue(device.hasObject(By.text("Matrícula o ID institucional")))
        }
    }
}