package org.companerodeescuela.core.designsystem.v8

import androidx.compose.material3.darkColorScheme

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
