package org.companerodeescuela.feature.auth

import org.companerodeescuela.core.common.result.AppError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InvitationIssueFailureFormattingTest {
    @Test
    fun `prepare 404 and 503 explain disabled invitations and include request id`() {
        val state =
            InvitationIssueState(
                failure = AppError.Http(status = 404, technicalDetail = """{"secret":"raw"}""", requestId = "req-123"),
                failureStep = InvitationIssueStep.PREPARE,
            )
        assertEquals("No se pudo preparar la invitación", state.failureTitle)
        val message = requireNotNull(state.failureMessage)
        assertTrue(message.contains("PRODUCTION_INVITATIONS_ENABLED"))
        assertTrue(message.contains("HTTP 404"))
        assertTrue(message.contains("Referencia: req-123"))
        assertFalse(message.contains("secret"))
        assertFalse(message.contains("{"))
    }

    @Test
    fun `mfa 403 explains password otp and mfa enrollment`() {
        val state =
            InvitationIssueState(
                failure = AppError.Http(status = 403, requestId = "req-mfa"),
                failureStep = InvitationIssueStep.MFA_STEP_UP,
            )
        assertEquals("Error en la verificación MFA", state.failureTitle)
        val message = requireNotNull(state.failureMessage)
        assertTrue(message.contains("MFA"))
        assertTrue(message.contains("OTP"))
        assertTrue(message.contains("HTTP 403"))
        assertTrue(message.contains("Referencia: req-mfa"))
    }

    @Test
    fun `dispatch 409 explains duplicate operation conflict`() {
        val state =
            InvitationIssueState(
                failure = AppError.Http(status = 409),
                failureStep = InvitationIssueStep.DISPATCH,
            )
        assertEquals("No se pudo enviar la invitación", state.failureTitle)
        val message = requireNotNull(state.failureMessage)
        assertTrue(message.contains("HTTP 409"))
    }
}
