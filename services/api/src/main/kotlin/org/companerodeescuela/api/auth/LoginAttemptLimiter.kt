package org.companerodeescuela.api.auth

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
 * Small in-process login limiter used by the pilot API.
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

    private val buckets = LinkedHashMap<String, Bucket>()
    private val accountFailures = LinkedHashMap<String, Bucket>()
    private var operationsSincePrune = 0

    @Synchronized
    fun acquire(username: String, clientAddress: String): Long? = acquireKey(key(username, clientAddress))

    @Synchronized
    fun acquireAddress(clientAddress: String): Long? = acquireKey("ip|$clientAddress")

    @Synchronized
    private fun acquireKey(key: String): Long? {
        val now = clock.instant()
        maintain(now)
        val safeKey = storageKey(key)
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

    @Synchronized
    fun reset(username: String, clientAddress: String) {
        maintain(clock.instant())
        buckets.remove(storageKey(key(username, clientAddress)))
    }

    @Synchronized
    fun resetAddress(clientAddress: String) {
        maintain(clock.instant())
        buckets.remove(storageKey("ip|$clientAddress"))
    }

    @Synchronized
    fun recordLoginFailure(username: String, clientAddress: String): LoginFailureResult {
        val addressRetry = acquireAddress(clientAddress)
        val accountAddressRetry = acquire(username, clientAddress)
        val now = clock.instant()
        maintain(now)
        val accountKey = storageKey("account|${username.trim().lowercase()}")
        val current = accountFailures[accountKey]
        val expired = current == null || Duration.between(current.startedAt, now) >= window
        val attempts = if (expired) 1 else current!!.attempts + 1
        if (current == null) makeRoom(accountFailures)
        accountFailures[accountKey] = Bucket(
            startedAt = if (expired) now else current!!.startedAt,
            attempts = attempts,
        )
        val exponent = (attempts - 1).coerceAtMost(62)
        val baseSeconds = progressiveDelayBase.seconds
        val maximumSeconds = progressiveDelayMaximum.seconds
        val multiplier = 1L shl exponent
        val delaySeconds = if (baseSeconds == 0L || maximumSeconds == 0L) 0L else if (
            baseSeconds > maximumSeconds / multiplier
        ) maximumSeconds else baseSeconds * multiplier
        val retryAfter = listOfNotNull(addressRetry, accountAddressRetry).maxOrNull()
        return LoginFailureResult(retryAfter, Duration.of(delaySeconds, ChronoUnit.SECONDS))
    }

    @Synchronized
    fun resetLogin(username: String, clientAddress: String) {
        resetAddress(clientAddress)
        reset(username, clientAddress)
        maintain(clock.instant())
        accountFailures.remove(storageKey("account|${username.trim().lowercase()}"))
    }

    private fun key(username: String, clientAddress: String): String =
        username.trim().lowercase() + "|" + clientAddress

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

    private fun storageKey(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private companion object {
        const val MAX_RETAINED_KEYS = 4_096
        const val PRUNE_EVERY_OPERATIONS = 128
    }
}
