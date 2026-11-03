package org.companerodeescuela.api.admin

import org.companerodeescuela.api.auth.PlatformAccount
import org.companerodeescuela.api.auth.PlatformAccountRepository
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary
import java.time.Clock
import java.time.Instant

/**
 * A projected SIII career scope is authorization data, never a user preference.
 *
 * Only a server-side repository populated by a verified, signed SIII integration
 * may provide these grants. Do not bind a client payload to [CareerGrantReader].
 * Until the integration is configured, an empty reader must deny all delegated
 * administration. This is intentionally separate from institution-wide ADMIN.
 */
data class VerifiedCareerGrant(
    val accountId: String,
    val institutionId: String,
    val programId: String,
    val validUntil: Instant?,
    val revokedAt: Instant?,
    /** Upper bound of freshness of a server-verified SIII authority snapshot. */
    val authorityFreshUntil: Instant,
)

fun interface CareerGrantReader {
    suspend fun grantsFor(accountId: String): List<VerifiedCareerGrant>
}

class CareerAdministrationScopePolicy(
    accounts: PlatformAccountRepository,
    private val careerGrants: CareerGrantReader,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val identity = InstitutionScopePolicy(accounts, clock)

    suspend fun require(
        actor: UserSummary,
        institutionId: String,
        programId: String,
    ): PlatformAccount {
        // Identity and session revision are checked against Mongo before scope.
        val current = identity.live(actor)
        if (!validIdentifier(institutionId) || !validIdentifier(programId)) {
            throw ApiException.Forbidden("A validated institution and career are required")
        }
        // Native owner authority is global only for the already-provisioned SUPER_ADMIN
        // role; the ordinary ADMIN role is never a career grant.
        if (UserRole.SUPER_ADMIN in current.roles) return current
        if (current.institutionId != institutionId || UserRole.COORDINATOR !in current.roles) {
            throw ApiException.Forbidden("Career administration is not assigned")
        }
        val now = clock.instant()
        val authorized =
            careerGrants.grantsFor(current.id).any { grant ->
                grant.accountId == current.id &&
                    grant.institutionId == institutionId &&
                    grant.programId == programId &&
                    grant.revokedAt == null &&
                    (grant.validUntil == null || grant.validUntil > now) &&
                    grant.authorityFreshUntil > now
            }
        if (!authorized) throw ApiException.Forbidden("Career authority is absent, expired, revoked or stale")
        return current
    }

    private fun validIdentifier(value: String): Boolean =
        value.isNotBlank() &&
            value == value.trim() &&
            value.length <= 128 &&
            value.none(Char::isISOControl)
}
