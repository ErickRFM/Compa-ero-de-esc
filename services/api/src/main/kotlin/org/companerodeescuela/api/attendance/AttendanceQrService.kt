package org.companerodeescuela.api.attendance

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.AttendanceQrResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionStatus

sealed interface QrEvidenceResult {
    data class Valid(
        val issuedAtEpochSeconds: Long,
        val expiresAtEpochSeconds: Long,
    ) : QrEvidenceResult

    data class Expired(
        val issuedAtEpochSeconds: Long,
        val expiresAtEpochSeconds: Long,
    ) : QrEvidenceResult

    data object WrongSession : QrEvidenceResult
    data object Invalid : QrEvidenceResult
}

class AttendanceQrService(
    secret: CharArray,
    private val repository: AttendanceRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    private val keyBytes = secret.concatToString().toByteArray(StandardCharsets.UTF_8)

    init {
        require(keyBytes.size >= 32) { "Attendance QR signing secret must be at least 32 bytes" }
    }

    suspend fun issue(
        actorId: String,
        sessionId: String,
        allowCrossOwner: Boolean = false,
    ): AttendanceQrResponse {
        val session = repository.findSession(sessionId)
            ?: throw ApiException.NotFound("Attendance session was not found")
        if (!allowCrossOwner && session.openedBy != actorId) {
            throw ApiException.Forbidden("This attendance session belongs to another teacher")
        }

        val now = clock.instant().epochSecond
        if (
            session.status != AttendanceSessionStatus.OPEN ||
            session.closesAtEpochSeconds <= now
        ) {
            throw ApiException.Conflict("Attendance session is closed")
        }

        val expiresAt = minOf(
            now + TOKEN_TTL.seconds,
            session.closesAtEpochSeconds,
        )
        val nonceBytes = ByteArray(NONCE_BYTES).also(secureRandom::nextBytes)
        val nonce = encoder.encodeToString(nonceBytes)
        val prefix = listOf(
            TOKEN_VERSION,
            encodeText(sessionId),
            nonce,
            now.toString(),
            expiresAt.toString(),
        ).joinToString(".")
        val signature = encoder.encodeToString(sign(prefix))

        return AttendanceQrResponse(
            token = "$prefix.$signature",
            issuedAtEpochSeconds = now,
            expiresAtEpochSeconds = expiresAt,
            rotateAfterSeconds = ROTATE_AFTER.seconds,
        )
    }

    fun verify(
        token: String,
        expectedSessionId: String,
        receivedAtEpochSeconds: Long = clock.instant().epochSecond,
    ): QrEvidenceResult {
        val parts = token.split('.')
        if (parts.size != TOKEN_PARTS || parts[0] != TOKEN_VERSION) {
            return QrEvidenceResult.Invalid
        }

        val issuedAt = parts[3].toLongOrNull() ?: return QrEvidenceResult.Invalid
        val expiresAt = parts[4].toLongOrNull() ?: return QrEvidenceResult.Invalid
        if (
            expiresAt <= issuedAt ||
            expiresAt - issuedAt > TOKEN_TTL.seconds ||
            issuedAt > receivedAtEpochSeconds + MAX_CLOCK_SKEW_SECONDS
        ) {
            return QrEvidenceResult.Invalid
        }

        val prefix = parts.take(TOKEN_PARTS - 1).joinToString(".")
        val supplied = runCatching { decoder.decode(parts.last()) }.getOrNull()
            ?: return QrEvidenceResult.Invalid
        if (!MessageDigest.isEqual(sign(prefix), supplied)) {
            return QrEvidenceResult.Invalid
        }

        val sessionId = decodeText(parts[1]) ?: return QrEvidenceResult.Invalid
        if (sessionId != expectedSessionId) {
            return QrEvidenceResult.WrongSession
        }

        return if (receivedAtEpochSeconds > expiresAt) {
            QrEvidenceResult.Expired(issuedAt, expiresAt)
        } else {
            QrEvidenceResult.Valid(issuedAt, expiresAt)
        }
    }

    private fun sign(value: String): ByteArray =
        Mac.getInstance(HMAC_ALGORITHM).run {
            init(SecretKeySpec(keyBytes, HMAC_ALGORITHM))
            doFinal(value.toByteArray(StandardCharsets.UTF_8))
        }

    private fun encodeText(value: String): String =
        encoder.encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeText(value: String): String? =
        runCatching {
            String(decoder.decode(value), StandardCharsets.UTF_8)
                .takeIf(String::isNotBlank)
        }.getOrNull()

    companion object {
        private const val TOKEN_VERSION = "v1"
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private const val NONCE_BYTES = 16
        private const val TOKEN_PARTS = 6
        private const val MAX_CLOCK_SKEW_SECONDS = 5L
        private val TOKEN_TTL: Duration = Duration.ofSeconds(25)
        private val ROTATE_AFTER: Duration = Duration.ofSeconds(15)
        private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
        private val decoder: Base64.Decoder = Base64.getUrlDecoder()
    }
}
