package org.companerodeescuela.api.devices

import com.mongodb.MongoWriteException
import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.lte
import com.mongodb.client.model.Filters.or
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bson.Document
import org.companerodeescuela.api.errors.ApiException

data class PushOutboxItem(
    val id: String,
    val owner: String,
    val token: String,
    val title: String,
    val body: String,
    val attempts: Int = 0,
    val leaseId: String? = null,
)

interface PushOutboxRepository {
    suspend fun enqueue(item: PushOutboxItem): Boolean
    suspend fun claim(now: Instant): PushOutboxItem?
    suspend fun finish(item: PushOutboxItem, result: PushDeliveryResult, now: Instant): Boolean
}

/** Atomic leases across independent API replicas; completed/dropped rows retained for 30 days. */
class MongoPushOutboxRepository(database: MongoDatabase) : PushOutboxRepository {
    private val collection = database.getCollection<Document>("push_outbox")
    private val indexMutex = Mutex()
    private var indexed = false

    private suspend fun ensureIndexes() = indexMutex.withLock {
        if (indexed) return@withLock
        collection.createIndex(Indexes.compoundIndex(Indexes.ascending("status"), Indexes.ascending("nextAttemptAt")),
            IndexOptions().name("ix_push_due"))
        collection.createIndex(Indexes.ascending("expiresAt"),
            IndexOptions().name("ttl_push_outbox").expireAfter(0L, TimeUnit.SECONDS))
        indexed = true
    }

    override suspend fun enqueue(item: PushOutboxItem): Boolean {
        ensureIndexes()
        require(item.id.matches(Regex("[0-9a-f]{64}")) && item.owner.length in 2..512 &&
            item.token.length in 16..4096 && item.title.length in 1..160 && item.body.length <= 2000)
        val now = Instant.now()
        val row = Document("_id", item.id)
            .append("owner", item.owner)
            .append("token", item.token)
            .append("title", item.title)
            .append("body", item.body)
            .append("status", "PENDING")
            .append("attempts", 0)
            .append("nextAttemptAt", Date.from(now))
            .append("expiresAt", Date.from(now.plusSeconds(30L * 86400)))
        return try {
            collection.insertOne(row)
            true
        } catch (ex: MongoWriteException) {
            if (ex.error.code == 11000) false else throw ex
        }
    }

    override suspend fun claim(now: Instant): PushOutboxItem? {
        ensureIndexes()
        val leaseId = UUID.randomUUID().toString()
        val due = Date.from(now)
        val filter = or(
            and(eq("status", "PENDING"), lte("nextAttemptAt", due)),
            and(eq("status", "SENDING"), lte("leaseUntil", due)),
        )
        val updated = collection.findOneAndUpdate(filter,
            Updates.combine(
                Updates.set("status", "SENDING"),
                Updates.set("leaseId", leaseId),
                Updates.set("leaseUntil", Date.from(now.plusSeconds(90))),
                Updates.inc("attempts", 1),
            ),
            FindOneAndUpdateOptions().sort(Sorts.ascending("nextAttemptAt"))
                .returnDocument(ReturnDocument.AFTER),
        ) ?: return null
        return PushOutboxItem(
            id = updated.getString("_id"),
            owner = updated.getString("owner"),
            token = updated.getString("token"),
            title = updated.getString("title"),
            body = updated.getString("body"),
            attempts = (updated.get("attempts") as Number).toInt(),
            leaseId = leaseId,
        )
    }

    override suspend fun finish(item: PushOutboxItem, result: PushDeliveryResult, now: Instant): Boolean {
        val status = when {
            result == PushDeliveryResult.ACCEPTED -> "SENT"
            result == PushDeliveryResult.INVALID_TOKEN -> "DROPPED"
            result == PushDeliveryResult.REJECTED -> "REJECTED"
            item.attempts >= 7 -> "DEAD"
            else -> "PENDING"
        }
        val delaySeconds = when (result) {
            PushDeliveryResult.CONFIGURATION_ERROR -> 3600L
            else -> (15L * (1L shl item.attempts.coerceIn(0, 10))).coerceAtMost(21600L)
        }
        val update = Updates.combine(
            Updates.set("status", status),
            Updates.set("nextAttemptAt", Date.from(now.plusSeconds(delaySeconds))),
            Updates.unset("leaseId"),
            Updates.unset("leaseUntil"),
            Updates.set("lastResult", result.name),
        )
        return collection.updateOne(and(eq("_id", item.id), eq("status", "SENDING"),
            eq("leaseId", item.leaseId)), update).modifiedCount == 1L
    }
}

