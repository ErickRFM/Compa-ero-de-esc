package org.companerodeescuela.shared.contracts

import kotlinx.serialization.Serializable

@Serializable data class InstitutionSummary(val id: String, val displayName: String)
/** ACCEPTED means the provider accepted the request, not proven inbox delivery. */
@Serializable enum class VerificationDeliveryStatus { PENDING, ACCEPTED, UNAVAILABLE, UNCONFIRMED, FAILED }
@Serializable data class VerifyEmailRequest(val token: String)
@Serializable data class VerificationDeliveryReceipt(val status: VerificationDeliveryStatus, val resendAfterSeconds: Long)
