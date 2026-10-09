package org.companerodeescuela.api.integrations.identity

import org.companerodeescuela.api.integrations.IntegrationProvider
import org.companerodeescuela.shared.contracts.UserRole

/** Credentials as the institution's identity system understands them. */
data class InstitutionalCredentials(
    val username: String,
    val password: String,
)

/**
 * The account the platform creates for an authenticated person.
 *
 * Note what is absent: no student number, no academic record, no national ID.
 * Identity answers "who is this", not "what is their academic situation".
 */
data class AuthenticatedAccount(
    val externalId: String,
    val displayName: String,
    val email: String?,
    val roles: Set<UserRole>,
    val mustChangePassword: Boolean = false,
)

/** Current upstream authorization state, independent of a previous login snapshot. */
data class InstitutionalIdentityState(
    val externalId: String,
    val active: Boolean,
    val roles: Set<UserRole>,
)

/**
 * Adapter for the institution's identity source (LDAP, Active Directory, an
 * SSO gateway, or a campus ID service).
 *
 * The platform delegates authentication rather than storing passwords. Whether
 * a given implementation performs a local bind check or a token exchange is an
 * adapter detail; callers only learn whether authentication succeeded.
 */
interface IdentityProvider : IntegrationProvider {

    /**
     * Verifies [credentials] and returns the corresponding account.
     *
     * Returns `null` for an unknown user and for a wrong password on purpose:
     * the caller must not be able to tell the two apart, otherwise the endpoint
     * becomes a user enumeration oracle.
     */
    suspend fun authenticate(credentials: InstitutionalCredentials): AuthenticatedAccount?

    /** Resolves roles for an already-authenticated user, used on token refresh. */
    suspend fun refreshRoles(externalId: String): Set<UserRole>

    /** Null means that current active identity cannot be verified; access must fail closed. */
    suspend fun currentState(externalId: String): InstitutionalIdentityState? = null
}
