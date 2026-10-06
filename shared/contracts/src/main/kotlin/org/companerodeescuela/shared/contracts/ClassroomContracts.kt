package org.companerodeescuela.shared.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ClassroomStatus {
    @SerialName("active") ACTIVE,
    @SerialName("archived") ARCHIVED,
}

@Serializable
enum class ClassroomMemberRole {
    @SerialName("student") STUDENT,
    @SerialName("teacher") TEACHER,
}

@Serializable
data class ClassroomSummary(
    val id: String,
    val name: String,
    val description: String? = null,
    val room: String? = null,
    val teacherId: String,
    val teacherDisplayName: String,
    val status: ClassroomStatus = ClassroomStatus.ACTIVE,
    val canManage: Boolean = false,
    val joinedAtEpochSeconds: Long? = null,
)

@Serializable
data class CreateClassroomRequest(
    val name: String,
    val description: String? = null,
    val room: String? = null,
)

@Serializable
data class ClassroomMembership(
    val classroomId: String,
    val userId: String,
    val role: ClassroomMemberRole,
    val joinedAtEpochSeconds: Long,
)

@Serializable
data class CreateClassInviteRequest(
    val ttlMinutes: Int = 30,
    val maxUses: Int = 100,
)

@Serializable
data class ClassInvite(
    val id: String,
    val classroomId: String,
    val code: String,
    val expiresAtEpochSeconds: Long,
    val maxUses: Int,
    val uses: Int,
)

@Serializable
data class JoinClassInviteRequest(
    val code: String,
)

@Serializable
data class JoinClassInviteResponse(
    val classroom: ClassroomSummary,
    val membership: ClassroomMembership,
)
