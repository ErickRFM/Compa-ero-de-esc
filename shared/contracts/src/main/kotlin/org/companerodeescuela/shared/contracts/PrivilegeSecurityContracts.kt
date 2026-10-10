package org.companerodeescuela.shared.contracts

import kotlinx.serialization.Serializable

@Serializable
enum class StepUpAction {
    APPROVE_STAFF, ACCOUNT_LIFECYCLE, MANAGE_ACADEMICS, ADMIN_INVITE, ADMIN_AUTHORITY,
    INSTITUTION_MANAGEMENT, WORKSHOP_REVIEW, WORKSHOP_FACILITATOR, PRIVILEGED_INVITE_ACCEPT,
}
@Serializable data class MfaStatus(val enabled: Boolean,val available: Boolean)
@Serializable data class MfaEnrollmentRequest(val password: String) {
    override fun toString()="MfaEnrollmentRequest([redacted])"
}
@Serializable data class MfaConfirmationRequest(val enrollmentId: String,val otp: String) {
    override fun toString()="MfaConfirmationRequest(enrollmentId=$enrollmentId, otp=[redacted])"
}
@Serializable data class MfaStepUpRequest(val password: String,val otp: String,val action: StepUpAction,
    val resourceId: String,val operationId: String,val payloadDigest: String) {
    override fun toString()="MfaStepUpRequest(action=$action, credentials=[redacted])"
}
@Serializable data class MfaEnrollment(val enrollmentId: String,val secret: String,val provisioningUri: String,val expiresAtEpochSeconds: Long) {
    override fun toString()="MfaEnrollment(enrollmentId=$enrollmentId, material=[redacted])"
}
@Serializable data class StepUpProof(val proof: String,val expiresAtEpochSeconds: Long) {
    override fun toString()="StepUpProof([redacted])"
}
