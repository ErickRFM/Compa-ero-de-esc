package org.companerodeescuela.api.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.Payload
import com.auth0.jwt.interfaces.JWTVerifier
import java.time.Clock
import java.time.Duration
import java.util.Date
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary

/**
 * Issues and verifies short-lived platform access tokens.
 *
 * Institutional credentials are never encoded into a token. The token carries
 * only the platform identity required to authorize API requests.
 */
class AuthTokenService(
    settings: ApiSettings,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val issuer = settings.jwtIssuer
    private val audience = settings.jwtAudience
    private val algorithm: Algorithm

    val verifier: JWTVerifier

    init {
        val secret = requireNotNull(settings.jwtSecret) {
            "JWT_SECRET must be configured before AuthTokenService is created"
        }
        require(secret.isNotEmpty()) { "JWT secret must not be empty" }

        algorithm = Algorithm.HMAC256(secret.concatToString())
        verifier = JWT.require(algorithm)
            .withIssuer(issuer)
            .withAudience(audience)
            .build()
    }

    fun issue(
        user: UserSummary,
        sessionId: String,
        sessionGeneration: Long = 0,
    ): IssuedAccessToken {
        val issuedAt = clock.instant()
        val expiresAt = issuedAt.plus(ACCESS_TOKEN_TTL)

        val builder = JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject(user.id)
            .withClaim(CLAIM_SESSION_ID, sessionId)
            .withClaim(CLAIM_SESSION_GENERATION, sessionGeneration)
            .withIssuedAt(Date.from(issuedAt))
            .withExpiresAt(Date.from(expiresAt))
            .withClaim(CLAIM_DISPLAY_NAME, user.displayName)
            .withClaim(CLAIM_ROLES, user.roles.map(UserRole::name))
            .withClaim(CLAIM_ACTIVE, user.active)
            .withClaim(CLAIM_ACCOUNT_STATUS, user.accountStatus.name)
            .withClaim(CLAIM_AUTH_REVISION, user.authRevision)

        user.email?.let { builder.withClaim(CLAIM_EMAIL, it) }
        user.institutionId?.let { builder.withClaim(CLAIM_INSTITUTION_ID, it) }

        return IssuedAccessToken(builder.sign(algorithm), expiresAt.epochSecond)
    }

    fun userFrom(jwt: Payload): UserSummary {
        val id = jwt.subject?.takeIf { it.isNotBlank() }
            ?: error("Verified token has no subject")
        val displayName = jwt.getClaim(CLAIM_DISPLAY_NAME).asString()
            ?.takeIf { it.isNotBlank() }
            ?: error("Verified token has no display name")
        val roles = jwt.getClaim(CLAIM_ROLES)
            .asList(String::class.java)
            .orEmpty()
            .mapNotNull { encoded ->
                runCatching { UserRole.valueOf(encoded) }.getOrNull()
            }
            .toSet()

        return UserSummary(
            id = id,
            displayName = displayName,
            email = jwt.getClaim(CLAIM_EMAIL).asString(),
            roles = roles,
            active = jwt.getClaim(CLAIM_ACTIVE).asBoolean() ?: false,
            accountStatus = if (jwt.getClaim(CLAIM_ACCOUNT_STATUS).isMissing) AccountStatus.ACTIVE
                else runCatching { AccountStatus.valueOf(jwt.getClaim(CLAIM_ACCOUNT_STATUS).asString()) }
                    .getOrDefault(AccountStatus.REVOKED),
            authRevision = if (jwt.getClaim(CLAIM_AUTH_REVISION).isMissing) 0L
                else nonNegativeIntegerClaim(jwt, CLAIM_AUTH_REVISION) ?: -1L,
            institutionId = jwt.getClaim(CLAIM_INSTITUTION_ID).asString(),
        )
    }

    fun sessionIdFrom(jwt: Payload): String? = jwt.getClaim(CLAIM_SESSION_ID)
        .asString()
        ?.takeIf(String::isNotBlank)

    fun sessionGenerationFrom(jwt: Payload): Long? =
        nonNegativeIntegerClaim(jwt, CLAIM_SESSION_GENERATION)

    private fun nonNegativeIntegerClaim(jwt: Payload, name: String): Long? {
        // Claim.asLong coerces fractions. Inspect JSON representation before conversion.
        val encoded = jwt.getClaim(name).toString()
        return encoded.takeIf { NON_NEGATIVE_INTEGER.matches(it) }?.toLongOrNull()
    }

    companion object {
        private val NON_NEGATIVE_INTEGER = Regex("0|[1-9][0-9]*")
        const val PROVIDER_NAME = "auth-jwt"
        const val REALM = "companero-api"

        private val ACCESS_TOKEN_TTL: Duration = Duration.ofMinutes(15)
        private const val CLAIM_SESSION_ID = "session_id"
        private const val CLAIM_SESSION_GENERATION = "session_generation"
        private const val CLAIM_DISPLAY_NAME = "display_name"
        private const val CLAIM_EMAIL = "email"
        private const val CLAIM_ROLES = "roles"
        private const val CLAIM_ACTIVE = "active"
        private const val CLAIM_ACCOUNT_STATUS = "account_status"
        private const val CLAIM_AUTH_REVISION = "auth_revision"
        private const val CLAIM_INSTITUTION_ID = "institution_id"
    }
}

data class IssuedAccessToken(
    val value: String,
    val expiresAtEpochSeconds: Long,
)
