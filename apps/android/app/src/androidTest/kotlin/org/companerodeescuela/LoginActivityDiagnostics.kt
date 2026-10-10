package org.companerodeescuela

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.assertTrue

/** Failures retain the actual controlled test screen and accessibility tree; no test retries. */
internal fun assertLoginVisible(device: UiDevice, selector: BySelector, label: String) {
    val visible = device.wait(Until.hasObject(selector), 10_000)
    if (!visible) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "v10-evidence")
            .also { it.mkdirs() }
        device.dumpWindowHierarchy(File(directory, "failure-$label.xml"))
        device.takeScreenshot(File(directory, "failure-$label.png"))
    }
    assertTrue("Actual login node missing: $selector; failure screen/hierarchy retained", visible)
}
