package org.companerodeescuela.feature.admin

import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.shared.contracts.*

enum class AdminOperationKind {
    APPROVE_STAFF,
    AFFILIATE_STUDENT,
    LINK_SIII,
    SUSPEND_ACCOUNT,
    REVOKE_ACCOUNT,
    REACTIVATE_ACCOUNT,
    REVOKE_TUTOR_ASSIGNMENT,
    OTHER,
}

/** Operation description only; passwords, OTPs and proof bytes never enter UI state. */
data class AdminOperation(
    val operationId: String,
    val targetId: String,
    val action: StepUpAction,
    val targetName: String,
    val kind: AdminOperationKind = AdminOperationKind.OTHER,
)

data class AdminUiState(
    val loading: Boolean = true,
    val working: Boolean = false,
    val overview: AdministrationOverview? = null,
    val groups: List<AcademicGroupSummary> = emptyList(),
    val assignments: List<TutorAssignmentSummary> = emptyList(),
    val invitations: List<RegistrationInvitationSummary> = emptyList(),
    val invitationsLoading: Boolean = false,
    val invitationsFailure: AppError? = null,
    val auditEntries: List<AdminAuditEntry> = emptyList(),
    val auditLoading: Boolean = false,
    val auditFailure: AppError? = null,
    val operation: AdminOperation? = null,
    val failure: AppError? = null,
    val groupFailure: AppError? = null,
    val assignmentFailure: AppError? = null,
    val completedOperationId: String? = null,
    val ownerKey: String? = null,
)
