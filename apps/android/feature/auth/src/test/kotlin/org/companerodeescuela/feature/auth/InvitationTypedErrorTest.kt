package org.companerodeescuela.feature.auth

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import org.companerodeescuela.core.common.result.AppError

class InvitationTypedErrorTest {
    @Test
    fun institutionMessageHasActionableGuidanceAndSupportId() {
        val result =
            formatInvitationFailure(
                InvitationIssueStep.PREPARE,
                AppError.Http(400, "untrusted secret", "request-123", "INVITATION_INSTITUTION_UNAVAILABLE"),
            )
        assertContains(result, "institución")
        assertContains(result, "request-123")
        assertFalse(result.contains("untrusted secret"))
    }

    @Test
    fun mfaFailureCodesMapToDistinctActionsWithoutExposingRawMessage() {
        val messages =
            mapOf(
                "MFA_ENROLLMENT_REQUIRED" to "activar",
                "MFA_REAUTHENTICATION_FAILED" to "contraseña",
                "MFA_CODE_INVALID" to "expiró",
                "MFA_CODE_REUSED" to "utilizó",
            )
        messages.forEach { (code, expectation) ->
            val result =
                formatInvitationFailure(
                    InvitationIssueStep.MFA_STEP_UP,
                    AppError.Http(403, "untrusted secret", apiCode = code),
                )
            assertContains(result, expectation)
            assertFalse(result.contains("untrusted secret"))
        }
    }

    @Test
    fun unknownCodeRetainsExistingContextualError() {
        val result =
            formatInvitationFailure(
                InvitationIssueStep.MFA_STEP_UP,
                AppError.Http(403, apiCode = "FUTURE_ERROR"),
            )
        assertContains(result, "MFA")
    }
}
