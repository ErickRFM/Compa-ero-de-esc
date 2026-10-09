package org.companerodeescuela.feature.settings

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
        assertEquals(1f, settings.textScale)
        assertFalse(settings.reducedMotion)
        assertFalse(settings.highContrast)
    }

    @Test
    fun `custom values retain configured options`() {
        val settings = AppearanceSettings(
            themeMode = AppThemeMode.DARK,
            language = AppLanguage.SPANISH,
            textScale = 1.2f,
            reducedMotion = true,
            highContrast = true,
        )
        assertEquals(AppThemeMode.DARK, settings.themeMode)
        assertEquals(AppLanguage.SPANISH, settings.language)
        assertEquals("es", settings.language.languageTag)
        assertEquals(1.2f, settings.textScale)
        assertTrue(settings.reducedMotion)
        assertTrue(settings.highContrast)
    }
}
