package org.companerodeescuela.api.auth

import java.time.Instant
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.UserRole

/** Internal persistence command. The calling service must authorize current scope and step-up. */
data class AccountIdentityChange(
    val operationId: String,
    val actorId: String,
    val accountStatus: AccountStatus,
    val roles: Set<UserRole>,
    val institutionId: String?,
    val occurredAt: Instant,
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
)
internal const val MAX_IDENTITY_AUDIT_EVENTS = 1_000

/** Retries are bound to the original actor, revision and complete authority payload. */
internal fun PlatformAccount.isExactIdentityRetry(expectedRevision: Long, change: AccountIdentityChange): Boolean =
    identityAudit.any {
        it.operationId == change.operationId && it.actorId == change.actorId &&
            it.fromRevision == expectedRevision && it.toStatus == change.accountStatus &&
            it.rolesAfter == change.roles && it.institutionAfter == change.institutionId
    }

internal fun PlatformAccount.applyIdentityChange(expectedRevision: Long, change: AccountIdentityChange): PlatformAccount? {
    require(change.operationId.isNotBlank() && change.operationId.length <= 128)
    require(change.actorId.isNotBlank() && change.actorId.length <= 128)
    require(change.institutionId == null ||
        (change.institutionId.isNotBlank() && change.institutionId == change.institutionId.trim() && change.institutionId.length <= 128))
    if (expectedRevision < 0 || expectedRevision != authRevision || authRevision == Long.MAX_VALUE ||
        accountStatus == AccountStatus.REVOKED || identityAudit.size >= MAX_IDENTITY_AUDIT_EVENTS ||
        identityAudit.any { it.operationId == change.operationId }) return null
    // ACTIVE cannot move backwards into onboarding; a suspended pending identity may return to its pending state.
    if (accountStatus == AccountStatus.ACTIVE && change.accountStatus in
        setOf(AccountStatus.PENDING_VERIFICATION, AccountStatus.PENDING_APPROVAL)) return null
    val rolesAfter = change.roles.toSet()
    val event = AccountIdentityAudit(change.operationId, change.actorId, authRevision, authRevision + 1,
        accountStatus, change.accountStatus, roles.toSet(), rolesAfter, institutionId, change.institutionId, change.occurredAt)
    return copy(active = change.accountStatus.permitsSession, accountStatus = change.accountStatus,
        roles = rolesAfter, institutionId = change.institutionId, authRevision = authRevision + 1,
        identityAudit = identityAudit + event)
}
