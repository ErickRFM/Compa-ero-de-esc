package org.companerodeescuela.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Stable academic color policy. Subject identity is visual only: these colors
 * never encode success, warning, attendance or other semantic states.
 */
object AcademicSubjectColors {
    private val light = listOf(
        Color(0xFF3F51B5),
        Color(0xFF1565C0),
        Color(0xFF007C91),
        Color(0xFF2E7D32),
        Color(0xFF9A6700),
        Color(0xFFC04B00),
        Color(0xFF6A4BBC),
        Color(0xFFAD3F6B),
    )

    private val dark = listOf(
        Color(0xFF9FA8DA),
        Color(0xFF90CAF9),
        Color(0xFF80DEEA),
        Color(0xFFA5D6A7),
        Color(0xFFFFCC80),
        Color(0xFFFFAB91),
        Color(0xFFCEB5FF),
        Color(0xFFF4A7C1),
    )

    @Composable
    fun accent(
        key: String,
        surface: Color,
    ): Color {
        val palette = if (surface.luminance() < 0.5f) dark else light
        val index = (key.hashCode() and Int.MAX_VALUE) % palette.size
        return palette[index]
    }
}
