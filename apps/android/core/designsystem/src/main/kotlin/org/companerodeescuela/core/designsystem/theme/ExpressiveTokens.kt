package org.companerodeescuela.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

/**
 * Public presentation tokens for feature modules.
 *
 * Features consume semantic roles instead of inventing raw dimensions or
 * gradient colors locally. This keeps the expressive layer replaceable.
 */
object CompaneroSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object CompaneroElevation {
    val raised = 8.dp
    val immersive = 12.dp
}

object CompaneroExpressive {
    @Composable
    fun heroBrush(): Brush = Brush.linearGradient(
        colors = listOf(
            CompanionColors.graphite,
            CompanionColors.crimson,
        ),
    )

    @Composable
    fun accentBrush(): Brush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.tertiary,
        ),
    )

    @Composable
    fun subduedHeroBrush(): Brush = Brush.linearGradient(
        colors = listOf(
            CompanionColors.graphite,
            CompanionColors.graphiteRaised,
            CompanionColors.crimson,
        ),
    )
}
