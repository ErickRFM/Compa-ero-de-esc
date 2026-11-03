package org.companerodeescuela.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** The foreground belongs to its background, including wallpaper-derived colors. */
@Immutable
data class CompaneroColorPair(
    val background: Color,
    val foreground: Color,
)

/** Chooses the more legible opaque ink for a solid custom accent. */
fun accentForeground(background: Color): Color = if (background.luminance() > 0.179f) Color.Black else Color.White

@Composable
fun companeroAccentSurfaceColors(): CompaneroColorPair {
    val scheme = MaterialTheme.colorScheme
    return when {
        LocalCompaneroHighContrast.current -> CompaneroColorPair(scheme.surface, scheme.onSurface)
        LocalCompaneroDynamicColor.current -> CompaneroColorPair(scheme.primaryContainer, scheme.onPrimaryContainer)
        else -> LocalCompaneroAccentPalette.current.darkContainer.let { CompaneroColorPair(it, accentForeground(it)) }
    }
}

@Composable
fun companeroPrimaryActionColors(): CompaneroColorPair {
    val scheme = MaterialTheme.colorScheme
    if (LocalCompaneroDynamicColor.current) return CompaneroColorPair(scheme.primary, scheme.onPrimary)
    val background = if (scheme.background.luminance() > 0.5f) scheme.primary else LocalCompaneroAccentPalette.current.darkContainer
    return CompaneroColorPair(background, accentForeground(background))
}
