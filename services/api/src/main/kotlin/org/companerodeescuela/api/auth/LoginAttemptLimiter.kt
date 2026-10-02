package org.companerodeescuela.api.auth

import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Small in-process login limiter used by the pilot API.
 *
 * The key combines normalized username and connection address. That avoids
 * locking every student behind a school NAT while still slowing repeated
 * guesses against one account from one source.
 *
 * This is an abuse-control boundary, not an authentication boundary. A
 * multi-replica deployment must replace or front it with a shared limiter.
 */
class LoginAttemptLimiter(
    private val maxAttempts: Int = 8,
    private val window: Duration = Duration.ofMinutes(5),
    private val clock: Clock = Clock.systemUTC(),
) {
    init {
        require(maxAttempts > 0) { "maxAttempts must be positive" }
        require(!window.isZero && !window.isNegative) { "window must be positive" }
    }

    private data class Bucket(
        val startedAt: Instant,
        var attempts: Int,
    )

    private val buckets = mutableMapOf<String, Bucket>()

    @Synchronized
    fun acquire(username: String, clientAddress: String): Long? {
        val now = clock.instant()
        val key = key(username, clientAddress)
        val existing = buckets[key]

        if (existing == null || Duration.between(existing.startedAt, now) >= window) {
            buckets[key] = Bucket(startedAt = now, attempts = 1)
            prune(now)
            return null
        }

        if (existing.attempts >= maxAttempts) {
            val elapsed = Duration.between(existing.startedAt, now)
            return (window.minus(elapsed).seconds).coerceAtLeast(1)
        }

        existing.attempts += 1
        return null
    }

    @Synchronized
    fun reset(username: String, clientAddress: String) {
        buckets.remove(key(username, clientAddress))
    }

    private fun key(username: String, clientAddress: String): String =
        username.trim().lowercase() + "|" + clientAddress

    private fun prune(now: Instant) {
        buckets.entries.removeIf { (_, bucket) ->
            Duration.between(bucket.startedAt, now) >= window
        }
    }
}
