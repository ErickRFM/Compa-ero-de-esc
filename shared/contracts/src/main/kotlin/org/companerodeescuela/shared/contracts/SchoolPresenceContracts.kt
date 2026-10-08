package org.companerodeescuela.shared.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SchoolPresenceStatus {
    @SerialName("active") ACTIVE,
    @SerialName("closed") CLOSED,
    @SerialName("expired") EXPIRED,
}

@Serializable
enum class NetworkVerificationMethod {
    @SerialName("ssid") SSID,
    @SerialName("bssid") BSSID,
    @SerialName("ssid_bssid") SSID_BSSID,
}

@Serializable
data class SchoolNetworkEvidence(
    val ssid: String? = null,
    val bssid: String? = null,
)

@Serializable
data class StartSchoolPresenceRequest(
    val operationId: String,
    val qrToken: String,
    val network: SchoolNetworkEvidence,
    val deviceTimestampEpochSeconds: Long,
)

@Serializable
data class SchoolPresenceResponse(
    val id: String,
    val studentId: String,
    val startedAtEpochSeconds: Long,
    val expiresAtEpochSeconds: Long,
    val closedAtEpochSeconds: Long? = null,
    val status: SchoolPresenceStatus,
    val qrVerified: Boolean,
    val networkVerified: Boolean,
    val networkVerificationMethod: NetworkVerificationMethod,
    val serverTimeEpochSeconds: Long? = null,
)

@Serializable
data class CloseSchoolPresenceRequest(
    val reason: String? = null,
)
