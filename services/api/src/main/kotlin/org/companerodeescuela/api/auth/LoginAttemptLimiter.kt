package org.companerodeescuela.api.auth

import kotlinx.coroutines.runBlocking
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.LinkedHashMap

data class LoginFailureResult(
    val retryAfterSeconds: Long?,
    val progressiveDelay: Duration,
)

/**
 * Login attempt limiter with optional distributed backing store.
 *
 * When a [RateLimitStore] is provided (e.g. [MongoRateLimitStore]), counters
 * are shared across replicas so a multi-instance deployment observes the same
 * attempt buckets. Without a store, behavior is identical to the previous
 * in-process limiter.
 *
 * This is an abuse-control boundary, not an authentication boundary.
 */
class LoginAttemptLimiter(
    private val maxAttempts: Int = 8,
    private val window: Duration = Duration.ofMinutes(5),
    private val clock: Clock = Clock.systemUTC(),
    private val progressiveDelayBase: Duration = Duration.ofSeconds(1),
    private val progressiveDelayMaximum: Duration = Duration.ofSeconds(30),
    private val store: RateLimitStore? = null,
) {
    init {
        require(maxAttempts > 0) { "maxAttempts must be positive" }
        require(!window.isZero && !window.isNegative) { "window must be positive" }
        require(!progressiveDelayBase.isNegative && !progressiveDelayMaximum.isNegative) { "progressive delays cannot be negative" }
        require(progressiveDelayMaximum >= progressiveDelayBase) { "maximum progressive delay must be at least the base delay" }
    }

    private data class Bucket(
        val startedAt: Instant,
        var attempts: Int,
    )

    // Used only when store == null (single-replica fallback)
    private val buckets = LinkedHashMap<String, Bucket>()
    private val accountFailures = LinkedHashMap<String, Bucket>()
    private var operationsSincePrune = 0

    fun acquire(
        username: String,
        clientAddress: String,
    ): Long? = acquireKey(key(username, clientAddress))

    fun acquireAddress(clientAddress: String): Long? = acquireKey("ip|$clientAddress")

    private fun acquireKey(rawKey: String): Long? {
        val safeKey = storageKey(rawKey)
        val now = clock.instant()

        if (store != null) {
            return try {
                runBlocking { acquireDistributed(safeKey, now) }
            } catch (_: Exception) {
                // Degrade to local limiter if the distributed store is unavailable.
                // Abuse control is preserved locally; cross-replica visibility is lost
                // but the request still receives correct 401 responses.
                acquireLocal(safeKey, now)
            }
        }

        return acquireLocal(safeKey, now)
    }

    @Synchronized
    private fun acquireLocal(
        safeKey: String,
        now: Instant,
    ): Long? {
        maintain(now)
        val existing = buckets[safeKey]

        if (existing == null || Duration.between(existing.startedAt, now) >= window) {
            if (existing == null) makeRoom(buckets)
            buckets[safeKey] = Bucket(startedAt = now, attempts = 1)
            return null
        }

        if (existing.attempts >= maxAttempts) {
            val elapsed = Duration.between(existing.startedAt, now)
            return (window.minus(elapsed).seconds).coerceAtLeast(1)
        }

        existing.attempts += 1
        return null
    }

    private suspend fun acquireDistributed(
        safeKey: String,
        now: Instant,
    ): Long? {
        val entry = store!!.increment(safeKey, window, now)
        if (entry.attempts > maxAttempts) {
            val elapsed = Duration.between(entry.windowStartedAt, now)
            return (window.minus(elapsed).seconds).coerceAtLeast(1)
        }
        return null
    }

    fun reset(
        username: String,
        clientAddress: String,
    ) {
        val safeKey = storageKey(key(username, clientAddress))
        if (store != null) {
            try {
                runBlocking { store.reset(safeKey) }
            } catch (_: Exception) {
                // Best-effort reset; local state may be stale but auth is unaffected
            }
            return
        }
        maintain(clock.instant())
        buckets.remove(safeKey)
    }

    fun resetAddress(clientAddress: String) {
        val safeKey = storageKey("ip|$clientAddress")
        if (store != null) {
            try {
                runBlocking { store.reset(safeKey) }
            } catch (_: Exception) {
                // Best-effort reset; local state may be stale but auth is unaffected
            }
            return
        }
        maintain(clock.instant())
        buckets.remove(safeKey)
    }

    fun recordLoginFailure(
        username: String,
        clientAddress: String,
    ): LoginFailureResult {
        val addressRetry = acquireAddress(clientAddress)
        val accountAddressRetry = acquire(username, clientAddress)
        val now = clock.instant()

        val accountKey = storageKey("account|${username.trim().lowercase()}")
        val attempts: Int

        if (store != null) {
            val entry =
                try {
                    runBlocking { store.increment(accountKey, window, now) }
                } catch (_: Exception) {
                    null
                }
            attempts = entry?.attempts ?: recordLocalAccountFailure(accountKey, now)
        } else {
            attempts = recordLocalAccountFailure(accountKey, now)
        }

        val exponent = (attempts - 1).coerceAtMost(62)
        val baseSeconds = progressiveDelayBase.seconds
        val maximumSeconds = progressiveDelayMaximum.seconds
        val multiplier = 1L shl exponent
        val delaySeconds =
            if (baseSeconds == 0L || maximumSeconds == 0L) {
                0L
            } else if (
                baseSeconds > maximumSeconds / multiplier
            ) {
                maximumSeconds
            } else {
                baseSeconds * multiplier
            }
        val retryAfter = listOfNotNull(addressRetry, accountAddressRetry).maxOrNull()
        return LoginFailureResult(retryAfter, Duration.of(delaySeconds, ChronoUnit.SECONDS))
    }

    @Synchronized
    private fun recordLocalAccountFailure(
        accountKey: String,
        now: Instant,
    ): Int {
        maintain(now)
        val current = accountFailures[accountKey]
        val expired = current == null || Duration.between(current.startedAt, now) >= window
        val attempts = if (expired) 1 else current!!.attempts + 1
        if (current == null) makeRoom(accountFailures)
        accountFailures[accountKey] =
            Bucket(
                startedAt = if (expired) now else current!!.startedAt,
                attempts = attempts,
            )
        return attempts
    }

    fun resetLogin(
        username: String,
        clientAddress: String,
    ) {
        resetAddress(clientAddress)
        reset(username, clientAddress)
        val accountKey = storageKey("account|${username.trim().lowercase()}")
        if (store != null) {
            try {
                runBlocking { store.reset(accountKey) }
            } catch (_: Exception) {
                // Best-effort reset; local state may be stale but auth is unaffected
            }
            return
        }
        maintain(clock.instant())
        accountFailures.remove(accountKey)
    }

    private fun key(
        username: String,
        clientAddress: String,
    ): String = username.trim().lowercase() + "|" + clientAddress

    private fun maintain(now: Instant) {
        operationsSincePrune += 1
        if (operationsSincePrune < PRUNE_EVERY_OPERATIONS) return
        operationsSincePrune = 0
        buckets.entries.removeIf { (_, bucket) -> Duration.between(bucket.startedAt, now) >= window }
        accountFailures.entries.removeIf { (_, bucket) -> Duration.between(bucket.startedAt, now) >= window }
    }

    private fun makeRoom(map: LinkedHashMap<String, Bucket>) {
        if (map.size < MAX_RETAINED_KEYS) return
        map.remove(map.keys.first())
    }

    private fun storageKey(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private companion object {
        const val MAX_RETAINED_KEYS = 4_096
        const val PRUNE_EVERY_OPERATIONS = 128
    }
}
