package org.companerodeescuela.api.invitations

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.Locale

/** An invitation is a request, never a role grant. No HTTP or email adapter calls this prototype yet. */
internal enum class InvitationProfile { STUDENT, TEACHER, TUTOR, PARTICIPANT, INSTITUTION_ADMIN, EXTERNAL_GUEST }

internal enum class InvitationStatus { CREATED, SENT, ACCEPTED, ACTIVE, FAILED, EXPIRED, REVOKED }

internal class InvitationToken private constructor(
    val value: String,
    val hash: String,
) {
    override fun toString() = "InvitationToken([redacted])"

    companion object {
        fun generate(random: SecureRandom = SecureRandom()): InvitationToken {
            val bytes = ByteArray(32).also(random::nextBytes)
            return try {
                val value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
                InvitationToken(value, hash(value))
            } finally {
                bytes.fill(0)
            }
        }

        fun hash(value: String): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)),
            )
    }
}

internal data class InvitationRecord(
    val id: String,
    val recipient: String,
    val institutionId: String,
    val profile: InvitationProfile,
    val issuerId: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    val status: InvitationStatus,
    val tokenHash: String,
    val acceptedBy: String? = null,
    val acceptedOperation: String? = null,
    val acceptedAt: Instant? = null,
    val revokedAt: Instant? = null,
) {
    override fun toString() = "InvitationRecord(id=$id, profile=$profile, status=$status, recipient=[redacted], token=[redacted])"
}

internal fun InvitationRecord.canonicalForStorage(): InvitationRecord =
    copy(createdAt = createdAt.truncatedTo(ChronoUnit.MILLIS), expiresAt = expiresAt.truncatedTo(ChronoUnit.MILLIS))

internal fun InvitationRecord.validForCreation(): Boolean =
    id.isNotBlank() &&
        id.length <= 128 &&
        id.none(Char::isISOControl) &&
        issuerId.isNotBlank() &&
        issuerId.length <= 128 &&
        issuerId.none(Char::isISOControl) &&
        institutionId.isNotBlank() &&
        institutionId.length <= 128 &&
        institutionId.none(Char::isISOControl) &&
        status in setOf(InvitationStatus.CREATED, InvitationStatus.SENT) &&
        recipient == recipient.trim().lowercase(Locale.ROOT) &&
        recipient.matches(Regex("[^\\s@]+@[^\\s@]+")) &&
        recipient.length <= 128 &&
        recipient.none(Char::isISOControl) &&
        tokenHash.matches(Regex("[A-Za-z0-9_-]{43}")) &&
        expiresAt > createdAt &&
        expiresAt <= createdAt.plusSeconds(30 * 86400L) &&
        acceptedBy == null &&
        acceptedOperation == null &&
        acceptedAt == null &&
        revokedAt == null

internal fun validAcceptanceInput(
    emailVerified: Boolean,
    accountId: String,
    operationId: String,
): Boolean =
    emailVerified &&
        accountId.isNotBlank() &&
        accountId.length <= 128 &&
        accountId.none(Char::isISOControl) &&
        operationId.isNotBlank() &&
        operationId.length <= 128 &&
        operationId.none(Char::isISOControl)

/** Identity and email verification must be resolved server-side before calling accept. */
internal interface InvitationRepository {
    suspend fun create(record: InvitationRecord): Boolean

    suspend fun find(id: String): InvitationRecord? = null

    suspend fun markSent(
        id: String,
        now: Instant,
    ): Boolean = false

    suspend fun accept(
        id: String,
        tokenHash: String,
        accountEmail: String,
        emailVerified: Boolean,
        accountId: String,
        operationId: String,
        now: Instant,
    ): InvitationRecord?

    suspend fun revoke(
        id: String,
        now: Instant,
    ): Boolean

    /** Marks every still-pending invitation past its expiry. Returns the number of rows changed. */
    suspend fun expireDue(now: Instant): Int = 0
}

internal class InMemoryInvitationRepository : InvitationRepository {
    private val mutex = Mutex()
    private val records = mutableMapOf<String, InvitationRecord>()

    override suspend fun create(record: InvitationRecord): Boolean =
        mutex.withLock {
            val canonical = record.canonicalForStorage()
            if (!canonical.validForCreation() || canonical.id in records || records.values.any { it.tokenHash == canonical.tokenHash }) {
                return@withLock false
            }
            records[canonical.id] = canonical
            true
        }

    override suspend fun find(id: String): InvitationRecord? =
        mutex.withLock { records[id] }

    override suspend fun markSent(
        id: String,
        now: Instant,
    ): Boolean =
        mutex.withLock {
            val current = records[id] ?: return@withLock false
            val timestamp = now.truncatedTo(ChronoUnit.MILLIS)
            if (timestamp < current.createdAt || current.status != InvitationStatus.CREATED) {
                return@withLock false
            }
            records[id] = current.copy(status = InvitationStatus.SENT)
            true
        }

    override suspend fun expireDue(now: Instant): Int =
        mutex.withLock {
            val timestamp = now.truncatedTo(ChronoUnit.MILLIS)
            var changed = 0
            for (id in records.keys.toList()) {
                val record = records[id] ?: continue
                if (record.status in setOf(InvitationStatus.CREATED, InvitationStatus.SENT) &&
                    timestamp >= record.expiresAt
                ) {
                    records[id] = record.copy(status = InvitationStatus.EXPIRED)
                    changed += 1
                }
            }
            changed
        }

    override suspend fun accept(
        id: String,
        tokenHash: String,
        accountEmail: String,
        emailVerified: Boolean,
        accountId: String,
        operationId: String,
        now: Instant,
    ): InvitationRecord? =
        mutex.withLock {
            val current = records[id] ?: return@withLock null
            val timestamp = now.truncatedTo(ChronoUnit.MILLIS)
            if (!validAcceptanceInput(emailVerified, accountId, operationId) ||
                accountEmail.trim().lowercase(Locale.ROOT) != current.recipient ||
                !MessageDigest.isEqual(current.tokenHash.toByteArray(Charsets.US_ASCII), tokenHash.toByteArray(Charsets.US_ASCII)) ||
                timestamp < current.createdAt
            ) {
                return@withLock null
            }
            if (current.status == InvitationStatus.ACCEPTED &&
                current.acceptedBy == accountId &&
                current.acceptedOperation == operationId
            ) {
                return@withLock current
            }
            if (timestamp >= current.expiresAt) return@withLock null
            if (current.status != InvitationStatus.SENT) return@withLock null
            current
                .copy(status = InvitationStatus.ACCEPTED, acceptedBy = accountId, acceptedOperation = operationId, acceptedAt = timestamp)
                .also { records[id] = it }
        }

    override suspend fun revoke(
        id: String,
        now: Instant,
    ): Boolean =
        mutex.withLock {
            val current = records[id] ?: return@withLock false
            val timestamp = now.truncatedTo(ChronoUnit.MILLIS)
            if (timestamp < current.createdAt ||
                current.status == InvitationStatus.ACTIVE ||
                current.status == InvitationStatus.REVOKED
            ) {
                return@withLock false
            }
            records[id] = current.copy(status = InvitationStatus.REVOKED, revokedAt = timestamp)
            true
        }
}
