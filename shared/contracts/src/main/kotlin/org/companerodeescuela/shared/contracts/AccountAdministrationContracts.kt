package org.companerodeescuela.shared.contracts

import kotlinx.serialization.Serializable

/**
 * Administrative capabilities authorized on accounts.
 *
 * Operational:
 * - APPROVE_STAFF: Enforced on staff approval endpoints.
 * - MANAGE_USERS: Enforced on user lifecycle (suspension/revocation).
 * - MANAGE_ACADEMICS: Enforced on academic group and schedule override mutations.
 * - DIRECTIVE_SUPERVISION: Enforced on directive overview and attendance auditing.
 *
 * Reserved (under development; no active endpoints or UI tools until product definition):
 * - REVIEW_WORKSHOPS: Workshop review tools.
 * - VIEW_AUDIT: System-wide audit log access.
 * - VIEW_SESSIONS: Active session inspection.
 */
@Serializable enum class AdminCapability {
    APPROVE_STAFF,
    MANAGE_USERS,
    MANAGE_ACADEMICS,
    REVIEW_WORKSHOPS,
    VIEW_AUDIT,
    VIEW_SESSIONS,
    DIRECTIVE_SUPERVISION,
}

@Serializable enum class StaffRole { TEACHER, TUTOR }

@Serializable enum class StaffApplicationStatus { PENDING, APPROVED, REJECTED }

@Serializable data class StaffApplication(
    val id: String,
    val role: StaffRole,
    val requestedInstitutionId: String,
    val identityReference: String,
    val preferredGroup: String? = null,
    val requestedAtEpochSeconds: Long,
    val status: StaffApplicationStatus = StaffApplicationStatus.PENDING,
    val decidedBy: String? = null,
    val decidedAtEpochSeconds: Long? = null,
    val decisionOperationId: String? = null,
    val decisionDigest: String? = null,
    val grantedRoleGeneration: Long? = null,
) {
    override fun toString() = "StaffApplication(id=$id, role=$role, status=$status, reference=[redacted])"
}

@Serializable data class TutorApplicationRequest(
    val operationId: String,
    val expectedAuthRevision: Long,
    val institutionId: String,
    val identityReference: String,
    val preferredGroup: String? = null,
)

@Serializable data class TutorApprovalScope(
    val academicGroupId: String,
    val periodId: String,
    val startsAtEpochSeconds: Long,
    val endsAtEpochSeconds: Long,
    val groupScopeRevision: Long = 0,
)

@Serializable data class ApproveStaffRequest(
    val operationId: String,
    val expectedAuthRevision: Long,
    val applicationId: String,
    val institutionId: String,
    val tutorScopes: List<TutorApprovalScope> = emptyList(),
    val proof: String,
) {
    override fun toString() = "ApproveStaffRequest(operationId=$operationId, applicationId=$applicationId, proof=[redacted])"
}

@Serializable data class AccountLifecycleRequest(
    val operationId: String,
    val expectedAuthRevision: Long,
    val status: AccountStatus,
    val proof: String,
) {
    override fun toString() = "AccountLifecycleRequest(operationId=$operationId, status=$status, proof=[redacted])"
}

@Serializable data class AdminUserSummary(
    val user: UserSummary,
    val applications: List<StaffApplication>,
    val capabilities: Set<AdminCapability> = emptySet(),
)

@Serializable data class AdministrationMetrics(
    val totalUsers: Int,
    val pendingStaff: Int,
    val activeTeachers: Int,
    val activeTutors: Int,
    val suspended: Int,
)

@Serializable data class AdministrationOverview(
    val users: List<AdminUserSummary>,
    val metrics: AdministrationMetrics,
)
