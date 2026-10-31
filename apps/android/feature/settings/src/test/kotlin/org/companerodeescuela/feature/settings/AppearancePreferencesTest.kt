package org.companerodeescuela.feature.settings

import org.companerodeescuela.core.designsystem.theme.CompaneroAccentPalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppearancePreferencesTest {
    @Test
    fun `default settings use system values`() {
        val settings = AppearanceSettings()
        assertEquals(AppThemeMode.SYSTEM, settings.themeMode)
        assertEquals(AppLanguage.SYSTEM, settings.language)
        assertEquals(CompaneroAccentPalette.CRIMSON, settings.accentPalette)
        assertFalse(settings.dynamicColor)
        assertTrue(settings.campusBackdropEnabled)
        assertEquals(1f, settings.textScale)
        assertFalse(settings.reducedMotion)
        assertFalse(settings.highContrast)
    }

    @Test
    fun `custom values retain configured options`() {
        val settings =
            AppearanceSettings(
                themeMode = AppThemeMode.DARK,
                language = AppLanguage.SPANISH,
                accentPalette = CompaneroAccentPalette.COBALT,
                dynamicColor = true,
                campusBackdropEnabled = false,
                textScale = 1.2f,
                reducedMotion = true,
                highContrast = true,
            )
        assertEquals(AppThemeMode.DARK, settings.themeMode)
        assertEquals(AppLanguage.SPANISH, settings.language)
        assertEquals(CompaneroAccentPalette.COBALT, settings.accentPalette)
        assertTrue(settings.dynamicColor)
        assertFalse(settings.campusBackdropEnabled)
        assertEquals("es", settings.language.languageTag)
        assertEquals(1.2f, settings.textScale)
        assertTrue(settings.reducedMotion)
        assertTrue(settings.highContrast)
    }
}
