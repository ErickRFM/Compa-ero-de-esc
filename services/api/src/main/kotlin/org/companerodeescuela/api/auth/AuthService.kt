package org.companerodeescuela.api.auth

import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.identity.IdentityProvider
import org.companerodeescuela.api.integrations.identity.InstitutionalCredentials
import org.companerodeescuela.shared.contracts.LoginRequest
import org.companerodeescuela.shared.contracts.LoginResponse
import org.companerodeescuela.shared.contracts.UserSummary

/**
 * Authentication use case.
 *
 * Passwords exist only for the duration of this call and are delegated to the
 * institutional adapter. They are never persisted by the platform.
 */
class AuthService(
    private val identityProvider: IdentityProvider,
    private val tokenService: AuthTokenService,
) {
    suspend fun login(request: LoginRequest): LoginResponse {
        val username = request.username.trim()
        if (username.isBlank()) {
            throw ApiException.Validation("Username is required")
        }
        if (request.password.isBlank()) {
            throw ApiException.Validation("Password is required")
        }

        val account = identityProvider.authenticate(
            InstitutionalCredentials(
                username = username,
                password = request.password,
            ),
        ) ?: throw ApiException.Unauthorized("Invalid username or password")

        val user = UserSummary(
            id = account.externalId,
            displayName = account.displayName,
            email = account.email,
            roles = account.roles,
            active = true,
        )
        return tokenService.issue(user)
    }
}
