package org.companerodeescuela.api.invitations

import com.mongodb.client.model.Filters
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bson.Document
import org.companerodeescuela.api.security.MfaSecretCipher
import org.companerodeescuela.shared.contracts.VerificationDeliveryStatus
import java.time.Clock
import java.time.Instant
import java.util.Date

/**
 * Durable outbox for invitation email delivery. When the email gateway returns
 * UNCONFIRMED, the delivery is recorded here for asynchronous retry instead of
 * blocking the request or silently dropping it.
 *
 * Each entry tracks the invitation ID, recipient, token value (cleared after
 * first successful delivery), attempts, and next retry time.
 */
internal interface InvitationOutbox {
    suspend fun enqueue(entry: OutboxEntry): Boolean

    /** Replaces a pending capability after an explicitly authorized manual retry. */
    suspend fun replace(entry: OutboxEntry): Boolean = enqueue(entry)

    suspend fun pending(now: Instant): List<OutboxEntry>

    suspend fun markDelivered(
        id: String,
        now: Instant,
    ): Boolean

    suspend fun markFailed(
        id: String,
        now: Instant,
    ): Boolean

    suspend fun scheduleRetry(
        id: String,
        nextAttemptAt: Instant,
        now: Instant,
    ): Boolean

    suspend fun clearToken(id: String): Boolean
}

internal data class OutboxEntry(
    val invitationId: String,
    val recipient: String,
    val tokenValue: String,
    val expiresAt: Instant,
    val attempts: Int = 0,
    val nextAttemptAt: Instant,
    val lastStatus: VerificationDeliveryStatus = VerificationDeliveryStatus.PENDING,
    val createdAt: Instant,
)

internal class InMemoryInvitationOutbox(
    private val clock: Clock = Clock.systemUTC(),
) : InvitationOutbox {
    private val mutex = Mutex()
    private val entries = mutableMapOf<String, OutboxEntry>()

    override suspend fun enqueue(entry: OutboxEntry): Boolean =
        mutex.withLock {
            if (entry.invitationId in entries) return@withLock false
            entries[entry.invitationId] = entry
            true
        }

    override suspend fun replace(entry: OutboxEntry): Boolean =
        mutex.withLock {
            entries[entry.invitationId] = entry
            true
        }

    override suspend fun pending(now: Instant): List<OutboxEntry> =
        mutex.withLock {
            entries.values
                .filter {
                    it.nextAttemptAt <= now &&
                        it.expiresAt > now &&
                        it.tokenValue.isNotEmpty() &&
                        it.lastStatus != VerificationDeliveryStatus.DELIVERED &&
                        it.lastStatus != VerificationDeliveryStatus.FAILED
                }.toList()
        }

    override suspend fun markDelivered(
        id: String,
        now: Instant,
    ): Boolean =
        mutex.withLock {
            val current = entries[id] ?: return@withLock false
            entries[id] =
                current.copy(
                    lastStatus = VerificationDeliveryStatus.DELIVERED,
                    tokenValue = "",
                    attempts = current.attempts + 1,
                )
            true
        }

    override suspend fun markFailed(
        id: String,
        now: Instant,
    ): Boolean =
        mutex.withLock {
            val current = entries[id] ?: return@withLock false
            entries[id] = current.copy(lastStatus = VerificationDeliveryStatus.FAILED, tokenValue = "")
            true
        }

    override suspend fun scheduleRetry(
        id: String,
        nextAttemptAt: Instant,
        now: Instant,
    ): Boolean =
        mutex.withLock {
            val current = entries[id] ?: return@withLock false
            entries[id] = current.copy(nextAttemptAt = nextAttemptAt, attempts = current.attempts + 1)
            true
        }

    override suspend fun clearToken(id: String): Boolean =
        mutex.withLock {
            val current = entries[id] ?: return@withLock false
            entries[id] = current.copy(tokenValue = "")
            true
        }
}

