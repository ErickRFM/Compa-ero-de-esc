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
import org.companerodeescuela.core.designsystem.theme.CompanionPalette as P

private val LightColors = lightColorScheme(
    primary = P.Violet40,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = P.Violet90,
    onPrimaryContainer = P.Violet30,
    secondary = P.Cyan40,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = P.Cyan90,
    onSecondaryContainer = P.Cyan30,
    tertiary = P.Amber40,
    onTertiary = androidx.compose.ui.graphics.Color.White,
    tertiaryContainer = P.Amber90,
    onTertiaryContainer = P.Amber20,
    error = P.Red40,
    onError = androidx.compose.ui.graphics.Color.White,
    errorContainer = P.Red90,
    onErrorContainer = P.Red30,
    background = P.NeutralBackgroundLight,
    onBackground = P.NeutralOnSurfaceLight,
    surface = P.NeutralSurfaceLight,
    onSurface = P.NeutralOnSurfaceLight,
    surfaceVariant = P.NeutralSurfaceVariantLight,
    onSurfaceVariant = P.NeutralOnSurfaceVariantLight,
    outline = P.NeutralOutlineLight,
)

private val DarkColors = darkColorScheme(
    primary = P.Violet80,
    onPrimary = P.Violet30,
    primaryContainer = P.Violet30,
    onPrimaryContainer = P.Violet90,
    secondary = P.Cyan80,
    onSecondary = P.Cyan30,
    secondaryContainer = P.Cyan30,
    onSecondaryContainer = P.Cyan90,
    tertiary = P.Amber80,
    onTertiary = P.Amber20,
    tertiaryContainer = P.Amber20,
    onTertiaryContainer = P.Amber90,
    error = P.Red80,
    onError = P.Red30,
    errorContainer = P.Red30,
    onErrorContainer = P.Red90,
    background = P.NeutralBackgroundDark,
    onBackground = P.NeutralOnSurfaceDark,
    surface = P.NeutralSurfaceDark,
    onSurface = P.NeutralOnSurfaceDark,
    surfaceVariant = P.NeutralSurfaceVariantDark,
    onSurfaceVariant = P.NeutralOnSurfaceVariantDark,
    outline = P.NeutralOutlineDark,
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
