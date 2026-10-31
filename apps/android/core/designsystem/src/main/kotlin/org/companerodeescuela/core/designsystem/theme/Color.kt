package org.companerodeescuela.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette for the whole app.
 *
 * Every pair below was checked for contrast against its `on*` colour, because
 * a design system that ships unreadable combinations is worse than one that
 * ships no colours at all. Dark variants are defined here too: the app is
 * used in dim classrooms and outdoors, so light-only is not an option.
 */
object CompanionColors {
    val crimson = Color(0xFF9B1C31)
    val crimsonContainer = Color(0xFFF5DADF)
    val crimsonSubtle = Color(0xFFFCEFF1)

    val graphite = Color(0xFF242528)
    val graphiteRaised = Color(0xFF34363A)
    val graphiteSoft = Color(0xFF55585D)

    val warmBackground = Color(0xFFF6F4F1)
    val warmSurface = Color(0xFFFFFCF9)
    val warmSurfaceVariant = Color(0xFFEAE6E1)

    val institutionalGold = Color(0xFF765500)

    val semanticGreen = Color(0xFF2F6B48)
    val semanticGreenContainer = Color(0xFFDCEFE2)
    val semanticGreenDark = Color(0xFFA6D8B5)
    val semanticGreenDarkContainer = Color(0xFF204C32)
    val semanticBlue = Color(0xFF42627A)
    val semanticBlueContainer = Color(0xFFDDEAF2)
    val semanticBlueDark = Color(0xFFB0CDE0)
    val semanticAmber = Color(0xFF8A5100)
    val semanticAmberContainer = Color(0xFFFFDDB8)
    val semanticRed = Color(0xFFBA1A1A)
    val semanticRedContainer = Color(0xFFFFDAD6)

    val onLightSurface = Color(0xFF211F1E)
    val onLightSurfaceVariant = Color(0xFF514B49)
    val lightOutline = Color(0xFF79716E)

    val darkBackground = Color(0xFF191A1C)
    val darkSurface = Color(0xFF222326)
    val darkInset = Color(0xFF26272A)
    val darkRaised = Color(0xFF2B2D30)
    val darkSurfaceVariant = Color(0xFF313338)
    val darkFloating = Color(0xFF383A3F)
    val onDarkSurface = Color(0xFFE9E6E3)
    val onDarkSurfaceVariant = Color(0xFFC9C4C0)
    val darkOutline = Color(0xFF938C88)
}

/**
 * Curated accent palettes that preserve WCAG AA contrast across light, dark,
 * and high-contrast surfaces while letting users personalize the app.
 */
enum class CompaneroAccentPalette(
    val displayName: String,
    val lightPrimary: Color,
    val lightContainer: Color,
    val darkPrimary: Color,
    val darkContainer: Color,
    val previewColor: Color,
) {
    CRIMSON(
        displayName = "Carmesí UPTlax",
        lightPrimary = Color(0xFF9B1C31),
        lightContainer = Color(0xFFF5DADF),
        darkPrimary = Color(0xFFFF303F),
        darkContainer = Color(0xFFB31328),
        previewColor = Color(0xFFD91E36),
    ),
    COBALT(
        displayName = "Azul Cobalto",
        lightPrimary = Color(0xFF1D4ED8),
        lightContainer = Color(0xFFDBEAFE),
        darkPrimary = Color(0xFF60A5FA),
        darkContainer = Color(0xFF1E40AF),
        previewColor = Color(0xFF2563EB),
    ),
    EMERALD(
        displayName = "Esmeralda",
        lightPrimary = Color(0xFF0F766E),
        lightContainer = Color(0xFFCCFBF1),
        darkPrimary = Color(0xFF34D399),
        darkContainer = Color(0xFF065F46),
        previewColor = Color(0xFF10B981),
    ),
    VIOLET(
        displayName = "Violeta",
        lightPrimary = Color(0xFF6D28D9),
        lightContainer = Color(0xFFEDE9FE),
        darkPrimary = Color(0xFFA78BFA),
        darkContainer = Color(0xFF5B21B6),
        previewColor = Color(0xFF7C3AED),
    ),
    AMBER(
        displayName = "Ámbar Dorado",
        lightPrimary = Color(0xFFB45309),
        lightContainer = Color(0xFFFEF3C7),
        darkPrimary = Color(0xFFFBBF24),
        darkContainer = Color(0xFF92400E),
        previewColor = Color(0xFFD97706),
    ),
    ROSE(
        displayName = "Rosa Coral",
        lightPrimary = Color(0xFFBE123C),
        lightContainer = Color(0xFFFFE4E6),
        darkPrimary = Color(0xFFFB7185),
        darkContainer = Color(0xFF9F1239),
        previewColor = Color(0xFFE11D48),
    ),
}
