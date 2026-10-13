package org.companerodeescuela.api.auth

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

data class LoginFailureResult(
    val retryAfterSeconds: Long?,
    val progressiveDelay: Duration,
)

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
    private val progressiveDelayBase: Duration = Duration.ofSeconds(1),
    private val progressiveDelayMaximum: Duration = Duration.ofSeconds(30),
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

    private val buckets = mutableMapOf<String, Bucket>()
    private val accountFailures = mutableMapOf<String, Bucket>()

    @Synchronized
    fun acquire(username: String, clientAddress: String): Long? {
        return acquireKey(key(username, clientAddress))
    }

    @Synchronized
    fun acquireAddress(clientAddress: String): Long? = acquireKey("ip|$clientAddress")

    @Synchronized
    private fun acquireKey(key: String): Long? {
        val now = clock.instant()
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

    @Synchronized
    fun resetAddress(clientAddress: String) {
        buckets.remove("ip|$clientAddress")
    }

    @Synchronized
    fun recordLoginFailure(username: String, clientAddress: String): LoginFailureResult {
        val addressRetry = acquireAddress(clientAddress)
        val accountAddressRetry = acquire(username, clientAddress)
        val now = clock.instant()
        val accountKey = key(username, clientAddress)
        val current = accountFailures[accountKey]
        val attempts = if (current == null || Duration.between(current.startedAt, now) >= window) {
            1
        } else {
            current.attempts + 1
        }
        accountFailures[accountKey] = Bucket(
            startedAt = if (current == null || Duration.between(current.startedAt, now) >= window) now else current.startedAt,
            attempts = attempts,
        )
        val exponent = (attempts - 1).coerceAtMost(62)
        val baseSeconds = progressiveDelayBase.seconds
        val maximumSeconds = progressiveDelayMaximum.seconds
        val multiplier = 1L shl exponent
        val delaySeconds = if (baseSeconds == 0L || maximumSeconds == 0L) 0L else if (
            exponent >= 63 || baseSeconds > maximumSeconds / multiplier
        ) maximumSeconds else baseSeconds * multiplier
        val retryAfter = listOfNotNull(addressRetry, accountAddressRetry).maxOrNull()
        return LoginFailureResult(retryAfter, Duration.of(delaySeconds, ChronoUnit.SECONDS))
    }

    @Synchronized
    fun resetLogin(username: String, clientAddress: String) {
        resetAddress(clientAddress)
        reset(username, clientAddress)
        accountFailures.remove(key(username, clientAddress))
    }

    private fun key(username: String, clientAddress: String): String =
        username.trim().lowercase() + "|" + clientAddress

    private fun prune(now: Instant) {
        buckets.entries.removeIf { (_, bucket) ->
            Duration.between(bucket.startedAt, now) >= window
        }
    }
}
