package org.companerodeescuela.shared.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
enum class InstitutionalEventOperation {
    @SerialName("upsert")
    UPSERT,

    @SerialName("delete")
    DELETE,
}

/** Versioned, tenant-neutral envelope. The authenticated credential supplies the tenant. */
@Serializable
data class InstitutionalPushEvent(
    val schemaVersion: Int,
    val eventId: String,
    val entityType: String,
    val externalId: String,
    val sourceVersion: Long,
    val occurredAtEpochSeconds: Long,
    val operation: InstitutionalEventOperation,
    val payload: JsonObject,
)

@Serializable
data class InstitutionalPushReceipt(
    val eventId: String,
    val status: String,
    val receivedAtEpochSeconds: Long,
)
