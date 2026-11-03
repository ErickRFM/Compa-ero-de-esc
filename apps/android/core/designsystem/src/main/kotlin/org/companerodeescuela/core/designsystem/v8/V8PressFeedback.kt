package org.companerodeescuela.core.designsystem.v8

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import org.companerodeescuela.core.motion.CompaneroMotionDuration
import org.companerodeescuela.core.motion.LocalCompaneroMotionPreferences

/** Visual feedback only: the original clickable owns gestures, cancellation and events. */
fun Modifier.v8PressFeedback(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
): Modifier =
    composed {
        val reduced = LocalCompaneroMotionPreferences.current.reducedMotion
        val pressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (enabled && pressed && !reduced) 0.985f else 1f,
            animationSpec = if (reduced) snap() else tween(CompaneroMotionDuration.FAST),
            label = "v8PressFeedback",
        )
        graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    }
