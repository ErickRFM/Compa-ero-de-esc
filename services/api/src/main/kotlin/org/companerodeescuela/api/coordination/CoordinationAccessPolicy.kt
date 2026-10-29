package org.companerodeescuela.api.coordination

import org.companerodeescuela.api.admin.InstitutionScopePolicy
import org.companerodeescuela.api.auth.PlatformAccount
import org.companerodeescuela.api.auth.PlatformAccountRepository
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.AdminCapability
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary
import java.time.Clock

/** Read-only supervision uses current native authority, never a login-card intention. */
class CoordinationAccessPolicy(
    accounts: PlatformAccountRepository,
    clock: Clock = Clock.systemUTC(),
) {
    private val identity = InstitutionScopePolicy(accounts, clock)

    suspend fun requireOverview(actor: UserSummary): PlatformAccount {
        val current = identity.live(actor)
        val institution = current.institutionId
        if (institution.isNullOrBlank() ||
            institution != institution.trim() ||
            institution.length > 128 ||
            institution.any(Char::isISOControl)
        ) {
            throw ApiException.Forbidden("Institution linkage required")
        }
        val coordinator = UserRole.COORDINATOR in current.roles
        val direction =
            UserRole.PRINCIPAL in current.roles &&
                AdminCapability.DIRECTIVE_SUPERVISION in current.adminCapabilities
        if (!coordinator && !direction) {
            throw ApiException.Forbidden("Institutional supervision is not granted")
        }
        return current
    }
}
