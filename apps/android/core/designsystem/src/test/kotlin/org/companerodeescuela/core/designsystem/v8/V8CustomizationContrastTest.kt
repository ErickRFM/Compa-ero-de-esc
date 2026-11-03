package org.companerodeescuela.core.designsystem.v8

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.companerodeescuela.core.designsystem.theme.CompaneroAccentPalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class V8CustomizationContrastTest {
    @Test
    fun `dynamic light preserves both foreground and background pairs`() {
        val parent =
            lightColorScheme(
                background = Color.White,
                primary = Color(0xFF244A37),
                onPrimary = Color(0xFFF0FFF3),
                primaryContainer = Color(0xFFC1EBCB),
                onPrimaryContainer = Color(0xFF092D18),
            )
        val actual = v8ResolvedColorScheme(parent, false, preserveDynamicPrimary = true)
        assertEquals(parent.primary, actual.primary)
        assertEquals(parent.onPrimary, actual.onPrimary)
        assertEquals(parent.primaryContainer, actual.primaryContainer)
        assertEquals(parent.onPrimaryContainer, actual.onPrimaryContainer)
    }

    @Test
    fun `dynamic dark preserves both foreground and background pairs`() {
        val parent =
            darkColorScheme(
                primary = Color(0xFFA0D3AC),
                onPrimary = Color(0xFF07391B),
                primaryContainer = Color(0xFF254D32),
                onPrimaryContainer = Color(0xFFC3EFCC),
            )
        val actual = v8ResolvedColorScheme(parent, false, preserveDynamicPrimary = true)
        assertEquals(parent.primary, actual.primary)
        assertEquals(parent.onPrimary, actual.onPrimary)
        assertEquals(parent.primaryContainer, actual.primaryContainer)
        assertEquals(parent.onPrimaryContainer, actual.onPrimaryContainer)
    }

    @Test
    fun `all accents have readable text on primary and container in both themes`() {
        CompaneroAccentPalette.entries.forEach { palette ->
            val scheme = v8EffectiveColorScheme(false, palette)
            assertTrue(contrast(scheme.primary, scheme.onPrimary) >= 4.5, "${palette.name} primary")
            assertTrue(contrast(scheme.primaryContainer, scheme.onPrimaryContainer) >= 4.5, "${palette.name} container")
            val light = v8ResolvedColorScheme(lightColorScheme(background = Color.White), false, palette)
            assertTrue(contrast(light.primary, light.onPrimary) >= 4.5, "${palette.name} light primary")
            assertTrue(contrast(light.primaryContainer, light.onPrimaryContainer) >= 4.5, "${palette.name} light container")
        }
    }

    private fun contrast(
        a: Color,
        b: Color,
    ): Double = (maxOf(a.luminance(), b.luminance()) + 0.05) / (minOf(a.luminance(), b.luminance()) + 0.05)
}
