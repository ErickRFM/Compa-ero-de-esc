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

@Serializable
data class PrepareRegistrationInvitationRetryRequest(
    val invitationId: String,
    val operationId: String,
)

@Serializable
data class RetryRegistrationInvitationRequest(
    val invitationId: String,
    val operationId: String,
    val proof: String,
) {
    override fun toString() = "RetryRegistrationInvitationRequest(invitationId=$invitationId, operationId=$operationId, proof=[redacted])"
}

@Serializable
data class RegistrationInvitationSummary(
    val invitationId: String,
    val recipient: String,
    val institutionId: String,
    val profile: String,
    val status: String,
    val createdAtEpochSeconds: Long,
    val expiresAtEpochSeconds: Long,
    val acceptedAtEpochSeconds: Long? = null,
    val revokedAtEpochSeconds: Long? = null,
    val deliveryStatus: String = "PENDING",
    val deliveryUpdatedAtEpochSeconds: Long? = null,
    val acceptedByAccountId: String? = null,
) {
    override fun toString() =
        "RegistrationInvitationSummary(id=$invitationId, profile=$profile, status=$status, deliveryStatus=$deliveryStatus)"
}
