package org.companerodeescuela.api.auth

import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.RegistrationAccountType
import org.companerodeescuela.shared.contracts.StaffApplicationStatus
import org.companerodeescuela.shared.contracts.StaffRole
import org.companerodeescuela.shared.contracts.UserRole
import java.time.Instant

/** Internal persistence command. The calling service must authorize current scope and step-up. */
data class AccountIdentityChange(
    val operationId: String,
    val actorId: String,
    val accountStatus: AccountStatus,
    val roles: Set<UserRole>,
    val institutionId: String?,
    val occurredAt: Instant,
    val scopeDigest: String? = null,
    val approvedApplicationId: String? = null,
    val capabilities: Set<org.companerodeescuela.shared.contracts.AdminCapability>? = null,
    val registrationIntent: RegistrationIntent? = null,
)

/** Immutable grant/state audit, deliberately excludes passwords, emails and tokens. */
data class AccountIdentityAudit(
    val operationId: String,
    val actorId: String,
    val fromRevision: Long,
    val toRevision: Long,
    val fromStatus: AccountStatus,
    val toStatus: AccountStatus,
    val rolesBefore: Set<UserRole>,
    val rolesAfter: Set<UserRole>,
    val institutionBefore: String?,
    val institutionAfter: String?,
    val occurredAt: Instant,
    val scopeDigest: String? = null,
    val approvedApplicationId: String? = null,
    val capabilitiesAfter: Set<org.companerodeescuela.shared.contracts.AdminCapability> = emptySet(),
    val registrationProfileBefore: RegistrationAccountType? = null,
    val registrationProfileAfter: RegistrationAccountType? = null,
)

internal const val MAX_IDENTITY_AUDIT_EVENTS = 1_000

/** Retries are bound to the original actor, revision and complete authority payload. */
internal fun PlatformAccount.isExactIdentityRetry(
    expectedRevision: Long,
    change: AccountIdentityChange,
): Boolean =
    identityAudit.any {
        it.operationId == change.operationId &&
            it.actorId == change.actorId &&
            it.fromRevision == expectedRevision &&
            it.toStatus == change.accountStatus &&
            it.rolesAfter == change.roles &&
            it.institutionAfter == change.institutionId &&
            it.scopeDigest == change.scopeDigest &&
            it.approvedApplicationId == change.approvedApplicationId &&
            (change.registrationIntent == null || it.registrationProfileAfter == change.registrationIntent.profile) &&
            (change.capabilities == null || it.capabilitiesAfter == change.capabilities)
    }

