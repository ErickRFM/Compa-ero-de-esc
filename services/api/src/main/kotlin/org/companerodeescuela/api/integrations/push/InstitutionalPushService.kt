package org.companerodeescuela.api.integrations.push

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.companerodeescuela.shared.contracts.InstitutionalEventOperation
import org.companerodeescuela.shared.contracts.InstitutionalPushEvent
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant

data class InstitutionalEventRecord(
    val tenantId: String,
    val sourceCredentialId: String,
    val event: InstitutionalPushEvent,
    val payloadSha256: String,
    val receivedAt: Instant,
)

enum class InstitutionalEventAcceptance { ACCEPTED, DUPLICATE, CONFLICT, STALE }

interface InstitutionalEventRepository {
    suspend fun accept(record: InstitutionalEventRecord): InstitutionalEventAcceptance
}

sealed interface InstitutionalPushResult {
    data class Accepted(
        val eventId: String,
        val receivedAt: Instant,
        val duplicate: Boolean,
    ) : InstitutionalPushResult

    data object Invalid : InstitutionalPushResult

    data object Forbidden : InstitutionalPushResult

    data object Conflict : InstitutionalPushResult

    data object Stale : InstitutionalPushResult
}

class InstitutionalPushService(
    private val repository: InstitutionalEventRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val strictJson =
        Json {
            isLenient = false
            ignoreUnknownKeys = false
            explicitNulls = false
            coerceInputValues = false
        }

    suspend fun accept(
        institution: VerifiedInstitution,
        rawBody: ByteArray,
    ): InstitutionalPushResult {
        if (rawBody.size !in 1..MAX_BODY_BYTES) return InstitutionalPushResult.Invalid
        val event =
            try {
                strictJson.decodeFromString<InstitutionalPushEvent>(decodeUtf8(rawBody))
            } catch (_: SerializationException) {
                return InstitutionalPushResult.Invalid
            } catch (_: java.nio.charset.CharacterCodingException) {
                return InstitutionalPushResult.Invalid
            } catch (_: IllegalArgumentException) {
                return InstitutionalPushResult.Invalid
            }
        if (!isValid(event)) return InstitutionalPushResult.Invalid
        if (event.entityType + ":write" !in institution.scopes && "academic:*:write" !in institution.scopes) {
            return InstitutionalPushResult.Forbidden
        }

        val receivedAt = clock.instant()
        val record =
            InstitutionalEventRecord(
                tenantId = institution.tenantId,
                sourceCredentialId = institution.credentialId,
                event = event,
                payloadSha256 = sha256(rawBody),
                receivedAt = receivedAt,
            )
        return when (repository.accept(record)) {
            InstitutionalEventAcceptance.ACCEPTED -> InstitutionalPushResult.Accepted(event.eventId, receivedAt, duplicate = false)
            InstitutionalEventAcceptance.DUPLICATE -> InstitutionalPushResult.Accepted(event.eventId, receivedAt, duplicate = true)
            InstitutionalEventAcceptance.CONFLICT -> InstitutionalPushResult.Conflict
            InstitutionalEventAcceptance.STALE -> InstitutionalPushResult.Stale
        }
    }

    private fun isValid(event: InstitutionalPushEvent): Boolean {
        if (event.schemaVersion != CURRENT_SCHEMA_VERSION) return false
        if (event.eventId.length !in 1..MAX_EVENT_ID_LENGTH || !SAFE_IDENTIFIER.matches(event.eventId)) return false
        if (event.externalId.length !in 1..MAX_EXTERNAL_ID_LENGTH || event.externalId.any(Char::isISOControl)) return false
        if (event.entityType !in InstitutionalPushAuthenticator.ALLOWED_ENTITY_TYPES) return false
        if (event.sourceVersion < 1 || event.occurredAtEpochSeconds < 0) return false
        if (event.occurredAtEpochSeconds > clock.instant().plusSeconds(MAX_FUTURE_EVENT_SKEW_SECONDS).epochSecond) return false
        if (event.operation == InstitutionalEventOperation.DELETE && event.payload.isNotEmpty()) return false
        if (event.operation == InstitutionalEventOperation.UPSERT && event.payload.isEmpty()) return false
        return true
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    internal fun decodeUtf8(bytes: ByteArray): String =
        Charsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val MAX_BODY_BYTES = 1_048_576
        const val MAX_EVENT_ID_LENGTH = 128
        const val MAX_EXTERNAL_ID_LENGTH = 256
        const val MAX_FUTURE_EVENT_SKEW_SECONDS = 900L
        private val SAFE_IDENTIFIER = Regex("[A-Za-z0-9._:-]{1,128}")
    }
}
