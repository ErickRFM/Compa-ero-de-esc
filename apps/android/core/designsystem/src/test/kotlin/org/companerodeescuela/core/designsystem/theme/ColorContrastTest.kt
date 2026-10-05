package org.companerodeescuela.core.designsystem.theme

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

class ColorContrastTest {
    @Test
    fun `critical V5 3 pairs meet normal text contrast`() {
        val pairs = listOf(
            "#9B1C31" to "#FFFFFF", // crimson / white
            "#F5DADF" to "#9B1C31", // primary container / crimson
            "#F6F4F1" to "#211F1E", // light background / text
            "#222326" to "#E9E6E3", // dark surface / text
            "#313338" to "#C9C4C0", // dark raised / secondary text
        )

        pairs.forEach { (background, foreground) ->
            assertTrue(
                contrastRatio(background, foreground) >= 4.5,
                "Expected WCAG AA contrast for $foreground on $background",
            )
        }
    }

    private fun contrastRatio(first: String, second: String): Double {
        val firstLuminance = relativeLuminance(first)
        val secondLuminance = relativeLuminance(second)
        return (max(firstLuminance, secondLuminance) + 0.05) /
            (min(firstLuminance, secondLuminance) + 0.05)
    }

    private fun relativeLuminance(hex: String): Double {
        val rgb = hex.removePrefix("#")
            .chunked(2)
            .map { it.toInt(16) / 255.0 }
            .map { channel ->
                if (channel <= 0.04045) {
                    channel / 12.92
                } else {
                    ((channel + 0.055) / 1.055).pow(2.4)
                }
            }
        return 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2]
    }
}
