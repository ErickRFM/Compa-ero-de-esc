package org.companerodeescuela.core.motion

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

object CompaneroMotionDuration {
    const val FAST = 120
    const val STANDARD = 220
    const val EMPHASIZED = 320
    const val HERO = 450
}

object CompaneroMotion {
    fun <T> fast(): TweenSpec<T> = tween(
        durationMillis = CompaneroMotionDuration.FAST,
    )

    fun <T> standard(): TweenSpec<T> = tween(
        durationMillis = CompaneroMotionDuration.STANDARD,
    )

    fun <T> emphasized(): TweenSpec<T> = tween(
        durationMillis = CompaneroMotionDuration.EMPHASIZED,
    )

    fun <T> hero(): TweenSpec<T> = tween(
        durationMillis = CompaneroMotionDuration.HERO,
    )

    fun <T> snappySpring(): SpringSpec<T> = spring(
        dampingRatio = 0.88f,
        stiffness = Spring.StiffnessHigh,
    )

    fun <T> standardSpring(): SpringSpec<T> = spring(
        dampingRatio = 0.82f,
        stiffness = Spring.StiffnessMedium,
    )

    fun <T> softSpring(): SpringSpec<T> = spring(
        dampingRatio = 0.9f,
        stiffness = Spring.StiffnessLow,
    )

    fun <T> successSpring(): SpringSpec<T> = spring(
        dampingRatio = 0.64f,
        stiffness = Spring.StiffnessMedium,
    )
}
