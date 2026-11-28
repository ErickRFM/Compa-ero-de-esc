package org.companerodeescuela.feature.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.network.MfaRepository
import org.companerodeescuela.shared.contracts.AccountLifecycleRequest
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.ApproveStaffRequest
import org.companerodeescuela.shared.contracts.ApproveStudentAffiliationRequest
import org.companerodeescuela.shared.contracts.AuthorizeRegistrationInvitationRequest
import org.companerodeescuela.shared.contracts.InstitutionalIdentityKind
import org.companerodeescuela.shared.contracts.InstitutionalIdentityLinkDigest
import org.companerodeescuela.shared.contracts.InstitutionalIdentityLinkRequest
import org.companerodeescuela.shared.contracts.InstitutionalIdentityLinkRevokeRequest
import org.companerodeescuela.shared.contracts.MfaStepUpRequest
import org.companerodeescuela.shared.contracts.PrepareRegistrationInvitationRetryRequest
import org.companerodeescuela.shared.contracts.PrivilegedCommandDigests
import org.companerodeescuela.shared.contracts.RegistrationAccountType
import org.companerodeescuela.shared.contracts.RegistrationInvitationSummary
import org.companerodeescuela.shared.contracts.RetryRegistrationInvitationRequest
import org.companerodeescuela.shared.contracts.RevokeTutorAssignmentRequest
import org.companerodeescuela.shared.contracts.StaffApplicationStatus
import org.companerodeescuela.shared.contracts.StaffRole
import org.companerodeescuela.shared.contracts.StepUpAction
import org.companerodeescuela.shared.contracts.StepUpProof
import org.companerodeescuela.shared.contracts.TutorApprovalScope
import org.companerodeescuela.shared.contracts.UserRole
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

internal fun isValidInstitutionalExternalReference(value: String): Boolean =
    value.isNotBlank() && value == value.trim() && value.length <= 256 && value.none(Char::isISOControl)

