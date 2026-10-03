package org.companerodeescuela.core.motion

enum class MotionRole {
    NAVIGATION,
    STATE_CHANGE,
    HERO,
    SUCCESS,
}

data class MotionPolicy(
    val durationMillis: Int,
    val allowSpatialMotion: Boolean,
    val allowParticles: Boolean,
)

object CompaneroMotionPolicy {
    fun resolve(
        role: MotionRole,
        reducedMotion: Boolean,
    ): MotionPolicy {
        if (reducedMotion) {
            return MotionPolicy(
                durationMillis = CompaneroMotionDuration.FAST,
                allowSpatialMotion = false,
                allowParticles = false,
            )
        }

        return when (role) {
            MotionRole.NAVIGATION -> MotionPolicy(
                durationMillis = CompaneroMotionDuration.STANDARD,
                allowSpatialMotion = true,
                allowParticles = false,
            )
            MotionRole.STATE_CHANGE -> MotionPolicy(
                durationMillis = CompaneroMotionDuration.STANDARD,
                allowSpatialMotion = true,
                allowParticles = false,
            )
            MotionRole.HERO -> MotionPolicy(
                durationMillis = CompaneroMotionDuration.HERO,
                allowSpatialMotion = true,
                allowParticles = false,
            )
            MotionRole.SUCCESS -> MotionPolicy(
                durationMillis = CompaneroMotionDuration.EMPHASIZED,
                allowSpatialMotion = true,
                allowParticles = true,
            )
        }
    }
}
