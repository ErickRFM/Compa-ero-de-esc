package org.companerodeescuela.api.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.util.Base64
import java.util.UUID
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.identity.IdentityProvider
import org.companerodeescuela.api.integrations.identity.InstitutionalCredentials
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.api.institutions.InstitutionRepository
import org.companerodeescuela.api.institutions.InMemoryInstitutionRepository
import org.companerodeescuela.api.mail.UnavailableVerificationEmailGateway
import org.companerodeescuela.shared.contracts.LoginRequest
import org.companerodeescuela.shared.contracts.LoginResponse
import org.companerodeescuela.shared.contracts.RefreshSessionRequest
import org.companerodeescuela.shared.contracts.RegisterRequest
import org.companerodeescuela.shared.contracts.RegistrationAccountType
import org.companerodeescuela.shared.contracts.UserSummary

/**
 * Authentication use case.
 *
 * Compañero accounts are first-class and work without any institutional API.
 * Institutional identity remains an optional fallback for users who have not
 * created a native account yet.
 */
class AuthService(
    private val identityProvider: IdentityProvider,
    private val tokenService: AuthTokenService,
    private val sessions: RefreshSessionRepository,
    private val accounts: PlatformAccountRepository = InMemoryPlatformAccountRepository(),
    private val passwordHasher: PasswordHasher = PasswordHasher(),
    private val clock: Clock = Clock.systemUTC(),
    private val random: SecureRandom = SecureRandom(),
    private val institutions: InstitutionRepository = InMemoryInstitutionRepository(),
    private val verification: AccountVerificationService = AccountVerificationService(accounts, UnavailableVerificationEmailGateway),
) {
    private val authority = PlatformSessionAuthority(accounts, identityProvider, clock)
    suspend fun register(request: RegisterRequest): LoginResponse {
        val displayName = request.displayName.trim()
        val email = request.email.trim().lowercase()

        if (displayName.length !in 2..80 || displayName.any { it.isISOControl() }) {
            throw ApiException.Validation("Display name must contain between 2 and 80 characters")
        }
        if (!EMAIL_REGEX.matches(email) || email.length > 128 || email.substringBefore('@').length > 64 ||
            email.substringBefore('@').let { it.startsWith('.') || it.endsWith('.') || ".." in it } ||
            email.substringAfter('@').split('.').any { it.length !in 1..63 || it.startsWith('-') || it.endsWith('-') }) {
            throw ApiException.Validation("A valid email is required")
        }
        if (request.password.length !in 8..256 || request.password.any { it.isISOControl() }) {
            throw ApiException.Validation("Password must contain between 8 and 256 characters")
        }

        fun bounded(value: String?, maximum: Int, field: String): String? {
            val cleaned = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (cleaned.length > maximum || cleaned.any { it.isISOControl() }) throw ApiException.Validation("Invalid $field")
            return cleaned
        }
        val institutionPreference = bounded(request.requestedInstitutionId, 128, "institution")
        val reference = bounded(request.identityReference, 160, "identity reference")
        val groupPreference = bounded(request.preferredGroup, 128, "group preference")
        if (request.accountType in setOf(RegistrationAccountType.TEACHER, RegistrationAccountType.TUTOR) &&
            (institutionPreference == null || reference == null)) throw ApiException.Validation("Institution and identity reference are required")
        if (institutionPreference != null && authenticationDependency { institutions.findEnabled(institutionPreference) } == null)
            throw ApiException.Validation("Institution is not available for registration")
        if (request.accountType == RegistrationAccountType.PARTICIPANT &&
            (institutionPreference != null || reference != null || groupPreference != null)) throw ApiException.Validation("Participant registration does not request academic scope")
        val account = PlatformAccount(
            id = UUID.randomUUID().toString(),
            displayName = displayName,
            email = email,
            passwordHash = passwordHasher.hash(request.password),
            roles = emptySet(),
            accountStatus = AccountStatus.PENDING_VERIFICATION,
            registrationIntent = RegistrationIntent(request.accountType, institutionPreference, reference, groupPreference),
            createdAt = clock.instant(),
        )
        if (!authenticationDependency { accounts.create(account) }) {
            throw ApiException.Conflict("An account with that email already exists")
        }
        verification.issue(account.id)
        return createSession(account.toUserSummary(), SessionIdentitySource.NATIVE)
    }

    suspend fun verifyEmail(userId: String, token: String): LoginResponse =
        createSession(verification.verify(userId, token).toUserSummary(), SessionIdentitySource.NATIVE)

    suspend fun login(request: LoginRequest): LoginResponse {
        val username = request.username.trim()
        if (username.isBlank()) {
            throw ApiException.Validation("Username is required")
        }
        if (request.password.isEmpty()) {
            throw ApiException.Validation("Password is required")
        }

        val localAccount = authenticationDependency { accounts.findByIdentifier(username) }
        if (localAccount != null) {
            if (!localAccount.permitsSession || !passwordHasher.verify(request.password, localAccount.passwordHash)) {
                throw ApiException.Unauthorized("Invalid username or password")
            }
            return createSession(localAccount.toUserSummary(), SessionIdentitySource.NATIVE)
        }

        val institutional = authenticationDependency { identityProvider.authenticate(
            InstitutionalCredentials(
                username = username,
                password = request.password,
            ),
        ) } ?: throw ApiException.Unauthorized("Invalid username or password")

        return createSession(
            UserSummary(
                id = institutional.externalId,
                displayName = institutional.displayName,
                email = institutional.email,
                roles = institutional.roles,
                active = true,
            ),
            SessionIdentitySource.INSTITUTIONAL,
        )
    }

    suspend fun refresh(request: RefreshSessionRequest): LoginResponse {
        if (request.sessionId.isBlank() || request.refreshToken.isBlank()) {
            throw ApiException.Unauthorized()
        }
        val now = clock.instant()
        val session = authenticationDependency { sessions.find(request.sessionId) }
            ?.takeIf { it.revokedAt == null && it.expiresAt > now }
            ?: throw ApiException.Unauthorized()

        val updatedUser = authority.currentUser(session) ?: throw ApiException.Unauthorized()

        val nextRefreshToken = newRefreshToken()
        val rotated = authenticationDependency { sessions.rotate(
            sessionId = session.id,
            currentTokenHash = hashRefreshToken(request.refreshToken),
            nextTokenHash = hashRefreshToken(nextRefreshToken),
            user = updatedUser,
            now = now,
            expiresAt = now.plus(REFRESH_TOKEN_TTL),
        ) } ?: throw ApiException.Unauthorized()

        return response(rotated, nextRefreshToken)
    }

    suspend fun logout(request: RefreshSessionRequest) {
        if (request.sessionId.isBlank() || request.refreshToken.isBlank()) return
        authenticationDependency { sessions.revoke(
            sessionId = request.sessionId,
            presentedTokenHash = hashRefreshToken(request.refreshToken),
            now = clock.instant(),
        ) }
    }

    private suspend fun createSession(user: UserSummary, source: SessionIdentitySource): LoginResponse {
        val now = clock.instant()
        val sessionId = UUID.randomUUID().toString()
        val refreshToken = newRefreshToken()
        val pending = RefreshSession(
            id = sessionId,
            tokenHash = hashRefreshToken(refreshToken),
            user = user,
            createdAt = now,
            expiresAt = now.plus(REFRESH_TOKEN_TTL),
            identitySource = source,
        )
        val currentUser = authority.currentUser(pending) ?: throw ApiException.Unauthorized()
        val session = pending.copy(user = currentUser)
        authenticationDependency { sessions.create(session) }
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
        val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9\\-]+(?:\\.[A-Za-z0-9\\-]+)+$")
    }
}
