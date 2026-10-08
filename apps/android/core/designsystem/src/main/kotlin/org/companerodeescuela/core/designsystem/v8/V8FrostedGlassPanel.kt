package org.companerodeescuela.core.designsystem.v8

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Dark frosted-glass panel for high-detail campus photography.
 *
 * Android API 30 does not support native RenderEffect backdrop blur. This
 * cross-version treatment uses a layered, deliberately high-contrast translucent
 * scrim, subtle reflection, and a fine edge light. It does not pretend to blur
 * the content behind the panel; avoid transparent text fields on busy photos.
 */
@Composable
fun V8FrostedGlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier = modifier
            .shadow(
                elevation = 18.dp,
                shape = shape,
                ambientColor = V8RedColors.DeepCrimson.copy(alpha = 0.36f),
                spotColor = Color.Black,
            )
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xEE30171F),
                        Color(0xF015171D),
                        Color(0xF21B0B14),
                    ),
                ),
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xA7FFD9DF),
                        V8RedColors.Crimson.copy(alpha = 0.64f),
                        Color(0x66383949),
                        Color(0x75FF5365),
                    ),
                ),
                shape = shape,
            ),
    ) {
        // Reflected light on the glass is independent of the contents.
        Box(
            modifier = Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0.0f to Color.White.copy(alpha = 0.075f),
                    0.30f to Color.White.copy(alpha = 0.012f),
                    0.72f to Color.Transparent,
                    1.0f to V8RedColors.DeepCrimson.copy(alpha = 0.10f),
                ),
            ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        Color.Transparent,
                        Color(0xAAFFFFFF),
                        V8RedColors.Crimson.copy(alpha = 0.48f),
                        Color.Transparent,
                    ),
                ),
        )
        content()
    }
}
