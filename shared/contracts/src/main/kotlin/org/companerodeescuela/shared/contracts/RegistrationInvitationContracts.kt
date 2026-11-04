package org.companerodeescuela.shared.contracts

import kotlinx.serialization.Serializable

/** A code entered by the recipient; it cannot change account roles. */
@Serializable
data class AcceptRegistrationInvitationRequest(
    val invitationId: String,
    val code: String,
    val operationId: String,
)

@Serializable
data class RegistrationInvitationReceipt(
    val invitationId: String,
    val status: String,
)

/** Restricted to verified platform owners; the backend binds the preparation to MFA. */
@Serializable
data class PrepareRegistrationInvitationRequest(
    val recipient: String,
    val institutionId: String,
    val profile: String,
    val operationId: String,
    val proof: String? = null,
) {
    override fun toString() = "PrepareRegistrationInvitationRequest([redacted])"
}

@Serializable
data class RegistrationInvitationPreparation(
    val operationId: String,
    val resourceId: String,
    val payloadDigest: String,
)

@Serializable
data class RegistrationInvitationDispatchReceipt(
    val invitationId: String,
    val delivery: VerificationDeliveryStatus,
    val expiresAtEpochSeconds: Long,
)
