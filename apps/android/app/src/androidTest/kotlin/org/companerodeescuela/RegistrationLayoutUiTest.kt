package org.companerodeescuela

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.companerodeescuela.core.designsystem.theme.CompaneroTheme
import org.companerodeescuela.feature.auth.RegistrationScreen
import org.companerodeescuela.feature.auth.SessionUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Real registration content, empty form; no account is created. */
class RegistrationLayoutUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun largeFontShowsWholeAccountLabelsAndAllowsTeacherSelection() {
        render(2f)
        assertWholeLabel("Alumno")
        assertWholeLabel("Docente")
        val student = compose.onNodeWithText("Alumno", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val teacher = compose.onNodeWithText("Docente", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Large text options need separate rows", teacher.top > student.bottom)
        compose.onNodeWithText("Docente").performScrollTo().performClick()
        compose.onNodeWithText("Las cuentas docentes requieren verificación para operar sus clases asignadas y publicar avisos.")
            .performScrollTo().assertExists()
        compose.onNodeWithText("Ya tengo cuenta").performScrollTo().assertExists()
    }

    @Test fun normalFontPreservesTwoAccountOptionsInOneRow() {
        render(1f)
        assertWholeLabel("Alumno")
        assertWholeLabel("Docente")
        val student = compose.onNodeWithText("Alumno", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val teacher = compose.onNodeWithText("Docente", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(student.center.y, teacher.center.y, 1f)
    }

    private fun assertWholeLabel(label: String) {
        compose.onNodeWithText(label).performScrollTo()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(label, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals("$label must remain a whole word", 1, layouts.single().lineCount)
    }

    private fun render(fontScale: Float) {
        compose.setContent {
            val density = LocalDensity.current.density
            CompaneroTheme {
                CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                    Box(Modifier.width(360.dp)) {
                        RegistrationScreen(SessionUiState(checking = false), { _, _, _, _ -> error("Empty form cannot register") }, {})
                    }
                }
            }
        }
    }
}
