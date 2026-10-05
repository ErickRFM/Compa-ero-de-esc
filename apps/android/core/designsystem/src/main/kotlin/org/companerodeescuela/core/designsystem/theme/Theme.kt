package org.companerodeescuela.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val LightColors = lightColorScheme(
    primary = CompanionColors.crimson,
    onPrimary = Color.White,
    primaryContainer = CompanionColors.crimsonContainer,
    onPrimaryContainer = CompanionColors.crimson,
    secondary = CompanionColors.semanticBlue,
    onSecondary = Color.White,
    secondaryContainer = CompanionColors.semanticBlueContainer,
    onSecondaryContainer = CompanionColors.semanticBlue,
    tertiary = CompanionColors.semanticAmber,
    onTertiary = Color.White,
    tertiaryContainer = CompanionColors.semanticAmberContainer,
    onTertiaryContainer = CompanionColors.semanticAmber,
    error = CompanionColors.semanticRed,
    onError = Color.White,
    errorContainer = CompanionColors.semanticRedContainer,
    onErrorContainer = CompanionColors.semanticRed,
    background = CompanionColors.warmBackground,
    onBackground = CompanionColors.onLightSurface,
    surface = CompanionColors.warmSurface,
    onSurface = CompanionColors.onLightSurface,
    surfaceVariant = CompanionColors.warmSurfaceVariant,
    onSurfaceVariant = CompanionColors.onLightSurfaceVariant,
    outline = CompanionColors.lightOutline,
)

private val DarkColors = darkColorScheme(
    primary = CompanionColors.crimsonContainer,
    onPrimary = CompanionColors.crimson,
    primaryContainer = CompanionColors.crimson,
    onPrimaryContainer = CompanionColors.crimsonSubtle,
    secondary = CompanionColors.semanticBlueContainer,
    onSecondary = CompanionColors.semanticBlue,
    secondaryContainer = CompanionColors.semanticBlue,
    onSecondaryContainer = CompanionColors.semanticBlueContainer,
    tertiary = CompanionColors.semanticAmberContainer,
    onTertiary = CompanionColors.semanticAmber,
    tertiaryContainer = CompanionColors.semanticAmber,
    onTertiaryContainer = CompanionColors.semanticAmberContainer,
    error = CompanionColors.semanticRedContainer,
    onError = CompanionColors.semanticRed,
    errorContainer = CompanionColors.semanticRed,
    onErrorContainer = CompanionColors.semanticRedContainer,
    background = CompanionColors.darkBackground,
    onBackground = CompanionColors.onDarkSurface,
    surface = CompanionColors.darkSurface,
    onSurface = CompanionColors.onDarkSurface,
    surfaceContainerLowest = CompanionColors.darkBackground,
    surfaceContainerLow = CompanionColors.darkInset,
    surfaceContainer = CompanionColors.darkRaised,
    surfaceContainerHigh = CompanionColors.darkSurfaceVariant,
    surfaceContainerHighest = CompanionColors.darkFloating,
    surfaceVariant = CompanionColors.darkSurfaceVariant,
    onSurfaceVariant = CompanionColors.onDarkSurfaceVariant,
    outline = CompanionColors.darkOutline,
    outlineVariant = CompanionColors.darkSurfaceVariant,
)

/**
 * App theme with product-level accessibility preferences.
 *
 * Android's system font scale remains authoritative; [fontScaleMultiplier]
 * adjusts relative size without changing dp touch targets.
 */
@Composable
fun CompaneroTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    fontScaleMultiplier: Float = 1f,
    highContrast: Boolean = false,
    content: @Composable () -> Unit,
) {
    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    val colorScheme = if (highContrast) {
        baseScheme.copy(
            outline = baseScheme.onSurfaceVariant,
            outlineVariant = baseScheme.onSurface.copy(alpha = 0.35f),
        )
    } else {
        baseScheme
    }

    val density = LocalDensity.current
    val adjustedDensity = Density(
        density = density.density,
        fontScale = density.fontScale * fontScaleMultiplier.coerceIn(0.9f, 1.2f),
    )

    CompositionLocalProvider(LocalDensity provides adjustedDensity) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CompanionTypography,
            shapes = CompanionShapes,
            content = content,
        )
    }
}
