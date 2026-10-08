package org.companerodeescuela.core.designsystem.v8

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.companerodeescuela.core.designsystem.theme.LocalCompaneroHighContrast

/**
 * Shared finish based on the Login frosted panel: dark tint, reflected light and
 * a fine illuminated edge. Works on API 30 without blurring foreground text or
 * requiring RenderEffect. High contrast uses an opaque surface and a solid edge.
 */
@Composable
fun Modifier.v8GlassSurface(
    cornerRadius: Dp = V8RedDimensions.CardCorner,
    emphasized: Boolean = false,
    elevation: Dp = 8.dp,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    val highContrast = LocalCompaneroHighContrast.current
    val tint = if (highContrast) {
        listOf(V8RedColors.Surface, V8RedColors.Surface)
    } else {
        listOf(
            if (emphasized) Color(0xEE491520) else Color(0xEE30171F),
            Color(0xF015171D),
            Color(0xF21B0B14),
        )
    }
    val edge = if (highContrast) {
        listOf(V8RedColors.TextPrimary, V8RedColors.TextPrimary)
    } else if (emphasized) {
        listOf(Color(0xC7FFD9DF), V8RedColors.Crimson, Color(0x9EFF5365))
    } else {
        listOf(Color(0x83FFD9DF), Color(0x705D424A), Color(0x66383949), Color(0x75C74959))
    }
    return this
        .shadow(
            elevation = if (highContrast) 0.dp else elevation,
            shape = shape,
            ambientColor = V8RedColors.DeepCrimson.copy(alpha = 0.30f),
            spotColor = Color.Black,
        )
        .clip(shape)
        .background(Brush.linearGradient(tint))
        .drawWithCache {
            val reflection = Brush.verticalGradient(
                0.0f to Color.White.copy(alpha = 0.075f),
                0.30f to Color.White.copy(alpha = 0.012f),
                0.72f to Color.Transparent,
                1.0f to V8RedColors.DeepCrimson.copy(alpha = 0.10f),
            )
            val topLight = Brush.horizontalGradient(
                listOf(Color.Transparent, Color(0x75FFFFFF), V8RedColors.Crimson.copy(alpha = 0.30f), Color.Transparent),
            )
            onDrawBehind {
                if (!highContrast) {
                    drawRect(reflection)
                    val inset = cornerRadius.toPx().coerceAtMost(size.width / 2)
                    drawLine(topLight, Offset(inset, 0.5.dp.toPx()), Offset(size.width - inset, 0.5.dp.toPx()), 1.dp.toPx())
                }
            }
        }
        .border(if (highContrast) 2.dp else 1.dp, Brush.linearGradient(edge), shape)
}

@Composable
fun V8FrostedGlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = V8RedDimensions.CardCorner,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    emphasized: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.v8GlassSurface(cornerRadius, emphasized).padding(contentPadding),
        content = content,
    )
}