internal class MongoInvitationOutbox(
    database: MongoDatabase,
    private val cipher: MfaSecretCipher,
    private val clock: Clock = Clock.systemUTC(),
) : InvitationOutbox {
    private val collection = database.getCollection<Document>("platform_invitation_outbox")

    @Volatile
    private var indexesReady = false

    private suspend fun ensureIndexes() {
        if (indexesReady) return
        try {
            collection.createIndex(Indexes.ascending("nextAttemptAt"), IndexOptions().name("outbox_retry_idx"))
        } catch (_: com.mongodb.MongoCommandException) {
            // Index already exists; safe to ignore
        }
        // One-time compatibility migrations encrypt legacy recipient addresses and retry tokens.
        collection.find(Filters.exists("recipient")).collect { legacy ->
            val id = legacy.getString("_id") ?: throw IllegalStateException("Legacy invitation outbox entry has no ID")
            val recipient = legacy.getString("recipient") ?: ""
            if (recipient.isNotEmpty()) {
                val encrypted = cipher.encrypt(
                    id,
                    "platform-invitation-outbox-recipient",
                    recipient.toByteArray(Charsets.UTF_8),
                )
                collection.updateOne(
                    Filters.and(Filters.eq("_id", id), Filters.exists("recipient")),
                    Updates.combine(
                        Updates.set("recipientCiphertext", encrypted),
                        Updates.unset("recipient"),
                    ),
                )
            } else {
                collection.updateOne(
                    Filters.eq("_id", id),
                    Updates.unset("recipient"),
                )
            }
        }
        collection.find(Filters.exists("tokenValue")).collect { legacy ->
            val token = legacy.getString("tokenValue") ?: ""
            if (token.isNotEmpty()) {
                require(token.length in 20..64) { "Legacy invitation retry token has an invalid length" }
                val id = legacy.getString("_id") ?: throw IllegalStateException("Legacy invitation outbox entry has no ID")
                val encrypted = cipher.encrypt(id, "platform-invitation-outbox", token.toByteArray(Charsets.UTF_8))
                collection.updateOne(
                    Filters.and(Filters.eq("_id", id), Filters.exists("tokenValue")),
                    Updates.combine(
                        Updates.set("tokenCiphertext", encrypted),
                        Updates.unset("tokenValue"),
                    ),
                )
            } else {
                collection.updateOne(
                    Filters.eq("_id", legacy.getString("_id")),
                    Updates.unset("tokenValue"),
                )
            }
        }
        indexesReady = true
    }

    override suspend fun enqueue(entry: OutboxEntry): Boolean {
        ensureIndexes()
        return try {
            collection.insertOne(entry.toDocument())
            true
        } catch (error: com.mongodb.MongoWriteException) {
            if (error.error.code == 11000) false else throw error
        }
    }

    override suspend fun replace(entry: OutboxEntry): Boolean {
        ensureIndexes()
        collection.replaceOne(
            Filters.eq("_id", entry.invitationId),
            entry.toDocument(),
            com.mongodb.client.model.ReplaceOptions().upsert(true),
        )
        return true
    }

    override suspend fun pending(now: Instant): List<OutboxEntry> {
        ensureIndexes()
        val result = mutableListOf<OutboxEntry>()
        collection
            .find(
                Filters.and(
                    Filters.lte("nextAttemptAt", Date.from(now)),
                    Filters.gt("expiresAt", Date.from(now)),
                    Filters.nin("lastStatus", VerificationDeliveryStatus.DELIVERED.name, VerificationDeliveryStatus.FAILED.name),
                ),
            ).limit(50)
            .collect { doc -> doc.toOutboxEntry()?.takeIf { it.tokenValue.isNotEmpty() }?.let(result::add) }
        return result
    }

    override suspend fun markDelivered(
        id: String,
        now: Instant,
    ): Boolean =
        collection
            .updateOne(
                Filters.eq("_id", id),
                Updates.combine(
                    Updates.set("lastStatus", VerificationDeliveryStatus.DELIVERED.name),
                    Updates.set("tokenCiphertext", ""),
                    Updates.set("deliveredAt", Date.from(now)),
                    Updates.inc("attempts", 1),
                ),
            ).matchedCount == 1L

    override suspend fun markFailed(
        id: String,
        now: Instant,
    ): Boolean =
        collection
            .updateOne(
                Filters.eq("_id", id),
                Updates.combine(
                    Updates.set("lastStatus", VerificationDeliveryStatus.FAILED.name),
                    Updates.set("tokenCiphertext", ""),
                ),
            ).matchedCount == 1L

    override suspend fun scheduleRetry(
        id: String,
        nextAttemptAt: Instant,
        now: Instant,
    ): Boolean =
        collection
            .updateOne(
                Filters.eq("_id", id),
                Updates.combine(
                    Updates.set("nextAttemptAt", Date.from(nextAttemptAt)),
                    Updates.inc("attempts", 1),
                ),
            ).matchedCount == 1L

    override suspend fun clearToken(id: String): Boolean =
        collection
            .updateOne(
                Filters.eq("_id", id),
                Updates.set("tokenCiphertext", ""),
            ).matchedCount == 1L

    private fun OutboxEntry.toDocument(): Document {
        val encryptedToken =
            if (tokenValue.isEmpty()) {
                ""
            } else {
                cipher.encrypt(
                    invitationId,
                    "platform-invitation-outbox",
                    tokenValue.toByteArray(Charsets.UTF_8),
                )
            }
        val encryptedRecipient =
            cipher.encrypt(
                invitationId,
                "platform-invitation-outbox-recipient",
                recipient.toByteArray(Charsets.UTF_8),
            )
        return Document("_id", invitationId)
            .append("recipientCiphertext", encryptedRecipient)
            .append("tokenCiphertext", encryptedToken)
            .append("expiresAt", Date.from(expiresAt))
            .append("attempts", attempts)
            .append("nextAttemptAt", Date.from(nextAttemptAt))
            .append("lastStatus", lastStatus.name)
            .append("createdAt", Date.from(createdAt))
    }

    private fun Document.toOutboxEntry(): OutboxEntry? {
        return try {
            val recipientCiphertext = getString("recipientCiphertext") ?: return null
            val recipient =
                cipher
                    .decrypt(getString("_id"), "platform-invitation-outbox-recipient", recipientCiphertext)
                    .toString(Charsets.UTF_8)
            val ciphertext = getString("tokenCiphertext") ?: return null
            val value =
                if (ciphertext.isEmpty()) {
                    ""
                } else {
                    cipher
                        .decrypt(getString("_id"), "platform-invitation-outbox", ciphertext)
                        .toString(Charsets.UTF_8)
                }
            OutboxEntry(
                invitationId = getString("_id"),
                recipient = recipient,
                tokenValue = value,
                expiresAt = getDate("expiresAt").toInstant(),
                attempts = getInteger("attempts", 0),
                nextAttemptAt = getDate("nextAttemptAt").toInstant(),
                lastStatus = VerificationDeliveryStatus.valueOf(getString("lastStatus")),
                createdAt = getDate("createdAt").toInstant(),
            )
        } catch (_: Exception) {
            null
        }
    }
}
