package org.companerodeescuela.api.admin

import java.time.Clock
import org.companerodeescuela.api.auth.PlatformAccount
import org.companerodeescuela.api.auth.PlatformAccountRepository
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.AdminCapability
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary

/**
 * Direction (PRINCIPAL) authority policy.
 *
 * Enforces live server verification for Principal role operations:
 * - Requires active account with email verification and verified session.
 * - Enforces exact institution scope matching (no cross-tenant access).
 * - Requires explicit capability grant in adminCapabilities.
 */
class DirectionAccessPolicy(
    private val accounts: PlatformAccountRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val scopePolicy = InstitutionScopePolicy(accounts, clock)

    suspend fun requireDirectionScope(
        actor: UserSummary,
        requiredCapability: AdminCapability,
        targetInstitutionId: String,
    ): PlatformAccount {
        val current = scopePolicy.live(actor)

        if (targetInstitutionId.isBlank() || targetInstitutionId != targetInstitutionId.trim()) {
            throw ApiException.Forbidden("A valid institution scope is required")
        }

        if (UserRole.PRINCIPAL !in current.roles && UserRole.SUPER_ADMIN !in current.roles) {
            throw ApiException.Forbidden("Principal direction authority is required")
        }

        if (UserRole.SUPER_ADMIN !in current.roles) {
            if (current.institutionId != targetInstitutionId) {
                throw ApiException.Forbidden("Access to another institution scope is forbidden")
            }
            if (requiredCapability !in current.adminCapabilities) {
                throw ApiException.Forbidden("Direction capability ${requiredCapability.name} is not granted")
            }
        }

        return current
    }
}