@HiltViewModel
class AdminViewModel
    @Inject
    constructor(
        private val repository: AdminRepository,
        private val mfa: MfaRepository,
        private val clock: Clock,
    ) : ViewModel() {
        private val mutable = MutableStateFlow(AdminUiState())
        val state: StateFlow<AdminUiState> = mutable.asStateFlow()
        private var owner: String? = null
        private var generation = 0L
        private var loadJob: Job? = null
        private var actionJob: Job? = null
        private var command: Command? = null
        private var proof: StepUpProof? = null

        private sealed interface Command {
            val target: String
            val operationId: String
            val action: StepUpAction
            val digest: String
            val resourceId: String get() = target

            data class Approval(
                override val target: String,
                val body: ApproveStaffRequest,
            ) : Command {
                override val operationId get() = body.operationId
                override val action = StepUpAction.APPROVE_STAFF
                override val digest get() = PrivilegedCommandDigests.staffApproval(target, body)
            }

            data class Affiliation(
                override val target: String,
                val body: ApproveStudentAffiliationRequest,
            ) : Command {
                override val operationId get() = body.operationId
                override val action = StepUpAction.MANAGE_ACADEMICS
                override val digest get() = PrivilegedCommandDigests.studentAffiliation(target, body)
            }

            data class IdentityLink(
                override val target: String,
                val body: InstitutionalIdentityLinkRequest,
            ) : Command {
                override val operationId get() = body.operationId
                override val action = StepUpAction.MANAGE_ACADEMICS
                override val resourceId get() = body.institutionId
                override val digest get() = InstitutionalIdentityLinkDigest.command(body)
            }

            data class IdentityUnlink(
                override val target: String,
                val body: InstitutionalIdentityLinkRevokeRequest,
            ) : Command {
                override val operationId get() = body.operationId
                override val action = StepUpAction.MANAGE_ACADEMICS
                override val resourceId get() = body.institutionId
                override val digest get() = InstitutionalIdentityLinkDigest.revoke(body)
            }

            data class Lifecycle(
                override val target: String,
                val body: AccountLifecycleRequest,
            ) : Command {
                override val operationId get() = body.operationId
                override val action = StepUpAction.ACCOUNT_LIFECYCLE
                override val digest get() = PrivilegedCommandDigests.accountLifecycle(target, body)
            }

            data class Revocation(
                override val target: String,
                val body: RevokeTutorAssignmentRequest,
            ) : Command {
                override val operationId get() = body.operationId
                override val action = StepUpAction.MANAGE_ACADEMICS
                override val digest get() = PrivilegedCommandDigests.tutorRevocation(target, body)
            }

            data class InvitationRetry(
                override val target: String,
                override val operationId: String,
                override val resourceId: String,
                override val digest: String,
            ) : Command {
                override val action = StepUpAction.ADMIN_INVITE
            }

            data class InvitationAuthorization(
                override val target: String,
                val body: AuthorizeRegistrationInvitationRequest,
            ) : Command {
                override val operationId get() = body.operationId
                override val action = StepUpAction.ADMIN_AUTHORITY
                override val digest get() = PrivilegedCommandDigests.invitationAuthorization(target, body)
            }
        }

        init {
            viewModelScope.launch {
                mfa.observeOwner().collect { next ->
                    generation++
                    owner = next
                    loadJob?.cancel()
                    actionJob?.cancel()
                    command = null
                    proof = null
                    mutable.value =
                        AdminUiState(
                            loading = next != null,
                            failure = if (next == null) AppError.Http(401) else null,
                            ownerKey = next,
                        )
                    if (next != null) refresh()
                }
            }
        }

        private fun current(
            captured: Long,
            capturedOwner: String?,
        ) = captured == generation && capturedOwner != null && capturedOwner == owner

        fun refresh() {
            if (owner == null) return
            generation++
            loadJob?.cancel()
            actionJob?.cancel()
            command = null
            proof = null
            val captured = generation
            val capturedOwner = owner
            mutable.value = mutable.value.copy(loading = true, working = false, operation = null, failure = null)
            loadJob =
                viewModelScope.launch {
                    val overview = repository.overview()
                    if (!current(captured, capturedOwner)) return@launch
                    if (overview is Outcome.Failure) {
                        mutable.value = AdminUiState(loading = false, failure = overview.error)
                        return@launch
                    }
                    val groups = repository.approvalGroups()
                    if (!current(captured, capturedOwner)) return@launch
                    val assignments = repository.assignments()
                    if (!current(captured, capturedOwner)) return@launch
                    mutable.value =
                        mutable.value.copy(
                            loading = false,
                            overview = (overview as Outcome.Success).value,
                            groups = (groups as? Outcome.Success)?.value.orEmpty(),
                            groupFailure = (groups as? Outcome.Failure)?.error,
                            assignments = (assignments as? Outcome.Success)?.value.orEmpty(),
                            assignmentFailure = (assignments as? Outcome.Failure)?.error,
                        )
                }
        }

        private fun ready() = !mutable.value.loading && !mutable.value.working && owner != null && mutable.value.overview != null

        private fun invalid() {
            mutable.value = mutable.value.copy(failure = AppError.Http(422))
        }

        private fun prepare(
            next: Command,
            name: String,
        ) {
            command = next
            proof = null
            val kind =
                when (next) {
                    is Command.Approval -> AdminOperationKind.APPROVE_STAFF
                    is Command.Affiliation -> AdminOperationKind.AFFILIATE_STUDENT
                    is Command.IdentityLink -> AdminOperationKind.LINK_SIII
                    is Command.IdentityUnlink -> AdminOperationKind.UNLINK_SIII
                    is Command.Revocation -> AdminOperationKind.REVOKE_TUTOR_ASSIGNMENT
                    is Command.InvitationRetry -> AdminOperationKind.RETRY_INVITATION
                    is Command.InvitationAuthorization -> AdminOperationKind.AUTHORIZE_INVITATION
                    is Command.Lifecycle ->
                        when (next.body.status) {
                            AccountStatus.SUSPENDED -> AdminOperationKind.SUSPEND_ACCOUNT
                            AccountStatus.REVOKED -> AdminOperationKind.REVOKE_ACCOUNT
                            AccountStatus.ACTIVE -> AdminOperationKind.REACTIVATE_ACCOUNT
                            else -> AdminOperationKind.OTHER
                        }
                }
            mutable.value =
                mutable.value.copy(
                    operation = AdminOperation(next.operationId, next.target, next.action, name, kind),
                    failure = null,
                    completedOperationId = null,
                )
        }

        fun prepareApproval(
            userId: String,
            applicationId: String,
            groupIds: Set<String> = emptySet(),
        ) = prepareApprovalWithValidity(userId, applicationId, groupIds, null)

        fun prepareApprovalWithValidity(
            userId: String,
            applicationId: String,
            groupIds: Set<String>,
            validityDays: Int?,
        ) {
            if (!ready() || command != null) return
            if (validityDays != null && validityDays !in 1..36500) return invalid()
            val target =
                mutable.value.overview
                    ?.users
                    ?.find { it.user.id == userId } ?: return invalid()
            val application =
                target.applications.find {
                    it.id == applicationId && it.status == StaffApplicationStatus.PENDING
                } ?: return invalid()
            if (!target.user.emailVerified ||
                target.user.accountStatus !in setOf(AccountStatus.PENDING_APPROVAL, AccountStatus.ACTIVE)
            ) {
                return invalid()
            }
            val scopes = mutableListOf<TutorApprovalScope>()
            if (application.role == StaffRole.TUTOR) {
                if (groupIds.isEmpty() || groupIds.size > 20) return invalid()
                for (id in groupIds.sorted()) {
                    val group = mutable.value.groups.find { it.id == id } ?: return invalid()
                    val start = group.startsAtEpochSeconds ?: return invalid()
                    val end = group.endsAtEpochSeconds ?: return invalid()
                    val period = group.periodId ?: return invalid()
                    val validFrom = maxOf(start, clock.instant().epochSecond)
                    if (!group.active ||
                        group.institutionId != application.requestedInstitutionId ||
                        group.scopeRevision < 0 ||
                        period.isBlank() ||
                        validFrom >= end
                    ) {
                        return invalid()
                    }
                    val validUntil = validityDays?.let { minOf(end, validFrom + it * 86400L) } ?: end
                    scopes += TutorApprovalScope(id, period, validFrom, validUntil, group.scopeRevision)
                }
            } else if (groupIds.isNotEmpty() || validityDays != null) {
                return invalid()
            }
            prepare(
                Command.Approval(
                    userId,
                    ApproveStaffRequest(
                        UUID.randomUUID().toString(),
                        target.user.authRevision,
                        applicationId,
                        application.requestedInstitutionId,
                        scopes,
                        "",
                    ),
                ),
                target.user.displayName,
            )
        }

        fun prepareLifecycle(
            userId: String,
            status: AccountStatus,
        ) {
            if (!ready() || command != null) return
            val target =
                mutable.value.overview
                    ?.users
                    ?.find { it.user.id == userId } ?: return invalid()
            val canRestore = target.user.accountStatus == AccountStatus.SUSPENDED && status == AccountStatus.ACTIVE
            if (target.user.accountStatus == AccountStatus.REVOKED ||
                (!canRestore && status !in setOf(AccountStatus.SUSPENDED, AccountStatus.REVOKED))
            ) {
                return invalid()
            }
            prepare(
                Command.Lifecycle(userId, AccountLifecycleRequest(UUID.randomUUID().toString(), target.user.authRevision, status, "")),
                target.user.displayName,
            )
        }

        fun prepareStudentAffiliation(
            userId: String,
            institutionId: String,
        ) {
            if (!ready() || command != null) return
            val target =
                mutable.value.overview
                    ?.users
                    ?.find { it.user.id == userId } ?: return invalid()
            if (target.requestedInstitutionId != institutionId ||
                target.user.institutionId != null ||
                !target.user.emailVerified ||
                target.user.accountStatus != AccountStatus.ACTIVE ||
                target.user.roles != setOf(UserRole.STUDENT) ||
                target.user.registrationAccountType != RegistrationAccountType.STUDENT
            ) {
                return invalid()
            }
            prepare(
                Command.Affiliation(
                    userId,
                    ApproveStudentAffiliationRequest(UUID.randomUUID().toString(), target.user.authRevision, institutionId, ""),
                ),
                target.user.displayName,
            )
        }

        fun prepareInstitutionalLink(
            userId: String,
            externalId: String,
        ) {
            if (!ready() || command != null) return
            val target =
                mutable.value.overview
                    ?.users
                    ?.find { it.user.id == userId }
                    ?.user ?: return invalid()
            val institution = target.institutionId ?: return invalid()
            val reference = externalId
            if (!isValidInstitutionalExternalReference(reference) ||
                !target.emailVerified ||
                target.accountStatus != AccountStatus.ACTIVE ||
                target.roles.any { it == UserRole.ADMIN || it == UserRole.SUPER_ADMIN }
            ) {
                return invalid()
            }
            val kind =
                when {
                    UserRole.TEACHER in target.roles -> InstitutionalIdentityKind.TEACHER
                    UserRole.STUDENT in target.roles -> InstitutionalIdentityKind.STUDENT
                    else -> return invalid()
                }
            prepare(
                Command.IdentityLink(
                    userId,
                    InstitutionalIdentityLinkRequest(institution, kind, reference, userId, UUID.randomUUID().toString(), ""),
                ),
                target.displayName + " · " + reference,
            )
        }

        /** Revokes a wrong SIII link (audit A3); the server checks the link, permission and step-up again. */
        fun prepareInstitutionalUnlink(
            userId: String,
            externalId: String,
            reason: String,
        ) {
            if (!ready() || command != null) return
            val target =
                mutable.value.overview
                    ?.users
                    ?.find { it.user.id == userId }
                    ?.user ?: return invalid()
            val institution = target.institutionId ?: return invalid()
            val reference = externalId
            val cleanReason = reason.trim()
            if (!isValidInstitutionalExternalReference(reference) || cleanReason.length !in 3..500) return invalid()
            val kind =
                when {
                    UserRole.TEACHER in target.roles -> InstitutionalIdentityKind.TEACHER
                    UserRole.STUDENT in target.roles -> InstitutionalIdentityKind.STUDENT
                    else -> return invalid()
                }
            prepare(
                Command.IdentityUnlink(
                    userId,
                    InstitutionalIdentityLinkRevokeRequest(institution, kind, reference, cleanReason, UUID.randomUUID().toString(), ""),
                ),
                target.displayName + " · " + reference,
            )
        }

        fun prepareRevocation(id: String) {
            if (!ready() || command != null) return
            val assignment = mutable.value.assignments.find { it.id == id && it.active } ?: return invalid()
            prepare(Command.Revocation(id, RevokeTutorAssignmentRequest(UUID.randomUUID().toString(), "")), assignment.academicGroupName)
        }

        fun cancelOperation() {
            generation++
            actionJob?.cancel()
            command = null
            proof = null
            mutable.value = mutable.value.copy(operation = null, working = false, failure = null)
        }

        fun authorize(
            password: String,
            otp: String,
        ) {
            val pending = command ?: return
            val canExecute =
                if (pending is Command.InvitationRetry || pending is Command.InvitationAuthorization) {
                    !mutable.value.working && owner != null
                } else {
                    ready()
                }
            if (!canExecute) return
            val cached = proof?.takeIf { it.expiresAtEpochSeconds > clock.instant().epochSecond }
            if (cached == null && (password.length !in 1..128 || otp.length != 6 || otp.any { it !in '0'..'9' })) return invalid()
            val captured = generation
            val capturedOwner = owner
            mutable.value = mutable.value.copy(working = true, failure = null)
            actionJob =
                viewModelScope.launch {
                    val evidence =
                        if (cached !=
                            null
                        ) {
                            Outcome.Success(cached)
                        } else {
                            mfa.stepUp(
                                MfaStepUpRequest(password, otp, pending.action, pending.resourceId, pending.operationId, pending.digest),
                            )
                        }
                    if (!current(captured, capturedOwner) || command !== pending) return@launch
                    if (evidence is Outcome.Failure) {
                        proof = null
                        mutable.value = mutable.value.copy(working = false, failure = evidence.error)
                        return@launch
                    }
                    val actual = (evidence as Outcome.Success).value
                    if (actual.expiresAtEpochSeconds <= clock.instant().epochSecond) {
                        proof = null
                        mutable.value = mutable.value.copy(working = false, failure = AppError.Http(403))
                        return@launch
                    }
                    proof = actual
                    val result: Outcome<Any> =
                        when (pending) {
                            is Command.Affiliation -> repository.affiliateStudent(pending.target, pending.body.copy(proof = actual.proof))
                            is Command.IdentityLink -> repository.linkInstitutionalIdentity(pending.body.copy(proof = actual.proof))
                            is Command.IdentityUnlink -> repository.revokeInstitutionalIdentity(pending.body.copy(proof = actual.proof))
                            is Command.Approval -> repository.approve(pending.target, pending.body.copy(proof = actual.proof))
                            is Command.Lifecycle -> repository.lifecycle(pending.target, pending.body.copy(proof = actual.proof))
                            is Command.Revocation -> repository.revokeAssignment(pending.target, pending.body.copy(proof = actual.proof))
                            is Command.InvitationRetry ->
                                repository.retryInvitation(
                                    RetryRegistrationInvitationRequest(
                                        invitationId = pending.target,
                                        operationId = pending.operationId,
                                        proof = actual.proof,
                                    ),
                                )
                            is Command.InvitationAuthorization ->
                                repository.authorizeInvitation(pending.body.copy(proof = actual.proof))
                        }
                    if (!current(captured, capturedOwner) || command !== pending) return@launch
                    when (result) {
                        is Outcome.Success -> {
                            command = null
                            proof = null
                            if (pending is Command.InvitationRetry) {
                                mutable.value =
                                    mutable.value.copy(
                                        working = false,
                                        operation = null,
                                        failure = null,
                                        completedOperationId = pending.operationId,
                                    )
                                refreshInvitations()
                                refreshAudit()
                            } else if (pending is Command.InvitationAuthorization) {
                                mutable.value =
                                    mutable.value.copy(
                                        loading = true,
                                        working = false,
                                        operation = null,
                                        failure = null,
                                        completedOperationId = pending.operationId,
                                    )
                                refresh()
                                refreshInvitations()
                                refreshAudit()
                            } else {
                                mutable.value =
                                    mutable.value.copy(
                                        loading = true,
                                        working = false,
                                        operation = null,
                                        failure = null,
                                        completedOperationId = pending.operationId,
                                    )
                                refresh()
                            }
                        }
                        is Outcome.Failure -> {
                            // Keep evidence only for an uncertain transport/server result of this exact immutable command.
                            if ((result.error as? AppError.Http)?.status?.let { it < 500 } == true) proof = null
                            mutable.value = mutable.value.copy(working = false, failure = result.error)
                        }
                    }
                }
        }

        fun refreshAudit() {
            val capturedOwner = owner ?: return
            val captured = generation
            mutable.value = mutable.value.copy(auditLoading = true, auditFailure = null)
            viewModelScope.launch {
                val result = repository.auditLog()
                if (!current(captured, capturedOwner)) return@launch
                mutable.value =
                    when (result) {
                        is Outcome.Success ->
                            mutable.value.copy(
                                auditEntries = result.value,
                                auditLoading = false,
                                auditFailure = null,
                            )
                        is Outcome.Failure ->
                            mutable.value.copy(
                                auditLoading = false,
                                auditFailure = result.error,
                            )
                    }
            }
        }

        fun refreshInvitations() {
            val capturedOwner = owner ?: return
            val captured = generation
            mutable.value = mutable.value.copy(invitationsLoading = true, invitationsFailure = null)
            viewModelScope.launch {
                val result = repository.invitations()
                if (!current(captured, capturedOwner)) return@launch
                mutable.value =
                    when (result) {
                        is Outcome.Success ->
                            mutable.value.copy(
                                invitations = result.value,
                                invitationsLoading = false,
                                invitationsFailure = null,
                            )
                        is Outcome.Failure ->
                            mutable.value.copy(
                                invitationsLoading = false,
                                invitationsFailure = result.error,
                            )
                    }
            }
        }

        fun prepareInvitationRetry(
            invitationId: String,
            recipient: String,
        ) {
            val capturedOwner = owner ?: return
            val captured = generation
            if (mutable.value.working || command != null) return
            val operationId = UUID.randomUUID().toString()
            mutable.value = mutable.value.copy(working = true, invitationsFailure = null, failure = null, completedOperationId = null)
            actionJob =
                viewModelScope.launch {
                    val prepared =
                        repository.prepareInvitationRetry(
                            PrepareRegistrationInvitationRetryRequest(
                                invitationId = invitationId,
                                operationId = operationId,
                            ),
                        )
                    if (!current(captured, capturedOwner)) return@launch
                    when (prepared) {
                        is Outcome.Success -> {
                            mutable.value = mutable.value.copy(working = false)
                            prepare(
                                Command.InvitationRetry(
                                    target = invitationId,
                                    operationId = prepared.value.operationId,
                                    resourceId = prepared.value.resourceId,
                                    digest = prepared.value.payloadDigest,
                                ),
                                recipient,
                            )
                        }
                        is Outcome.Failure -> {
                            mutable.value = mutable.value.copy(working = false, invitationsFailure = prepared.error)
                        }
                    }
                }
        }

        fun prepareInvitationAuthorization(
            invitation: RegistrationInvitationSummary,
            groupIds: Set<String> = emptySet(),
            validityDays: Int? = null,
        ) {
            val capturedOwner = owner ?: return
            val captured = generation
            if (mutable.value.working || command != null || invitation.status != "ACCEPTED") return
            val targetId = invitation.acceptedByAccountId ?: return invalid()
            if (validityDays != null && validityDays !in 1..36500) return invalid()

            mutable.value = mutable.value.copy(working = true, invitationsFailure = null, failure = null, completedOperationId = null)
            actionJob =
                viewModelScope.launch {
                    val currentOverview =
                        mutable.value.overview ?: when (val loaded = repository.overview()) {
                            is Outcome.Success -> loaded.value
                            is Outcome.Failure -> {
                                if (current(captured, capturedOwner)) {
                                    mutable.value = mutable.value.copy(working = false, invitationsFailure = loaded.error)
                                }
                                return@launch
                            }
                        }
                    val currentGroups =
                        if (invitation.profile == "TUTOR" && mutable.value.groups.isEmpty()) {
                            (repository.approvalGroups() as? Outcome.Success)?.value.orEmpty()
                        } else {
                            mutable.value.groups
                        }
                    if (!current(captured, capturedOwner)) return@launch

                    val target = currentOverview.users.find { it.user.id == targetId }
                    if (target == null || !target.user.emailVerified) {
                        mutable.value = mutable.value.copy(working = false, invitationsFailure = AppError.Http(422))
                        return@launch
                    }

                    val scopes = mutableListOf<TutorApprovalScope>()
                    if (invitation.profile == "TUTOR") {
                        if (groupIds.isEmpty() || groupIds.size > 20) {
                            mutable.value = mutable.value.copy(working = false, invitationsFailure = AppError.Http(422))
                            return@launch
                        }
                        for (id in groupIds.sorted()) {
                            val group = currentGroups.find { it.id == id }
                            val start = group?.startsAtEpochSeconds
                            val end = group?.endsAtEpochSeconds
                            val period = group?.periodId
                            val validFrom = start?.let { maxOf(it, clock.instant().epochSecond) }
                            if (group == null ||
                                start == null ||
                                end == null ||
                                period.isNullOrBlank() ||
                                validFrom == null ||
                                !group.active ||
                                group.institutionId != invitation.institutionId ||
                                group.scopeRevision < 0 ||
                                validFrom >= end
                            ) {
                                mutable.value = mutable.value.copy(working = false, invitationsFailure = AppError.Http(422))
                                return@launch
                            }
                            val validUntil = validityDays?.let { minOf(end, validFrom + it * 86400L) } ?: end
                            scopes += TutorApprovalScope(id, period, validFrom, validUntil, group.scopeRevision)
                        }
                    } else if (groupIds.isNotEmpty() || validityDays != null) {
                        mutable.value = mutable.value.copy(working = false, invitationsFailure = AppError.Http(422))
                        return@launch
                    }

                    mutable.value = mutable.value.copy(working = false, overview = currentOverview, groups = currentGroups)
                    prepare(
                        Command.InvitationAuthorization(
                            target = invitation.invitationId,
                            body =
                                AuthorizeRegistrationInvitationRequest(
                                    operationId = UUID.randomUUID().toString(),
                                    invitationId = invitation.invitationId,
                                    expectedAuthRevision = target.user.authRevision,
                                    institutionId = invitation.institutionId,
                                    profile = invitation.profile,
                                    tutorScopes = scopes,
                                    proof = "",
                                ),
                        ),
                        "${target.user.displayName} (${invitation.recipient})",
                    )
                }
        }

        fun revokeInvitation(invitationId: String) {
            val capturedOwner = owner ?: return
            val captured = generation
            if (mutable.value.working) return
            mutable.value = mutable.value.copy(working = true, invitationsFailure = null)
            viewModelScope.launch {
                val result = repository.revokeInvitation(invitationId)
                if (!current(captured, capturedOwner)) return@launch
                when (result) {
                    is Outcome.Success -> {
                        mutable.value = mutable.value.copy(working = false, completedOperationId = invitationId)
                        refreshInvitations()
                    }
                    is Outcome.Failure -> {
                        mutable.value = mutable.value.copy(working = false, invitationsFailure = result.error)
                    }
                }
            }
        }
    }
