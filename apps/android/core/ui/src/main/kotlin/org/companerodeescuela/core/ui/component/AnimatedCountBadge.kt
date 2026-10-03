package org.companerodeescuela.core.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.companerodeescuela.core.motion.CompaneroMotionDuration
import org.companerodeescuela.core.motion.LocalCompaneroMotionPreferences

@Composable
fun AnimatedCountBadge(
    count: Int,
    modifier: Modifier = Modifier,
) {
    val safeCount = count.coerceAtLeast(0)
    val reducedMotion = LocalCompaneroMotionPreferences.current.reducedMotion
    val duration = if (reducedMotion) 0 else CompaneroMotionDuration.FAST

    AnimatedContent(
        targetState = safeCount,
        modifier = modifier,
        transitionSpec = {
            (fadeIn(tween(duration)) + scaleIn(tween(duration)))
                .togetherWith(fadeOut(tween(duration)) + scaleOut(tween(duration)))
                .using(SizeTransform(clip = false))
        },
        label = "animatedCountBadge",
    ) { value ->
        if (value > 0) {
            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.error)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (value > 99) "99+" else value.toString(),
                    color = MaterialTheme.colorScheme.onError,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
