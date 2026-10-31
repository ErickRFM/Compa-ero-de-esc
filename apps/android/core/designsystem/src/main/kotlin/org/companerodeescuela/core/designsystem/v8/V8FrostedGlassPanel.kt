@file:Suppress("ktlint:standard:function-naming")

package org.companerodeescuela.core.designsystem.v8

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.companerodeescuela.core.designsystem.theme.CompaneroAccentPalette
import org.companerodeescuela.core.designsystem.theme.LocalCompaneroAccentPalette
import org.companerodeescuela.core.designsystem.theme.LocalCompaneroHighContrast

/**
 * One shared V9 surface with three deliberately different visual treatments:
 * normal dark is deep matte (no reflected light), dark high contrast has
 * visible top reflections with an opaque black foundation, and light is clean
 * white with ink text. High-contrast light remains opaque and sharply outlined.
 */
internal enum class V8SurfaceFinish {
    DARK_MATTE,
    DARK_HIGH_CONTRAST,
    LIGHT_CLEAN,
    LIGHT_HIGH_CONTRAST,
}

internal fun v8SurfaceFinish(
    light: Boolean,
    highContrast: Boolean,
): V8SurfaceFinish =
    when {
        light && highContrast -> V8SurfaceFinish.LIGHT_HIGH_CONTRAST
        light -> V8SurfaceFinish.LIGHT_CLEAN
        highContrast -> V8SurfaceFinish.DARK_HIGH_CONTRAST
        else -> V8SurfaceFinish.DARK_MATTE
    }

@Composable
fun Modifier.v8GlassSurface(
    cornerRadius: Dp = V8RedDimensions.CardCorner,
    emphasized: Boolean = false,
    elevation: Dp = 6.dp,
    themeAware: Boolean = true,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    val light = themeAware && MaterialTheme.colorScheme.background.luminance() > 0.5f
    val finish = v8SurfaceFinish(light, LocalCompaneroHighContrast.current)
    val scheme = MaterialTheme.colorScheme
    val palette = LocalCompaneroAccentPalette.current

    val tint =
        when (finish) {
            V8SurfaceFinish.LIGHT_CLEAN ->
                if (emphasized && palette != CompaneroAccentPalette.CRIMSON) {
                    listOf(Color.White, palette.lightContainer.copy(alpha = 0.35f))
                } else {
                    listOf(Color.White, Color(0xFFFCFCFE))
                }
            V8SurfaceFinish.LIGHT_HIGH_CONTRAST ->
                listOf(Color.White, Color.White)
            V8SurfaceFinish.DARK_MATTE ->
                listOf(
                    when {
                        !emphasized -> Color(0xF816171D)
                        palette == CompaneroAccentPalette.CRIMSON -> Color(0xF8241B24)
                        else -> palette.darkContainer.copy(alpha = 0.28f)
                    },
                    Color(0xFA101116),
                )
            V8SurfaceFinish.DARK_HIGH_CONTRAST ->
                listOf(
                    Color(0xFF393D48),
                    Color(0xFF22242E),
                    Color(0xFF101116),
                )
        }

    val edge =
        when (finish) {
            V8SurfaceFinish.LIGHT_CLEAN ->
                if (emphasized) {
                    listOf(scheme.primary.copy(alpha = 0.45f), Color(0xFFE0E3E9))
                } else {
                    listOf(Color(0xFFD1D5DD), Color(0xFFE0E3E9))
                }
            V8SurfaceFinish.LIGHT_HIGH_CONTRAST ->
                listOf(scheme.onSurface, scheme.onSurface)
            V8SurfaceFinish.DARK_MATTE ->
                listOf(
                    when {
                        !emphasized -> Color(0x4D555966)
                        palette == CompaneroAccentPalette.CRIMSON -> Color(0x665E3943)
                        else -> scheme.primary.copy(alpha = 0.45f)
                    },
                    Color(0x33373A44),
                )
            V8SurfaceFinish.DARK_HIGH_CONTRAST ->
                listOf(Color(0xE6FFFFFF), Color(0x73FFFFFF), Color(0xB3FFFFFF))
        }

    val shadowElevation = if (finish == V8SurfaceFinish.DARK_MATTE) elevation.coerceAtMost(4.dp) else 0.dp
    val borderWidth =
        if (finish == V8SurfaceFinish.DARK_HIGH_CONTRAST || finish == V8SurfaceFinish.LIGHT_HIGH_CONTRAST) 2.dp else 1.dp

    val shaded =
        this.shadow(
            elevation = shadowElevation,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = 0.25f),
            spotColor = Color.Black,
        )
    val clipped = shaded.clip(shape)
    val painted = clipped.background(Brush.verticalGradient(tint))
    return painted.border(borderWidth, Brush.verticalGradient(edge), shape)
}

@Composable
fun V8FrostedGlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = V8RedDimensions.CardCorner,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    emphasized: Boolean = false,
    themeAware: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    CompositionLocalProvider(
        LocalContentColor provides if (themeAware) MaterialTheme.colorScheme.onSurface else V8RedColors.TextPrimary,
    ) {
        Box(
            modifier = modifier.v8GlassSurface(cornerRadius, emphasized, themeAware = themeAware).padding(contentPadding),
            content = content,
        )
    }
}
