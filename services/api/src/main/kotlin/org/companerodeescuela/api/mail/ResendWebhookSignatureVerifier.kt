package org.companerodeescuela.api.mail

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal data class VerifiedResendWebhookEvent(
    val messageId: String,
    val type: String,
    val emailId: String?,
    val invitationId: String?,
    val createdAt: java.time.Instant,
    val receivedAt: java.time.Instant,
)

/** Verifies Resend's Svix signature against the exact raw request bytes. */
internal class ResendWebhookSignatureVerifier(
    signingSecret: CharArray,
    private val clock: Clock = Clock.systemUTC(),
    private val toleranceSeconds: Long = DEFAULT_TOLERANCE_SECONDS,
) {
    private val key: ByteArray = decodeSecret(signingSecret.concatToString())

    init {
        require(toleranceSeconds in 1..MAX_TOLERANCE_SECONDS)
    }

    fun verify(
        rawBody: ByteArray,
        messageId: String?,
        timestamp: String?,
        signatures: String?,
    ): Boolean {
        if (rawBody.isEmpty() || rawBody.size > MAX_BODY_BYTES) return false
        if (
            messageId.isNullOrBlank() ||
            messageId.length > 128 ||
            !messageId.matches(Regex("[A-Za-z0-9_-]{1,128}"))
        ) {
            return false
        }
        if (timestamp.isNullOrBlank() || timestamp.length > 12 || !timestamp.matches(Regex("[0-9]{1,12}"))) {
            return false
        }
        val timestampSeconds = timestamp.toLongOrNull() ?: return false
        val now = clock.instant().epochSecond
        if (timestampSeconds < now - toleranceSeconds || timestampSeconds > now + toleranceSeconds) return false
        if (signatures.isNullOrBlank() || signatures.length > MAX_SIGNATURE_HEADER_LENGTH) return false

        val prefix = (messageId + "." + timestamp + ".").toByteArray(StandardCharsets.UTF_8)
        val signedPayload = prefix + rawBody
        val expected = hmac(signedPayload)
        return signatures.split(' ').any { candidate ->
            if (!candidate.startsWith("v1,")) {
                false
            } else {
                val supplied = runCatching { Base64.getDecoder().decode(candidate.substring(3)) }.getOrNull()
                supplied != null && MessageDigest.isEqual(expected, supplied)
            }
        }
    }

    override fun toString() = "ResendWebhookSignatureVerifier(configured=true)"

    private fun hmac(payload: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(payload)
    }

    private companion object {
        const val DEFAULT_TOLERANCE_SECONDS = 300L
        const val MAX_TOLERANCE_SECONDS = 900L
        const val MAX_BODY_BYTES = 32 * 1024
        const val MAX_SIGNATURE_HEADER_LENGTH = 2_048

        fun decodeSecret(secret: String): ByteArray {
            require(secret.startsWith("whsec_")) { "Invalid Resend webhook signing secret" }
            val decoded =
                runCatching { Base64.getDecoder().decode(secret.removePrefix("whsec_")) }.getOrNull()
                    ?: throw IllegalArgumentException("Invalid Resend webhook signing secret")
            require(decoded.size == 32) { "Invalid Resend webhook signing secret" }
            return decoded
        }
    }
}
