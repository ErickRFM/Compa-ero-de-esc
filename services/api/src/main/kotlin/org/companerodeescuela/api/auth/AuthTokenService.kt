package org.companerodeescuela.api.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.Payload
import com.auth0.jwt.interfaces.JWTVerifier
import java.time.Clock
import java.time.Duration
import java.util.Date
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.shared.contracts.LoginResponse
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

    fun issue(user: UserSummary): LoginResponse {
        val issuedAt = clock.instant()
        val expiresAt = issuedAt.plus(ACCESS_TOKEN_TTL)

        val builder = JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject(user.id)
            .withIssuedAt(Date.from(issuedAt))
            .withExpiresAt(Date.from(expiresAt))
            .withClaim(CLAIM_DISPLAY_NAME, user.displayName)
            .withClaim(CLAIM_ROLES, user.roles.map(UserRole::name))
            .withClaim(CLAIM_ACTIVE, user.active)

        user.email?.let { builder.withClaim(CLAIM_EMAIL, it) }

        return LoginResponse(
            accessToken = builder.sign(algorithm),
            expiresAtEpochSeconds = expiresAt.epochSecond,
            user = user,
        )
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
            active = jwt.getClaim(CLAIM_ACTIVE).asBoolean() ?: true,
        )
    }

    companion object {
        const val PROVIDER_NAME = "auth-jwt"
        const val REALM = "companero-api"

        private val ACCESS_TOKEN_TTL: Duration = Duration.ofMinutes(15)
        private const val CLAIM_DISPLAY_NAME = "display_name"
        private const val CLAIM_EMAIL = "email"
        private const val CLAIM_ROLES = "roles"
        private const val CLAIM_ACTIVE = "active"
    }
}
