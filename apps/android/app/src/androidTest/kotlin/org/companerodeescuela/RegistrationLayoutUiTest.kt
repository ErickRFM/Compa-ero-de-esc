package org.companerodeescuela

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.companerodeescuela.core.designsystem.theme.CompaneroTheme
import org.companerodeescuela.feature.auth.RegistrationScreen
import org.companerodeescuela.feature.auth.SessionUiState
import org.companerodeescuela.shared.contracts.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real Compose form; submission is captured locally, no account is created. */
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
        compose.onNodeWithText("Docentes y tutores requieren verificación de correo y aprobación administrativa.").performScrollTo().assertExists()
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
    @Test fun universalRegistrationOffersTutorAndParticipantWithoutPrivilegedProfiles() {
        render(1f)
        compose.onNodeWithText("Tutor").performScrollTo().performClick()
        compose.onNodeWithText("Identificación profesional o referencia").performScrollTo().assertExists()
        compose.onNodeWithText("Grupo solicitado (preferencia)").performScrollTo().assertExists()
        compose.onNodeWithText("Participante").performScrollTo().performClick()
        compose.onNodeWithText("Identificación profesional o referencia").assertDoesNotExist()
        compose.onNodeWithText("Administrador").assertDoesNotExist()
        compose.onNodeWithText("Superadministrador").assertDoesNotExist()
    }
    @Test fun tutorRequiresActualInstitutionAndSendsPreferenceWithoutRoleGrant() {
        var submitted: RegisterRequest? = null
        render(1f, SessionUiState(checking = false, institutions = listOf(InstitutionSummary("campus", "Test campus")))) { submitted = it }
        fillCredentials()
        compose.onNodeWithText("Tutor").performScrollTo().performClick()
        compose.onNodeWithTag("registration_submit").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("registration_institution").performScrollTo().performClick()
        compose.onNodeWithText("Test campus").performClick()
        compose.onNodeWithTag("registration_identity").performScrollTo().performTextInput("professional-reference")
        compose.onNodeWithTag("registration_group").performScrollTo().performTextInput("preferred-group")
        compose.onNodeWithTag("registration_submit").performScrollTo().performClick()
        assertEquals(RegistrationAccountType.TUTOR, submitted?.accountType)
        assertEquals("campus", submitted?.requestedInstitutionId)
        assertEquals("preferred-group", submitted?.preferredGroup)
        assertEquals("qa@example.test", submitted?.email)
    }
    @Test fun participantDoesNotSendPreviouslyEnteredAcademicPreferences() {
        var submitted: RegisterRequest? = null
        render(1f, SessionUiState(checking = false, institutions = listOf(InstitutionSummary("campus", "Test campus")))) { submitted = it }
        fillCredentials()
        compose.onNodeWithText("Tutor").performScrollTo().performClick()
        compose.onNodeWithTag("registration_identity").performScrollTo().performTextInput("professional-reference")
        compose.onNodeWithTag("registration_group").performScrollTo().performTextInput("preferred-group")
        compose.onNodeWithText("Participante").performScrollTo().performClick()
        compose.onNodeWithTag("registration_submit").performScrollTo().performClick()
        assertEquals(RegistrationAccountType.PARTICIPANT, submitted?.accountType)
        assertNull(submitted?.identityReference)
        assertNull(submitted?.requestedInstitutionId)
        assertNull(submitted?.preferredGroup)
    }
    private fun fillCredentials() {
        compose.onNodeWithTag("registration_name").performScrollTo().performTextInput("QA Native")
        compose.onNodeWithTag("registration_email").performScrollTo().performTextInput("qa@example.test")
        compose.onNodeWithTag("registration_password").performScrollTo().performTextInput("test-password")
        compose.onNodeWithTag("registration_confirmation").performScrollTo().performTextInput("test-password")
    }
    private fun assertWholeLabel(label: String) {
        compose.onNodeWithText(label).performScrollTo()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals("$label must remain a whole word", 1, layouts.single().lineCount)
    }
    private fun render(fontScale: Float, state: SessionUiState = SessionUiState(checking = false),
        submit: (RegisterRequest) -> Unit = { error("Empty form cannot register") }) {
        compose.setContent { AuthTestLocale {
            CompaneroTheme {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val density = constraints.maxWidth / 360f
                    CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides Density(density, fontScale)) {
                        Box(Modifier.width(360.dp)) { RegistrationScreen(state, submit, {}) }
                    }
                }
            }
        } }
    }
}
