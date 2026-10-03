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
internal object CompanionPalette {
    // Brand: a calm indigo that stays legible on the low-brightness screens
    // common in school hardware.
    val Indigo10 = Color(0xFF00005C)
    val Indigo20 = Color(0xFF12177B)
    val Indigo40 = Color(0xFF2E3BC6)
    val Indigo80 = Color(0xFFBBC3FF)
    val Indigo90 = Color(0xFFE0E0FF)

    // Expressive V4 accents. These are intentionally deeper than the previous
    // palette so hero surfaces and dark mode can carry stronger identity
    // without sacrificing readable foregrounds.
    val Violet30 = Color(0xFF4430B8)
    val Violet40 = Color(0xFF5B43D6)
    val Violet80 = Color(0xFFC9BFFF)
    val Violet90 = Color(0xFFE8E0FF)

    val Cyan30 = Color(0xFF005B67)
    val Cyan40 = Color(0xFF007987)
    val Cyan80 = Color(0xFF5DD7E7)
    val Cyan90 = Color(0xFFA8EEFA)

    val DeepNavy = Color(0xFF0A1020)
    val DeepNavyRaised = Color(0xFF121A2E)

    // Secondary: teal, used for confirmations and "on time" states.
    val Teal20 = Color(0xFF00363C)
    val Teal40 = Color(0xFF00696F)
    val Teal80 = Color(0xFF4FD8DF)
    val Teal90 = Color(0xFFA8F2F6)

    // Tertiary: amber, reserved for warnings such as a late arrival.
    val Amber20 = Color(0xFF4A2800)
    val Amber40 = Color(0xFF8A5100)
    val Amber80 = Color(0xFFFFB871)
    val Amber90 = Color(0xFFFFDDB8)

    // Error: kept close to the Material defaults so it stays recognisable as
    // destructive even before the app adds its own iconography.
    val Red30 = Color(0xFF93000A)
    val Red40 = Color(0xFFBA1A1A)
    val Red80 = Color(0xFFFFB4AB)
    val Red90 = Color(0xFFFFDAD6)

    val NeutralBackgroundLight = Color(0xFFF8F7FF)
    val NeutralSurfaceLight = Color(0xFFFFFBFF)
    val NeutralSurfaceVariantLight = Color(0xFFE2E1EC)
    val NeutralOnSurfaceLight = Color(0xFF1B1B21)
    val NeutralOnSurfaceVariantLight = Color(0xFF45464F)
    val NeutralOutlineLight = Color(0xFF767680)

    val NeutralBackgroundDark = DeepNavy
    val NeutralSurfaceDark = DeepNavyRaised
    val NeutralSurfaceVariantDark = Color(0xFF45464F)
    val NeutralOnSurfaceDark = Color(0xFFE5E1E9)
    val NeutralOnSurfaceVariantDark = Color(0xFFC6C5D0)
    val NeutralOutlineDark = Color(0xFF90909A)
}
