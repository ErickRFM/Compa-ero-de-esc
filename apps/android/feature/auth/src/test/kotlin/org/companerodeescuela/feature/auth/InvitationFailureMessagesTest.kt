package org.companerodeescuela.feature.auth

import org.companerodeescuela.core.common.result.AppError
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class InvitationFailureMessagesTest {
    @Test fun institutionValidationHasActionableLocalizedMessage() {
        val result = invitationFailureMessage(
            AppError.Http(400, "sensitive", apiCode = "INVITATION_INSTITUTION_UNAVAILABLE"),
            InvitationStage.PREPARE,
        )
        assertContains(result, "institución")
        assertFalse(result.contains("sensitive"))
    }

    @Test fun mfaErrorsGiveUsefulDistinctGuidanceWithoutEchoingRawBody() {
        val cases = mapOf(
            "MFA_ENROLLMENT_REQUIRED" to "activar",
            "MFA_REAUTHENTICATION_FAILED" to "contraseña",
            "MFA_CODE_INVALID" to "expiró",
            "MFA_CODE_REUSED" to "utilizó",
        )
        cases.forEach { (code, expected) ->
            val message = invitationFailureMessage(
                AppError.Http(403, "secret", apiCode = code),
                InvitationStage.MFA,
            )
            assertContains(message, expected)
            assertFalse(message.contains("secret"))
        }
    }

    @Test fun fallback403ReflectsStage() {
        val error = AppError.Http(403)
        assertContains(invitationFailureMessage(error, InvitationStage.MFA), "MFA")
        assertContains(invitationFailureMessage(error, InvitationStage.PREPARE), "superadministradora")
    }
}
