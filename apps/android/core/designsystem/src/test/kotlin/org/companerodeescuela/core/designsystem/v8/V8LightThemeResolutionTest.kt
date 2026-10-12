package org.companerodeescuela.core.designsystem.v8

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class V8LightThemeResolutionTest {
    @Test fun `light scheme remains selected for student and teacher scaffolds`() {
        val colors = lightColorScheme(background = Color.White, onSurface = Color.Black)
        assertEquals(colors, v8ResolvedColorScheme(colors, false))
        assertEquals(colors, v8ResolvedColorScheme(colors, true))
    }

    @Test fun `dark mode retains official V8 brand scheme`() {
        assertEquals(V8ColorScheme, v8ResolvedColorScheme(V8ColorScheme, false))
        assertEquals(Color.Black, v8ResolvedColorScheme(V8ColorScheme, true).background)
    }
}
