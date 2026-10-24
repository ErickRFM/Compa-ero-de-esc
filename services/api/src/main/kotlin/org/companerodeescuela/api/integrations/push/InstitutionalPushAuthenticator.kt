package org.companerodeescuela.api.integrations.push

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class IntegrationCredential(
    val keyId: String,
    val tenantId: String,
    val secret: ByteArray,
    val scopes: Set<String>,
    val enabled: Boolean,
    val expiresAtEpochSeconds: Long?,
)

data class SignedIntegrationRequest(
    val keyId: String,
    val timestamp: String,
    val nonce: String,
    val signature: String,
    val method: String,
    val path: String,
    val body: ByteArray,
)

data class VerifiedInstitution(
    val credentialId: String,
    val tenantId: String,
    val scopes: Set<String>,
)

interface IntegrationCredentialRepository {
    suspend fun findCredential(keyId: String): IntegrationCredential?
}

interface IntegrationNonceRepository {
    /** Atomically returns false when this credential has already used the nonce. */
    suspend fun reserveNonce(
        credentialId: String,
        nonce: String,
        expiresAt: Instant,
    ): Boolean
}

class InstitutionalPushAuthenticator(
    private val credentials: IntegrationCredentialRepository,
    private val nonces: IntegrationNonceRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val allowedClockSkewSeconds: Long = DEFAULT_ALLOWED_CLOCK_SKEW_SECONDS,
) {
    init {
        require(allowedClockSkewSeconds in 1..MAX_ALLOWED_CLOCK_SKEW_SECONDS)
    }

    suspend fun authenticate(request: SignedIntegrationRequest): VerifiedInstitution? {
        if (!KEY_ID.matches(request.keyId) || !NONCE.matches(request.nonce)) return null
        val timestamp = request.timestamp.toLongOrNull() ?: return null
        val now = clock.instant().epochSecond
        if (timestamp < now - allowedClockSkewSeconds || timestamp > now + allowedClockSkewSeconds) return null
        if (request.method != "POST" || request.path !in AUTHENTICATED_PATHS) return null

        val credential = credentials.findCredential(request.keyId) ?: return null
        if (!credential.enabled || credential.keyId != request.keyId || credential.tenantId.isBlank()) return null
        if (credential.expiresAtEpochSeconds?.let { it <= now } == true) return null
        if (credential.secret.size < MIN_SECRET_BYTES) return null

        val suppliedSignature = request.signature.removePrefix("v1=")
        if (!HEX_SIGNATURE.matches(suppliedSignature)) return null
        val expected =
            hex(
                hmacSha256(
                    credential.secret,
                    canonicalMessage(
                        request.method,
                        request.path,
                        request.timestamp,
                        request.nonce,
                        request.body,
                    ),
                ),
            )
        val matches =
            MessageDigest.isEqual(
                expected.toByteArray(StandardCharsets.US_ASCII),
                suppliedSignature.lowercase().toByteArray(StandardCharsets.US_ASCII),
            )
        if (!matches) return null

        val reserved =
            nonces.reserveNonce(
                credentialId = credential.keyId,
                nonce = request.nonce,
                expiresAt = clock.instant().plusSeconds(allowedClockSkewSeconds),
            )
        if (!reserved) return null
        return VerifiedInstitution(credential.keyId, credential.tenantId, credential.scopes)
    }

    companion object {
        const val EVENTS_PATH = "/integration/v1/events"
        const val PROJECTION_STATUS_PATH = "/integration/v1/projection-status"
        private val AUTHENTICATED_PATHS = setOf(EVENTS_PATH, PROJECTION_STATUS_PATH)
        const val DEFAULT_ALLOWED_CLOCK_SKEW_SECONDS = 300L
        const val MAX_ALLOWED_CLOCK_SKEW_SECONDS = 600L
        const val MIN_SECRET_BYTES = 32
        val ALLOWED_ENTITY_TYPES =
            setOf(
                "student",
                "teacher",
                "course",
                "group",
                "enrollment",
                "schedule",
                "occurrence",
                "assignment",
                "event",
                "announcement",
            )
        private val KEY_ID = Regex("[A-Za-z0-9_-]{1,64}")
        private val NONCE = Regex("[A-Za-z0-9_-]{16,128}")
        private val HEX_SIGNATURE = Regex("(?:v1=)?[A-Fa-f0-9]{64}")

        fun canonicalMessage(
            method: String,
            path: String,
            timestamp: String,
            nonce: String,
            body: ByteArray,
        ): ByteArray {
            val bodyHash = hex(MessageDigest.getInstance("SHA-256").digest(body))
            return "$method\n$path\n$timestamp\n$nonce\n$bodyHash".toByteArray(StandardCharsets.UTF_8)
        }

        fun sign(
            secret: ByteArray,
            canonicalMessage: ByteArray,
        ): String = "v1=" + hex(hmacSha256(secret, canonicalMessage))

        private fun hmacSha256(
            secret: ByteArray,
            message: ByteArray,
        ): ByteArray =
            Mac.getInstance("HmacSHA256").run {
                init(SecretKeySpec(secret, "HmacSHA256"))
                doFinal(message)
            }

        private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    }
}