/** In-memory analogue is intentionally for unit tests and local development only. */
class InMemoryPushOutboxRepository : PushOutboxRepository {
    private val mutex = Mutex()
    private data class Row(val item: PushOutboxItem, val due: Instant, val state: String)
    private val records = linkedMapOf<String, Row>()
    override suspend fun enqueue(item: PushOutboxItem): Boolean = mutex.withLock {
        if (records.containsKey(item.id)) false else {
            records[item.id] = Row(item, Instant.EPOCH, "PENDING")
            true
        }
    }
    override suspend fun claim(now: Instant): PushOutboxItem? = mutex.withLock {
        val row = records.entries.firstOrNull { (_, row) ->
            (row.state == "PENDING" || row.state == "SENDING") && !row.due.isAfter(now)
        } ?: return@withLock null
        val claimed = row.value.item.copy(attempts = row.value.item.attempts + 1,
            leaseId = UUID.randomUUID().toString())
        records[row.key] = Row(claimed, now.plusSeconds(90), "SENDING")
        claimed
    }
    override suspend fun finish(item: PushOutboxItem, result: PushDeliveryResult, now: Instant): Boolean = mutex.withLock {
        val existing = records[item.id] ?: return@withLock false
        if (existing.state != "SENDING" || existing.item.leaseId != item.leaseId) return@withLock false
        val status = when {
            result == PushDeliveryResult.ACCEPTED -> "SENT"
            result in setOf(PushDeliveryResult.INVALID_TOKEN, PushDeliveryResult.REJECTED) -> "DROPPED"
            item.attempts >= 7 -> "DEAD"
            else -> "PENDING"
        }
        val delaySeconds = if (result == PushDeliveryResult.CONFIGURATION_ERROR) 3600L else
            (15L * (1L shl item.attempts.coerceIn(0, 10))).coerceAtMost(21600L)
        records[item.id] = Row(item, now.plusSeconds(delaySeconds), status)
        true
    }
}

/** No public notification creation endpoint. Only trusted, already-scoped business services may enqueue. */
class PushOutboxService(
    private val devices: DeviceTokenRepository,
    private val outbox: PushOutboxRepository,
    private val sender: PushGateway,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun enqueueForOwner(eventId: String, owner: String, title: String, body: String): Int {
        if (eventId.length !in 1..256 || owner.length !in 2..512 ||
            !(owner.startsWith("NATIVE:") || owner.startsWith("INSTITUTIONAL:")) ||
            title.length !in 1..160 || body.length > 2000)
            throw ApiException.Validation("Invalid notification request")
        var created = 0
        for (token in devices.listTokens(setOf(owner))) {
            val id = digest("$eventId\u0000$owner\u0000$token")
            if (outbox.enqueue(PushOutboxItem(id, owner, token, title, body))) created++
        }
        return created
    }

    /** One leased destination only; never exposes secrets or provider responses to clients. */
    suspend fun dispatchNext(): Boolean {
        val item = outbox.claim(clock.instant()) ?: return false
        val current = devices.listTokens(setOf(item.owner))
        val result = if (item.token !in current) PushDeliveryResult.INVALID_TOKEN
            else try { sender.send(item.token, item.title, item.body) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { PushDeliveryResult.RETRYABLE }
        val ack = outbox.finish(item, result, clock.instant())
        if (ack && result == PushDeliveryResult.INVALID_TOKEN && item.token in current)
            devices.deactivateInvalid(item.token)
        return true
    }

    /** Caller owns cancellation via the application's lifecycle. */
    suspend fun runLoop() {
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            try {
                if (!dispatchNext()) delay(5_000)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Mongo/network outages must not kill the worker permanently.
                delay(10_000)
            }
        }
    }
}

private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
