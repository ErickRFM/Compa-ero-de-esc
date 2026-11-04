package org.companerodeescuela.shared.contracts

import kotlinx.serialization.Serializable

@Serializable data class InstitutionSummary(val id: String, val displayName: String)
/**
 * ACCEPTED means the provider accepted the request, not proven inbox delivery.
 * DELIVERED requires signed webhook evidence from the provider.
 */
@Serializable
enum class VerificationDeliveryStatus { PENDING, ACCEPTED, DELIVERED, UNAVAILABLE, UNCONFIRMED, FAILED }
@Serializable data class VerifyEmailRequest(val token: String)
@Serializable data class VerificationDeliveryReceipt(val status: VerificationDeliveryStatus, val resendAfterSeconds: Long)
