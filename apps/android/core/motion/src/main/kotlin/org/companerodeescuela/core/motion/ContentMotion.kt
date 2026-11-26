@file:Suppress("ktlint:standard:function-naming")

package org.companerodeescuela.core.motion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

@Composable
fun CompaneroExpandableVisibility(
    visible: Boolean,
    exitDurationMillis: Int = CompaneroMotionDuration.STANDARD,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val reducedMotion = LocalCompaneroMotionPreferences.current.reducedMotion
    AnimatedVisibility(
        visible = visible,
        enter =
            if (reducedMotion) {
                EnterTransition.None
            } else {
                fadeIn(CompaneroMotion.standard()) + expandVertically(CompaneroMotion.standard())
            },
        exit =
            if (reducedMotion) {
                ExitTransition.None
            } else {
                fadeOut(tween(exitDurationMillis)) + shrinkVertically(tween(exitDurationMillis))
            },
        content = content,
    )
}

@Composable
fun animateCompaneroProgressAsState(
    targetValue: Float,
    label: String,
): State<Float> =
    animateFloatAsState(
        targetValue = targetValue,
        animationSpec = if (LocalCompaneroMotionPreferences.current.reducedMotion) snap() else CompaneroMotion.standard(),
        label = label,
    )
