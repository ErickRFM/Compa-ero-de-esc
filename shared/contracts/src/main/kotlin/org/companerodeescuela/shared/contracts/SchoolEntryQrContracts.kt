package org.companerodeescuela.shared.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SchoolEntryQrStatus {
    @SerialName("active") ACTIVE,
    @SerialName("expired") EXPIRED,
    @SerialName("revoked") REVOKED,
}

@Serializable
data class CreateSchoolEntryQrRequest(
    val name: String,
    val location: String? = null,
    val validFromEpochSeconds: Long,
    val expiresAtEpochSeconds: Long,
)

@Serializable
data class SchoolEntryQrResponse(
    val id: String,
    val name: String,
    val location: String? = null,
    val token: String? = null,
    val status: SchoolEntryQrStatus,
    val validFromEpochSeconds: Long,
    val expiresAtEpochSeconds: Long,
    val createdBy: String,
    val createdAtEpochSeconds: Long,
    val revokedAtEpochSeconds: Long? = null,
    val lastUsedAtEpochSeconds: Long? = null,
    val usageCount: Long = 0,
)

@Serializable
data class RegenerateSchoolEntryQrResponse(
    val revoked: SchoolEntryQrResponse,
    val replacement: SchoolEntryQrResponse,
)
