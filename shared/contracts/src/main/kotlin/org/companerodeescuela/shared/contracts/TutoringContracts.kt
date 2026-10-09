package org.companerodeescuela.shared.contracts

import kotlinx.serialization.Serializable

@Serializable
data class TutorAssignmentSummary(
    val id: String,
    val tutorUserId: String,
    val academicGroupId: String,
    val academicGroupName: String,
    val active: Boolean,
    val assignedBy: String,
    val assignedAtEpochSeconds: Long,
    val revokedAtEpochSeconds: Long? = null,
    val revokedBy: String? = null,
)

@Serializable
data class CreateTutorAssignmentRequest(
    val tutorUserId: String,
    val academicGroupId: String,
)

@Serializable
data class TutorStudentSummary(
    val userId: String,
    val academicGroupId: String,
    val displayName: String? = null,
    val verifiedPlatformStudent: Boolean = false,
)

@Serializable
data class TutorScopeSummary(
    val tutorUserId: String,
    val groups: List<AcademicGroupSummary>,
)
