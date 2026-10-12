package org.companerodeescuela.core.designsystem.v8

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class V8HighContrastThemeTest {
    @Test
    fun `normal V8 keeps the existing branded dark palette`() {
        assertEquals(V8ColorScheme, v8EffectiveColorScheme(false))
    }

    @Test
    fun `high contrast uses black background and opaque high contrast surfaces`() {
        val scheme = v8EffectiveColorScheme(true)
        assertEquals(Color.Black, scheme.background)
        assertEquals(Color.White, scheme.onBackground)
        assertEquals(Color.White, scheme.onSurface)
        assertEquals(Color.White, scheme.outline)
        assertEquals(Color(0xFF0F0E13), scheme.surface)
    }
}
