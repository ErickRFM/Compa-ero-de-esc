package org.companerodeescuela.feature.coordinator

import org.companerodeescuela.core.common.result.AppError
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoordinatorOverviewPolicyTest {
    @Test
    fun `transient network and server failures may retain a previously authorized summary`() {
        assertTrue(retainCoordinationSnapshot(AppError.Network()))
        assertTrue(retainCoordinationSnapshot(AppError.Http(500)))
        assertTrue(retainCoordinationSnapshot(AppError.Http(503)))
        assertTrue(retainCoordinationSnapshot(AppError.Http(429)))
    }

    @Test
    fun `revoked session forbidden resources and invalid contracts clear institutional summary`() {
        assertFalse(retainCoordinationSnapshot(AppError.Http(401)))
        assertFalse(retainCoordinationSnapshot(AppError.Http(403)))
        assertFalse(retainCoordinationSnapshot(AppError.Http(404)))
        assertFalse(retainCoordinationSnapshot(AppError.Serialization()))
        assertFalse(retainCoordinationSnapshot(AppError.Unknown()))
    }
}
