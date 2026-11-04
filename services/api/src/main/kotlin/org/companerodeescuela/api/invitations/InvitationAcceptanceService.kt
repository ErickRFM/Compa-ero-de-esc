package org.companerodeescuela.api.invitations

import org.companerodeescuela.api.auth.PlatformAccount
import org.companerodeescuela.api.auth.PlatformAccountRepository
import org.companerodeescuela.api.auth.authenticationDependency
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.AccountStatus
import java.time.Clock

/** This service is not exposed via HTTP. Any future route must use NativeActorResolver and rate limiting. */
internal class InvitationAcceptanceService(
    private val accounts: PlatformAccountRepository,
    private val invitations: InvitationRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun accept(
        actor: PlatformAccount,
        invitationId: String,
        rawCode: String,
        operationId: String,
    ): InvitationAcceptanceReceipt =
        authenticationDependency {
            if (!invitationId.matches(Regex("[A-Za-z0-9_-]{1,128}")) ||
                !rawCode.matches(Regex("[A-Za-z0-9_-]{43}")) ||
                !operationId.matches(Regex("[A-Za-z0-9_-]{1,128}"))
            ) {
                throw ApiException.Validation("Invitation is invalid or unavailable")
            }
            val current = accounts.findById(actor.id) ?: throw ApiException.Unauthorized()
            if (!current.permitsSession ||
                current.authRevision != actor.authRevision ||
                current.roles != actor.roles ||
                current.accountStatus != actor.accountStatus ||
                current.institutionId != actor.institutionId ||
                current.email != actor.email ||
                current.accountStatus !in setOf(AccountStatus.ACTIVE, AccountStatus.PENDING_APPROVAL)
            ) {
                throw ApiException.Unauthorized()
            }
            val now = clock.instant()
            if (current.emailVerifiedAt == null ||
                current.emailVerifiedAt > now
            ) {
                throw ApiException.Forbidden("Verified native identity is required")
            }
            val accepted =
                invitations.accept(invitationId, InvitationToken.hash(rawCode), current.email, true, current.id, operationId, now)
                    ?: throw ApiException.Validation("Invitation is invalid or unavailable")
            InvitationAcceptanceReceipt(accepted.id, accepted.status)
        }
}

internal data class InvitationAcceptanceReceipt(
    val invitationId: String,
    val status: InvitationStatus,
)
