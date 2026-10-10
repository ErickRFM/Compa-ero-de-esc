package org.companerodeescuela.shared.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class RepresentativePosition {
    @SerialName("chief") CHIEF,
    @SerialName("deputy") DEPUTY,
}

@Serializable
enum class RepresentativeStatus {
    @SerialName("pending") PENDING,
    @SerialName("active") ACTIVE,
    @SerialName("declined") DECLINED,
    @SerialName("revoked") REVOKED,
    @SerialName("expired") EXPIRED,
}

@Serializable
data class GroupRepresentativeAssignment(
    val id: String,
    val institutionId: String? = null,
    val academicGroupId: String,
    val academicGroupName: String,
    val academicTermId: String? = null,
    val studentUserId: String,
    val studentDisplayName: String? = null,
    val position: RepresentativePosition,
    val status: RepresentativeStatus = RepresentativeStatus.PENDING,
    val appointedBy: String,
    val appointedAtEpochSeconds: Long,
    val acceptedAtEpochSeconds: Long? = null,
    val expiresAtEpochSeconds: Long? = null,
    val revokedAtEpochSeconds: Long? = null,
    val revokedBy: String? = null,
    val version: Long = 1L,
)

@Serializable
data class AppointRepresentativeRequest(
    val studentUserId: String,
    val position: RepresentativePosition,
    val academicTermId: String? = null,
)

@Serializable
data class RevokeRepresentativeRequest(
    val reason: String? = null,
)

@Serializable
data class GroupRepresentativeSummary(
    val assignment: GroupRepresentativeAssignment,
    val canRevoke: Boolean = false,
)

@Serializable
data class GroupRepresentativesOverview(
    val groupId: String,
    val groupName: String,
    val chief: GroupRepresentativeAssignment? = null,
    val deputy: GroupRepresentativeAssignment? = null,
    val pendingInvitations: List<GroupRepresentativeAssignment> = emptyList(),
)
