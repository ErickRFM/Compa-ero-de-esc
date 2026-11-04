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
