package org.companerodeescuela.core.motion

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class CompaneroMotionPolicyTest {

    @Test
    fun `duration scale remains ordered from fast to hero`() {
        assertTrue(CompaneroMotionDuration.FAST < CompaneroMotionDuration.STANDARD)
        assertTrue(CompaneroMotionDuration.STANDARD < CompaneroMotionDuration.EMPHASIZED)
        assertTrue(CompaneroMotionDuration.EMPHASIZED < CompaneroMotionDuration.HERO)
    }

    @Test
    fun `reduced motion removes spatial and particle effects`() {
        val policy = CompaneroMotionPolicy.resolve(
            role = MotionRole.SUCCESS,
            reducedMotion = true,
        )

        assertEquals(CompaneroMotionDuration.FAST, policy.durationMillis)
        assertFalse(policy.allowSpatialMotion)
        assertFalse(policy.allowParticles)
    }

    @Test
    fun `success motion may use one shot particles when motion is enabled`() {
        val policy = CompaneroMotionPolicy.resolve(
            role = MotionRole.SUCCESS,
            reducedMotion = false,
        )

        assertTrue(policy.allowSpatialMotion)
        assertTrue(policy.allowParticles)
    }
}
