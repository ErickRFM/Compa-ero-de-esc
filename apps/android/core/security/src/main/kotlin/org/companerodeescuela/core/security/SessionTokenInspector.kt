package org.companerodeescuela.core.security

import java.time.Clock
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonArray
import org.companerodeescuela.shared.contracts.UserRole

data class PlatformSessionClaims(
    val userId: String,
    val displayName: String?,
    val roles: Set<UserRole>,
    val expiresAtEpochSeconds: Long,
    val sessionId: String? = null,
)

/**
 * Reads the non-secret claims of our own JWT so the client can enforce local
 * expiry and scope cached academic data to the active account.
 *
 * This is not token verification. The API remains responsible for signature
 * verification and authorization. A locally malformed token is simply treated
 * as no session.
 */
object SessionTokenInspector {
    fun inspect(token: String): PlatformSessionClaims? = runCatching {
        val parts = token.split('.')
        require(parts.size == 3)

        val payload = String(
            Base64.getUrlDecoder().decode(padBase64(parts[1])),
            Charsets.UTF_8,
        )
        val json = Json.parseToJsonElement(payload).jsonObject
        val userId = json["sub"]?.jsonPrimitive?.contentOrNull
            ?.takeIf(String::isNotBlank)
            ?: return null
        val sessionId = json["session_id"]?.jsonPrimitive?.contentOrNull
            ?.takeIf(String::isNotBlank)
        val expiresAt = json["exp"]?.jsonPrimitive?.longOrNull ?: return null
        val displayName = json["display_name"]?.jsonPrimitive?.contentOrNull
            ?.takeIf(String::isNotBlank)
        val roles = json["roles"]
            ?.jsonArray
            ?.mapNotNull { entry ->
                entry.jsonPrimitive.contentOrNull
                    ?.let { encoded -> runCatching { UserRole.valueOf(encoded.uppercase()) }.getOrNull() }
            }
            ?.toSet()
            .orEmpty()

        PlatformSessionClaims(
            userId = userId,
            displayName = displayName,
            roles = roles,
            expiresAtEpochSeconds = expiresAt,
            sessionId = sessionId,
        )
    }.getOrNull()

    fun isUsable(
        token: String?,
        clock: Clock = Clock.systemUTC(),
        clockSkewSeconds: Long = 15,
    ): Boolean {
        if (token.isNullOrBlank()) return false
        val claims = inspect(token) ?: return false
        return claims.expiresAtEpochSeconds > clock.instant().epochSecond + clockSkewSeconds
    }

    private fun padBase64(value: String): String {
        val missing = (4 - value.length % 4) % 4
        return value + "=".repeat(missing)
    }
}
