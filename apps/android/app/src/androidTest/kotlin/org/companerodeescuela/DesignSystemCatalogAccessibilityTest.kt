package org.companerodeescuela

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.companerodeescuela.core.designsystem.theme.CompaneroAccentPalette
import org.companerodeescuela.core.designsystem.theme.CompaneroTheme
import org.companerodeescuela.feature.designsystem.DesignSystemCatalogScreen
import org.companerodeescuela.feature.designsystem.accentPaletteAccessibilityLabel
import org.junit.Rule
import org.junit.Test

class DesignSystemCatalogAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun accentPaletteChoicesAreNamedSelectedRadioButtonsAtLeast48dp() {
        compose.setContent {
            CompaneroTheme {
                DesignSystemCatalogScreen()
            }
        }

        CompaneroAccentPalette.entries.forEach { palette ->
            val labelledNode = compose.onNodeWithContentDescription(accentPaletteAccessibilityLabel(palette))
            labelledNode.assertIsDisplayed()
            labelledNode.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            val density = compose.density.density
            val bounds = labelledNode.fetchSemanticsNode().boundsInRoot
            val widthDp = bounds.width / density
            val heightDp = bounds.height / density
            assert(widthDp >= 48f) {
                "${palette.displayName} target width must be >=48dp " +
                    "(bounds=${bounds.width}x${bounds.height}px, density=$density, actual=${widthDp}dp x ${heightDp}dp)"
            }
            assert(heightDp >= 48f) {
                "${palette.displayName} target height must be >=48dp " +
                    "(bounds=${bounds.width}x${bounds.height}px, density=$density, actual=${widthDp}dp x ${heightDp}dp)"
            }
        }

        val initial = CompaneroAccentPalette.CRIMSON
        compose
            .onNodeWithContentDescription(accentPaletteAccessibilityLabel(initial))
            .assertIsSelected()

        val chosen = CompaneroAccentPalette.VIOLET
        compose.onNodeWithContentDescription(accentPaletteAccessibilityLabel(chosen)).performClick()
        compose.onNodeWithContentDescription(accentPaletteAccessibilityLabel(chosen)).assertIsSelected()
    }
}
