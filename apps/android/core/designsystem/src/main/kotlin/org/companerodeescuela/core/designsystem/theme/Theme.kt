package org.companerodeescuela.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = CompanionColors.crimson,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = CompanionColors.crimsonContainer,
    onPrimaryContainer = CompanionColors.crimson,
    secondary = CompanionColors.semanticBlue,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = CompanionColors.semanticBlueContainer,
    onSecondaryContainer = CompanionColors.semanticBlue,
    tertiary = CompanionColors.semanticAmber,
    onTertiary = androidx.compose.ui.graphics.Color.White,
    tertiaryContainer = CompanionColors.semanticAmberContainer,
    onTertiaryContainer = CompanionColors.semanticAmber,
    error = CompanionColors.semanticRed,
    onError = androidx.compose.ui.graphics.Color.White,
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
    surfaceVariant = CompanionColors.darkSurfaceVariant,
    onSurfaceVariant = CompanionColors.onDarkSurfaceVariant,
    outline = CompanionColors.darkOutline,
)

/**
 * App theme.
 *
 * Dynamic colour is opt-in rather than the default: attendance and schedule
 * screens are shared on school projectors, and Material You wallpaper
 * extraction makes those screens unreadable on some devices.
 */
@Composable
fun CompaneroTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CompanionTypography,
        shapes = CompanionShapes,
        content = content,
    )
}
