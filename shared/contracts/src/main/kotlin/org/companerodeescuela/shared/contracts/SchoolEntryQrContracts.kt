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
    val institutionId:String?=null,
    val operationId:String?=null,
    val proof:String?=null,
) {
    override fun toString()="CreateSchoolEntryQrRequest(institutionId=$institutionId, operationId=$operationId, proof=[redacted])"
}

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
    val institutionId:String?=null,
    val revision:Long=0,
    val operationId:String?=null,
    val commandDigest:String?=null,
    val updatedBy:String?=null,
    val audit:List<SchoolEntryQrAuditEntry> = emptyList(),
) {
    override fun toString()="SchoolEntryQrResponse(id=$id, institutionId=$institutionId, status=$status, revision=$revision, token=[redacted])"
}

@Serializable
data class SchoolEntryQrAuditEntry(val actorId:String,val operationId:String,val commandDigest:String,val fromRevision:Long,val status:SchoolEntryQrStatus,val atEpochSeconds:Long)

@Serializable
data class RegenerateSchoolEntryQrResponse(
    val revoked: SchoolEntryQrResponse,
    val replacement: SchoolEntryQrResponse,
)
