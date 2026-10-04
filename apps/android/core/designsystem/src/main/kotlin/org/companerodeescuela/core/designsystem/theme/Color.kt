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
    val darkSurface = Color(0xFF242528)
    val darkSurfaceVariant = Color(0xFF45474A)
    val onDarkSurface = Color(0xFFE9E6E3)
    val onDarkSurfaceVariant = Color(0xFFC9C4C0)
    val darkOutline = Color(0xFF938C88)
}
