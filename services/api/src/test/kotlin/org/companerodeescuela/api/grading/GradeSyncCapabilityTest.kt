package org.companerodeescuela.api.grading

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.companerodeescuela.shared.contracts.GradeSyncResult
import org.companerodeescuela.shared.contracts.GradeSyncStatus

class GradeSyncCapabilityTest {
    @Test fun `placeholder provider must report unavailable`() {
        assertFalse(UnavailableGradeSyncGateway.isConfiguredForPublishing())
    }

    @Test fun `registered provider can advertise readiness`() {
        val gateway = GradeSyncGateway { _, _ -> GradeSyncResult(GradeSyncStatus.SYNCED) }
        assertTrue(gateway.isConfiguredForPublishing())
    }
}
