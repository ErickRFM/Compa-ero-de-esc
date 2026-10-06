package org.companerodeescuela.shared.contracts

import kotlinx.serialization.Serializable

@Serializable
data class AcademicGroupSummary(
    val id: String,
    val name: String,
    val active: Boolean = true,
)

@Serializable
data class CreateAcademicGroupRequest(
    val id: String,
    val name: String,
)

@Serializable
data class AssignAcademicGroupMemberRequest(
    val userId: String,
)

@Serializable
data class AcademicGroupMembership(
    val groupId: String,
    val groupName: String,
    val userId: String,
    val joinedAtEpochSeconds: Long,
)