internal fun PlatformAccount.applyIdentityChange(
    expectedRevision: Long,
    change: AccountIdentityChange,
): PlatformAccount? {
    require(change.operationId.isNotBlank() && change.operationId.length <= 128)
    require(change.actorId.isNotBlank() && change.actorId.length <= 128)
    require(
        change.institutionId == null ||
            (
                change.institutionId.isNotBlank() &&
                    change.institutionId == change.institutionId.trim() &&
                    change.institutionId.length <= 128
            ),
    )
    if (expectedRevision < 0 ||
        expectedRevision != authRevision ||
        authRevision == Long.MAX_VALUE ||
        accountStatus == AccountStatus.REVOKED ||
        identityAudit.size >= MAX_IDENTITY_AUDIT_EVENTS ||
        identityAudit.any { it.operationId == change.operationId }
    ) {
        return null
    }
    // ACTIVE cannot move backwards into onboarding; a suspended pending identity may return to its pending state.
    if (accountStatus == AccountStatus.ACTIVE &&
        change.accountStatus in
        setOf(AccountStatus.PENDING_VERIFICATION, AccountStatus.PENDING_APPROVAL)
    ) {
        return null
    }
    if (change.scopeDigest != null && !Regex("[a-f0-9]{64}").matches(change.scopeDigest)) return null
    val nextRegistrationIntent = change.registrationIntent
    val pendingStudentOwnerInvite =
        accountStatus == AccountStatus.PENDING_VERIFICATION &&
            emailVerifiedAt == null &&
            roles.isEmpty() &&
            identityAudit.isEmpty() &&
            roleApplications.isEmpty() &&
            adminCapabilities.isEmpty()
    val verifiedStudentOwnerInvite =
        accountStatus == AccountStatus.ACTIVE &&
            emailVerifiedAt != null &&
            roles == setOf(UserRole.STUDENT) &&
            institutionId == null &&
            roleApplications.isEmpty() &&
            adminCapabilities.isEmpty() &&
            identityAudit.singleOrNull()?.let { audit ->
                audit.operationId.startsWith("email-verification-") &&
                    audit.actorId == id &&
                    audit.fromStatus == AccountStatus.PENDING_VERIFICATION &&
                    audit.toStatus == AccountStatus.ACTIVE &&
                    audit.rolesBefore.isEmpty() &&
                    audit.rolesAfter == setOf(UserRole.STUDENT) &&
                    audit.institutionBefore == null &&
                    audit.institutionAfter == null &&
                    audit.registrationProfileBefore == RegistrationAccountType.STUDENT &&
                    audit.registrationProfileAfter == RegistrationAccountType.STUDENT &&
                    audit.scopeDigest == null &&
                    audit.approvedApplicationId == null
            } == true
    if (nextRegistrationIntent != null &&
        (
            registrationIntent?.profile != RegistrationAccountType.STUDENT ||
                nextRegistrationIntent.profile != RegistrationAccountType.OWNER_INVITED ||
                nextRegistrationIntent.requestedInstitutionId != null ||
                nextRegistrationIntent.identityReference != null ||
                nextRegistrationIntent.preferredGroup != null ||
                change.scopeDigest == null ||
                !(pendingStudentOwnerInvite || verifiedStudentOwnerInvite) ||
                change.accountStatus != accountStatus ||
                change.roles != roles ||
                change.institutionId != institutionId
        )
    ) {
        return null
    }
    val rolesAfter = change.roles.toSet()
    val generations = roleGenerations.toMutableMap()
    for (role in (roles - rolesAfter) + (rolesAfter - roles)) {
        val previous = generations[role] ?: 0L
        if (previous < 0 || previous == Long.MAX_VALUE) return null
        generations[role] = previous + 1
    }
    val capabilitiesAfter =
        if (UserRole.ADMIN in
            rolesAfter
        ) {
            change.capabilities ?: adminCapabilities.takeIf { UserRole.ADMIN in roles }.orEmpty()
        } else {
            emptySet()
        }
    var applications = roleApplications
    if (change.approvedApplicationId != null) {
        if (change.scopeDigest == null ||
            change.accountStatus != AccountStatus.ACTIVE ||
            emailVerifiedAt == null ||
            applicationRevision == Long.MAX_VALUE
        ) {
            return null
        }
        applications = staffApplications()
        val application = applications.find { it.id == change.approvedApplicationId } ?: return null
        val role = if (application.role == StaffRole.TUTOR) UserRole.TUTOR else UserRole.TEACHER
        if (application.status != StaffApplicationStatus.PENDING ||
            role in roles ||
            role !in rolesAfter ||
            application.requestedInstitutionId != change.institutionId
        ) {
            return null
        }
        applications =
            applications.map {
                if (it.id == application.id) {
                    it.copy(
                        status = StaffApplicationStatus.APPROVED,
                        decidedBy = change.actorId,
                        decidedAtEpochSeconds = change.occurredAt.epochSecond,
                        decisionOperationId = change.operationId,
                        decisionDigest = change.scopeDigest,
                        grantedRoleGeneration = generations[role],
                    )
                } else {
                    it
                }
            }
    }
    val event =
        AccountIdentityAudit(
            change.operationId,
            change.actorId,
            authRevision,
            authRevision + 1,
            accountStatus,
            change.accountStatus,
            roles.toSet(),
            rolesAfter,
            institutionId,
            change.institutionId,
            change.occurredAt,
            change.scopeDigest,
            change.approvedApplicationId,
            capabilitiesAfter,
            registrationIntent?.profile,
            nextRegistrationIntent?.profile ?: registrationIntent?.profile,
        )
    return copy(
        active = change.accountStatus.permitsSession,
        accountStatus = change.accountStatus,
        roles = rolesAfter,
        institutionId = change.institutionId,
        authRevision = authRevision + 1,
        identityAudit = identityAudit + event,
        roleGenerations = generations,
        adminCapabilities = capabilitiesAfter,
        roleApplications = applications,
        registrationIntent = nextRegistrationIntent ?: registrationIntent,
        applicationRevision =
            if (change.approvedApplicationId !=
                null
            ) {
                applicationRevision + 1
            } else {
                applicationRevision
            },
    )
}
