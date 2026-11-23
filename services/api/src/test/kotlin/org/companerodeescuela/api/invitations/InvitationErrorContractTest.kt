package org.companerodeescuela.api.invitations

import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.errors.toApiError
import org.companerodeescuela.shared.contracts.ApiErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals

class InvitationErrorContractTest {
    @Test fun typedInvitationAndMfaErrorsRetainStatusesAndSupportIds() {
        val missingInstitution = ApiException.Validation(
            "Institution does not accept new registrations",
            code = ApiErrorCode.INVITATION_INSTITUTION_UNAVAILABLE,
        )
        val missingMfa = ApiException.Forbidden(
            "An enrolled MFA factor is required",
            code = ApiErrorCode.MFA_ENROLLMENT_REQUIRED,
        )
        assertEquals(400, missingInstitution.httpStatus.value)
        assertEquals(ApiErrorCode.INVITATION_INSTITUTION_UNAVAILABLE, missingInstitution.toApiError("req-1").code)
        assertEquals("req-1", missingInstitution.toApiError("req-1").requestId)
        assertEquals(403, missingMfa.httpStatus.value)
        assertEquals(ApiErrorCode.MFA_ENROLLMENT_REQUIRED, missingMfa.toApiError("req-2").code)
    }
}
