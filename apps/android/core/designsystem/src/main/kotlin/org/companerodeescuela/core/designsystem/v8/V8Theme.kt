package org.companerodeescuela.core.designsystem.v8

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.companerodeescuela.core.designsystem.theme.CompaneroAccentPalette

/** Enables the V8 finish for shared surfaces inside the student scaffold. */
val LocalV8GlassEnabled = staticCompositionLocalOf { false }

/** Fixed Red Edition palette; font scale and motion preferences remain inherited. */
val V8ColorScheme = darkColorScheme(
    primary = V8RedColors.Crimson,
    onPrimary = V8RedColors.TextPrimary,
    primaryContainer = V8RedColors.DeepCrimson,
    onPrimaryContainer = V8RedColors.TextPrimary,
    background = V8RedColors.Background,
    onBackground = V8RedColors.TextPrimary,
    surface = V8RedColors.Surface,
    onSurface = V8RedColors.TextPrimary,
    surfaceContainer = V8RedColors.Surface,
    surfaceContainerLow = V8RedColors.Background,
    surfaceVariant = V8RedColors.Card,
    onSurfaceVariant = V8RedColors.TextSecondary,
    outline = V8RedColors.Outline,
    outlineVariant = V8RedColors.Outline.copy(alpha = 0.5f),
    error = V8RedColors.Error,
)

/** V14: high contrast must change actual screen surfaces, not only the outlines. */
fun v8EffectiveColorScheme(
    highContrast: Boolean,
    accentPalette: CompaneroAccentPalette = CompaneroAccentPalette.CRIMSON,
): ColorScheme {
    val accentedBase =
        if (accentPalette == CompaneroAccentPalette.CRIMSON) {
            V8ColorScheme
        } else {
            V8ColorScheme.copy(
                primary = accentPalette.darkPrimary,
                primaryContainer = accentPalette.darkContainer,
            )
        }
    return if (!highContrast) {
        accentedBase
    } else {
        accentedBase.copy(
            background = Color.Black,
            onBackground = Color.White,
            surface = Color(0xFF0F0E13),
            onSurface = Color.White,
            surfaceContainerLow = Color.Black,
            surfaceContainer = Color(0xFF0F0E13),
            surfaceVariant = Color(0xFF18171E),
            onSurfaceVariant = Color.White,
            outline = Color.White,
            outlineVariant = Color(0xFFEEEEEE),
        )
    }
}

/**
 * The V8 light edition is intentionally neutral, not the dim gray/pink surfaces
 * of a dynamic wallpaper palette. Keep semantic error/secondary colors from the
 * parent while enforcing readable ink on white cards and across Material surfaces.
 */
fun v8ResolvedColorScheme(
    parent: ColorScheme,
    highContrast: Boolean,
    accentPalette: CompaneroAccentPalette = CompaneroAccentPalette.CRIMSON,
    preserveDynamicPrimary: Boolean = false,
): ColorScheme =
    if (parent.background.luminance() > 0.5f) {
        val resolvedPrimary = if (preserveDynamicPrimary) parent.primary else accentPalette.lightPrimary
        val resolvedContainer = if (preserveDynamicPrimary) parent.primaryContainer else accentPalette.lightContainer
        parent.copy(
            primary = resolvedPrimary,
            onPrimary = Color.White,
            primaryContainer = resolvedContainer,
            onPrimaryContainer = resolvedPrimary,
            background = Color.White,
            onBackground = Color(0xFF141720),
            surface = Color.White,
            onSurface = Color(0xFF141720),
            surfaceVariant = Color(0xFFF2F4F7),
            onSurfaceVariant = if (highContrast) Color.Black else Color(0xFF373E4B),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFFAFBFC),
            surfaceContainer = Color(0xFFF5F6F8),
            surfaceContainerHigh = Color(0xFFEFF1F4),
            surfaceContainerHighest = Color(0xFFE8EBF0),
            outline = if (highContrast) Color.Black else Color(0xFF727985),
            outlineVariant = if (highContrast) Color.Black else Color(0xFFC9CDD5),
        )
    } else {
        val darkResolved = v8EffectiveColorScheme(highContrast, accentPalette)
        if (preserveDynamicPrimary) {
            darkResolved.copy(
                primary = parent.primary,
                primaryContainer = parent.primaryContainer,
            )
        } else {
            darkResolved
        }
    }
