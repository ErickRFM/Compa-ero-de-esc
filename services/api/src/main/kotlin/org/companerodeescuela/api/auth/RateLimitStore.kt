package org.companerodeescuela.api.auth

import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.firstOrNull
import org.bson.Document
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared, cross-replica rate-limit counter store.
 *
 * Each key tracks a sliding window of attempts. Implementations must be safe
 * for concurrent access across processes. [MongoRateLimitStore] persists to a
 * shared MongoDB collection so every replica observes the same counters.
 */
interface RateLimitStore {
    /**
     * Atomically increments the counter for [key] and returns the number of
     * attempts within the current window. If the window has elapsed since the
     * last reset, a new window starts at 1.
     *
     * @param key       hashed storage key
     * @param window    sliding window duration
     * @param now       current instant
     * @return the attempt count after increment, and the instant the window started
     */
    suspend fun increment(
        key: String,
        window: Duration,
        now: Instant,
    ): RateLimitEntry

    /**
     * Reads the current counter for [key] without incrementing.
     */
    suspend fun current(key: String): RateLimitEntry?

    /**
     * Resets the counter for [key].
     */
    suspend fun reset(key: String)

    /**
     * Removes expired entries to bound collection/map size.
     */
    suspend fun pruneExpired(now: Instant)
}

data class RateLimitEntry(
    val key: String,
    val attempts: Int,
    val windowStartedAt: Instant,
)

/** In-process implementation backed by a ConcurrentHashMap. Single-replica only. */
internal class InMemoryRateLimitStore(
    private val clock: Clock = Clock.systemUTC(),
) : RateLimitStore {
    private val map = ConcurrentHashMap<String, RateLimitEntry>()

    override suspend fun increment(
        key: String,
        window: Duration,
        now: Instant,
    ): RateLimitEntry {
        var count = 0
        while (true) {
            val existing = map[key]
            val entry =
                if (existing == null || Duration.between(existing.windowStartedAt, now) >= window) {
                    RateLimitEntry(key, 1, now)
                } else {
                    existing.copy(attempts = existing.attempts + 1)
                }
            if (existing == null) {
                val prev = map.putIfAbsent(key, entry)
                if (prev == null) return entry
                continue
            }
            if (map.replace(key, existing, entry)) return entry
            // Retry on concurrent modification
            count++
            if (count > 64) return entry // Fallback: return best-effort
        }
    }

    override suspend fun current(key: String): RateLimitEntry? = map[key]

    override suspend fun reset(key: String) {
        map.remove(key)
    }

    override suspend fun pruneExpired(now: Instant) {
        val cutoff = now.minusSeconds(3600)
        map.entries.removeIf { it.value.windowStartedAt.isBefore(cutoff) }
    }
}

/**
 * MongoDB-backed implementation. Safe across replicas: uses atomic
 * findOneAndUpdate to increment and TTL index for automatic cleanup.
 */
internal class MongoRateLimitStore(
    database: MongoDatabase,
    private val clock: Clock = Clock.systemUTC(),
) : RateLimitStore {
    private val collection = database.getCollection<Document>("platform_rate_limits")

    // Index creation is idempotent: MongoDB ignores duplicate index names with
    // identical definitions. We guard with a volatile flag so concurrent calls
    // do not replay the command unnecessarily. The _id index is created
    // automatically by MongoDB and must not be recreated here.
    @Volatile
    private var indexesReady = false

    private suspend fun ensureIndexes() {
        if (indexesReady) return
        try {
            collection.createIndex(
                Indexes.ascending("expireAt"),
                IndexOptions()
                    .name("rate_limit_expireAt_ttl")
                    .expireAfter(0L, java.util.concurrent.TimeUnit.SECONDS),
            )
        } catch (_: com.mongodb.MongoCommandException) {
            // Index already exists with a different name or definition; safe to ignore
        }
        indexesReady = true
    }

    override suspend fun increment(
        key: String,
        window: Duration,
        now: Instant,
    ): RateLimitEntry {
        ensureIndexes()
        val expireAt = now.plus(window).plusSeconds(60)
        val doc =
            collection.findOneAndUpdate(
                com.mongodb.client.model.Filters
                    .eq("_id", key),
                Updates.combine(
                    Updates.setOnInsert("windowStartedAt", java.util.Date.from(now)),
                    Updates.setOnInsert("expireAt", java.util.Date.from(expireAt)),
                    Updates.inc("attempts", 1),
                ),
                FindOneAndUpdateOptions()
                    .upsert(true)
                    .returnDocument(ReturnDocument.AFTER),
            ) ?: return RateLimitEntry(key, 1, now)

        val startedAt = doc.getDate("windowStartedAt").toInstant()
        val attempts = doc.getInteger("attempts", 1)

        // If the window has elapsed, start a fresh window
        if (Duration.between(startedAt, now) >= window) {
            val reset =
                collection.findOneAndUpdate(
                    com.mongodb.client.model.Filters
                        .eq("_id", key),
                    Updates.combine(
                        Updates.set("windowStartedAt", java.util.Date.from(now)),
                        Updates.set("attempts", 1),
                        Updates.set("expireAt", java.util.Date.from(expireAt)),
                    ),
                    FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
                ) ?: return RateLimitEntry(key, 1, now)
            return RateLimitEntry(
                key,
                reset.getInteger("attempts", 1),
                reset.getDate("windowStartedAt").toInstant(),
            )
        }
        return RateLimitEntry(key, attempts, startedAt)
    }

    override suspend fun current(key: String): RateLimitEntry? {
        val doc =
            collection
                .find(
                    com.mongodb.client.model.Filters
                        .eq("_id", key),
                ).firstOrNull() ?: return null
        return RateLimitEntry(
            key,
            doc.getInteger("attempts", 0),
            doc.getDate("windowStartedAt").toInstant(),
        )
    }

    override suspend fun reset(key: String) {
        collection.deleteOne(
            com.mongodb.client.model.Filters
                .eq("_id", key),
        )
    }

    override suspend fun pruneExpired(now: Instant) {
        // TTL index handles automatic expiration; this is a safety net
        collection.deleteMany(
            com.mongodb.client.model.Filters
                .lt("expireAt", java.util.Date.from(now)),
        )
    }
}
