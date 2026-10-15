package org.companerodeescuela.shared.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class InstitutionalIdentityKind {
    @SerialName("student")
    STUDENT,

    @SerialName("teacher")
    TEACHER,
}

/** Links an institutional identifier to an already approved native account. */
@Serializable
data class InstitutionalIdentityLinkRequest(
    val institutionId: String,
    val entityType: InstitutionalIdentityKind,
    val externalId: String,
    val nativeUserId: String,
    val operationId: String,
    val proof: String,
)

@Serializable
data class InstitutionalIdentityLinkSummary(
    val institutionId: String,
    val entityType: InstitutionalIdentityKind,
    val externalId: String,
    val nativeUserId: String,
    val linkedBy: String,
    val linkedAtEpochSeconds: Long,
)
