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
    suspend fun pending(now: Instant): List<OutboxEntry>
    suspend fun markDelivered(id: String, now: Instant): Boolean
    suspend fun markFailed(id: String, now: Instant): Boolean
    suspend fun scheduleRetry(id: String, nextAttemptAt: Instant, now: Instant): Boolean
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

    override suspend fun pending(now: Instant): List<OutboxEntry> =
        mutex.withLock {
            entries.values
                .filter { it.nextAttemptAt <= now && it.lastStatus != VerificationDeliveryStatus.DELIVERED && it.lastStatus != VerificationDeliveryStatus.FAILED }
                .toList()
        }

    override suspend fun markDelivered(id: String, now: Instant): Boolean =
        mutex.withLock {
            val current = entries[id] ?: return@withLock false
            entries[id] = current.copy(
                lastStatus = VerificationDeliveryStatus.DELIVERED,
                tokenValue = "",
                attempts = current.attempts + 1,
            )
            true
        }

    override suspend fun markFailed(id: String, now: Instant): Boolean =
        mutex.withLock {
            val current = entries[id] ?: return@withLock false
            entries[id] = current.copy(lastStatus = VerificationDeliveryStatus.FAILED, tokenValue = "")
            true
        }

    override suspend fun scheduleRetry(id: String, nextAttemptAt: Instant, now: Instant): Boolean =
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
    private val clock: Clock = Clock.systemUTC(),
) : InvitationOutbox {
    private val collection = database.getCollection<Document>("platform_invitation_outbox")

    init {
        collection.createIndex(Indexes.ascending("nextAttemptAt"), IndexOptions().name("outbox_retry_idx"))
        collection.createIndex(Indexes.ascending("_id"), IndexOptions().unique(true))
    }

    override suspend fun enqueue(entry: OutboxEntry): Boolean =
        try {
            collection.insertOne(entry.toDocument())
            true
        } catch (_: com.mongodb.MongoWriteException) {
            false
        }

    override suspend fun pending(now: Instant): List<OutboxEntry> {
        val result = mutableListOf<OutboxEntry>()
        collection
            .find(Filters.lte("nextAttemptAt", Date.from(now)))
            .collect { doc -> doc.toOutboxEntry()?.let(result::add) }
        return result
    }

    override suspend fun markDelivered(id: String, now: Instant): Boolean =
        collection
            .updateOne(
                Filters.eq("_id", id),
                Updates.combine(
                    Updates.set("lastStatus", VerificationDeliveryStatus.DELIVERED.name),
                    Updates.set("tokenValue", ""),
                    Updates.set("deliveredAt", Date.from(now)),
                    Updates.inc("attempts", 1),
                ),
            ).matchedCount == 1L

    override suspend fun markFailed(id: String, now: Instant): Boolean =
        collection
            .updateOne(
                Filters.eq("_id", id),
                Updates.combine(
                    Updates.set("lastStatus", VerificationDeliveryStatus.FAILED.name),
                    Updates.set("tokenValue", ""),
                ),
            ).matchedCount == 1L

    override suspend fun scheduleRetry(id: String, nextAttemptAt: Instant, now: Instant): Boolean =
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
                Updates.set("tokenValue", ""),
            ).matchedCount == 1L

    private fun OutboxEntry.toDocument(): Document =
        Document("_id", invitationId)
            .append("recipient", recipient)
            .append("tokenValue", tokenValue)
            .append("expiresAt", Date.from(expiresAt))
            .append("attempts", attempts)
            .append("nextAttemptAt", Date.from(nextAttemptAt))
            .append("lastStatus", lastStatus.name)
            .append("createdAt", Date.from(createdAt))

    private fun Document.toOutboxEntry(): OutboxEntry? =
        try {
            OutboxEntry(
                invitationId = getString("_id"),
                recipient = getString("recipient"),
                tokenValue = getString("tokenValue"),
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
