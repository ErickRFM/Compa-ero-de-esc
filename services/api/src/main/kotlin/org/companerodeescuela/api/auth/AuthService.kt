package org.companerodeescuela.api.auth

import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.identity.IdentityProvider
import org.companerodeescuela.api.integrations.identity.InstitutionalCredentials
import org.companerodeescuela.shared.contracts.LoginRequest
import org.companerodeescuela.shared.contracts.LoginResponse
import org.companerodeescuela.shared.contracts.RefreshSessionRequest
import org.companerodeescuela.shared.contracts.UserSummary
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.util.Base64
import java.util.UUID

/**
 * Authentication use case.
 *
 * Passwords exist only for the duration of this call and are delegated to the
 * institutional adapter. They are never persisted by the platform.
 */
class AuthService(
    private val identityProvider: IdentityProvider,
    private val tokenService: AuthTokenService,
    private val sessions: RefreshSessionRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val random: SecureRandom = SecureRandom(),
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
        return createSession(user)
    }

    suspend fun refresh(request: RefreshSessionRequest): LoginResponse {
        if (request.sessionId.isBlank() || request.refreshToken.isBlank()) {
            throw ApiException.Unauthorized()
        }
        val now = clock.instant()
        val session = sessions.find(request.sessionId)
            ?.takeIf { it.revokedAt == null && it.expiresAt > now }
            ?: throw ApiException.Unauthorized()
        val updatedUser = session.user.copy(
            roles = identityProvider.refreshRoles(session.user.id),
        )
        val nextRefreshToken = newRefreshToken()
        val rotated = sessions.rotate(
            sessionId = session.id,
            currentTokenHash = hashRefreshToken(request.refreshToken),
            nextTokenHash = hashRefreshToken(nextRefreshToken),
            user = updatedUser,
            now = now,
            expiresAt = now.plus(REFRESH_TOKEN_TTL),
        ) ?: throw ApiException.Unauthorized()

        return response(rotated, nextRefreshToken)
    }

    suspend fun logout(request: RefreshSessionRequest) {
        if (request.sessionId.isBlank() || request.refreshToken.isBlank()) return
        sessions.revoke(
            sessionId = request.sessionId,
            presentedTokenHash = hashRefreshToken(request.refreshToken),
            now = clock.instant(),
        )
    }

    private suspend fun createSession(user: UserSummary): LoginResponse {
        val now = clock.instant()
        val sessionId = UUID.randomUUID().toString()
        val refreshToken = newRefreshToken()
        val session = RefreshSession(
            id = sessionId,
            tokenHash = hashRefreshToken(refreshToken),
            user = user,
            createdAt = now,
            expiresAt = now.plus(REFRESH_TOKEN_TTL),
        )
        sessions.create(session)
        return response(session, refreshToken)
    }

    private fun response(session: RefreshSession, refreshToken: String): LoginResponse {
        val accessToken = tokenService.issue(session.user, session.id, session.generation)
        return LoginResponse(
            accessToken = accessToken.value,
            expiresAtEpochSeconds = accessToken.expiresAtEpochSeconds,
            sessionId = session.id,
            refreshToken = refreshToken,
            user = session.user,
        )
    }

    private fun newRefreshToken(): String {
        val bytes = ByteArray(REFRESH_TOKEN_BYTES)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hashRefreshToken(token: String): String = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(token.toByteArray()))

    private companion object {
        val REFRESH_TOKEN_TTL: Duration = Duration.ofDays(30)
        const val REFRESH_TOKEN_BYTES = 32
    }
}
