package org.companerodeescuela.core.designsystem.v8

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.staticCompositionLocalOf

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
fun v8EffectiveColorScheme(highContrast: Boolean): ColorScheme =
    if (!highContrast) V8ColorScheme else V8ColorScheme.copy(
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

/** Honor the user's existing light/system scheme; V8's matte brand palette is for dark mode. */
fun v8ResolvedColorScheme(parent: ColorScheme, highContrast: Boolean): ColorScheme =
    if (parent.background.luminance() > 0.5f) parent else v8EffectiveColorScheme(highContrast)
