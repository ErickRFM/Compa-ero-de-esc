package org.companerodeescuela.core.designsystem.v8

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.companerodeescuela.core.designsystem.theme.LocalCompaneroHighContrast

/**
 * Shared V9 matte glass surface: dark matte tint, subtle depth and fine border.
 * Eliminates top white reflections and exaggerated glows while preserving legibility
 * and dark glass elegance over institutional campus imagery.
 */
@Composable
fun Modifier.v8GlassSurface(
    cornerRadius: Dp = V8RedDimensions.CardCorner,
    emphasized: Boolean = false,
    elevation: Dp = 6.dp,
    themeAware: Boolean = false,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    val highContrast = LocalCompaneroHighContrast.current
    val light = themeAware && MaterialTheme.colorScheme.background.luminance() > 0.5f
    val tint = if (light) {
        List(2) { MaterialTheme.colorScheme.surface.copy(alpha = if (highContrast) 1f else 0.96f) }
    } else if (highContrast) {
        listOf(Color(0xFF0F0E13), Color(0xFF0F0E13))
    } else {
        listOf(
            if (emphasized) Color(0xF2351720) else Color(0xF0211B22),
            Color(0xF218171E),
            Color(0xF51C1217),
        )
    }
    val edge = if (light) {
        List(2) { if (highContrast) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f) }
    } else if (highContrast) {
        listOf(Color.White, Color.White)
    } else if (emphasized) {
        listOf(Color(0x80FF5365), Color(0x50C74959), Color(0x30383949))
    } else {
        listOf(Color(0x35FF6B7A), Color(0x205D424A), Color(0x1A383949), Color(0x25C74959))
    }
    return this
        .shadow(
            elevation = if (highContrast) 0.dp else elevation,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = 0.35f),
            spotColor = Color.Black,
        )
        .clip(shape)
        .background(Brush.linearGradient(tint))
        .border(if (highContrast) 2.dp else 1.dp, Brush.linearGradient(edge), shape)
}

@Composable
fun V8FrostedGlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = V8RedDimensions.CardCorner,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    emphasized: Boolean = false,
    themeAware: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides if (themeAware) MaterialTheme.colorScheme.onSurface else V8RedColors.TextPrimary) {
        Box(
            modifier = modifier.v8GlassSurface(cornerRadius, emphasized, themeAware = themeAware).padding(contentPadding),
            content = content,
        )
    }
}
