package org.companerodeescuela.api.integrations.matica

import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
internal data class MaticaLinkClaims(
    val subject: String,
    val expiresAtEpochSeconds: Long,
    val nonce: String,
    val scope: String = "progress:read",
)

internal class MaticaLinkCodeVerifier(secret: ByteArray) {
    private val key = SecretKeySpec(secret.copyOf(), "HmacSHA256")
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()
    private val json = Json

    fun verify(code: String, now: Instant): MaticaLinkClaims? {
        return try {
            if (code.length !in 32..2048) return null
            val parts = code.split('.')
            if (parts.size != 2) return null
            val expected = sign(parts[0])
            val provided = decoder.decode(parts[1])
            if (!MessageDigest.isEqual(expected, provided)) return null
            val claims = json.decodeFromString(MaticaLinkClaims.serializer(), String(decoder.decode(parts[0])))
            val expiresIn = claims.expiresAtEpochSeconds - now.epochSecond
            if (claims.scope != "progress:read" || claims.expiresAtEpochSeconds <= now.epochSecond ||
                expiresIn > MAX_LINK_CODE_TTL_SECONDS || claims.nonce.length > 80
            ) return null
            java.util.UUID.fromString(claims.subject)
            java.util.UUID.fromString(claims.nonce)
            claims
        } catch (_: Exception) {
            null
        }
    }

    fun summarySignature(
        userId: String,
        timestamp: Long,
    ): String =
        encoder.encodeToString(
            sign("summary|$userId|$timestamp"),
        )

    private fun sign(payload: String): ByteArray =
        Mac.getInstance("HmacSHA256").run {
        init(key)
        doFinal(payload.toByteArray())
    }

    private companion object {
        const val MAX_LINK_CODE_TTL_SECONDS = 660L
    }
}
