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
    val institutionId: String? = null,
    val periodId: String? = null,
    val startsAtEpochSeconds: Long? = null,
    val endsAtEpochSeconds: Long? = null,
    val tutorRoleGeneration: Long = -1,
    val groupScopeRevision: Long = -1,
)

@Serializable
data class CreateTutorAssignmentRequest(
    val tutorUserId: String,
    val academicGroupId: String,
    val institutionId: String? = null,
    val periodId: String? = null,
    val startsAtEpochSeconds: Long? = null,
    val endsAtEpochSeconds: Long? = null,
    val operationId: String? = null,
    val proof: String? = null,
    val groupScopeRevision: Long = 0,
) {
    override fun toString()="CreateTutorAssignmentRequest(tutorUserId=$tutorUserId, group=$academicGroupId, proof=[redacted])"
}

@Serializable
data class RevokeTutorAssignmentRequest(val operationId:String,val proof:String) {
    override fun toString()="RevokeTutorAssignmentRequest(operationId=$operationId, proof=[redacted])"
}

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
