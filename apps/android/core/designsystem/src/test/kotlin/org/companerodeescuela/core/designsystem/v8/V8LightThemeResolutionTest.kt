package org.companerodeescuela.core.designsystem.v8

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class V8LightThemeResolutionTest {
    @Test
    fun `light edition replaces muddy surfaces with white and legible ink`() {
        val inherited =
            lightColorScheme(
                background = Color(0xFFE5E5E5),
                surface = Color(0xFF999999),
                onSurface = Color(0xFF777777),
                onSurfaceVariant = Color(0xFF9A9A9A),
            )
        val colors = v8ResolvedColorScheme(inherited, false)
        assertEquals(Color.White, colors.background)
        assertEquals(Color.White, colors.surface)
        assertEquals(Color(0xFF141720), colors.onSurface)
        assertEquals(Color(0xFF373E4B), colors.onSurfaceVariant)
        assertEquals(Color(0xFFF5F6F8), colors.surfaceContainer)
    }

    @Test
    fun `light high contrast uses opaque white and dark readable strokes`() {
        val colors = v8ResolvedColorScheme(lightColorScheme(background = Color.White), true)
        assertEquals(Color.White, colors.surface)
        assertEquals(Color.Black, colors.onSurfaceVariant)
        assertEquals(Color.Black, colors.outline)
    }

    @Test
    fun `dark mode retains official V8 brand while contrast deepens black`() {
        assertEquals(V8ColorScheme, v8ResolvedColorScheme(V8ColorScheme, false))
        assertEquals(Color.Black, v8ResolvedColorScheme(V8ColorScheme, true).background)
    }

    @Test
    fun `glass treatment honors the requested four theme combinations`() {
        assertEquals(V8SurfaceFinish.DARK_MATTE, v8SurfaceFinish(light = false, highContrast = false))
        assertEquals(V8SurfaceFinish.DARK_HIGH_CONTRAST, v8SurfaceFinish(light = false, highContrast = true))
        assertEquals(V8SurfaceFinish.LIGHT_CLEAN, v8SurfaceFinish(light = true, highContrast = false))
        assertEquals(V8SurfaceFinish.LIGHT_HIGH_CONTRAST, v8SurfaceFinish(light = true, highContrast = true))
    }
}
