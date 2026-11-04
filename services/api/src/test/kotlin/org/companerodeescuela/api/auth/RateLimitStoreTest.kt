package org.companerodeescuela.api.auth

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RateLimitStoreTest {
    @Test
    fun `in-memory store increments and resets`() {
        val clock = MutableClock(Instant.parse("2026-11-03T12:00:00Z"))
        val store = InMemoryRateLimitStore(clock)

        val entry1 = store.increment("key1", Duration.ofMinutes(5), clock.instant())
        assertEquals(1, entry1.attempts)

        val entry2 = store.increment("key1", Duration.ofMinutes(5), clock.instant())
        assertEquals(2, entry2.attempts)

        store.reset("key1")
        val entry3 = store.increment("key1", Duration.ofMinutes(5), clock.instant())
        assertEquals(1, entry3.attempts)
    }

    @Test
    fun `in-memory store starts fresh window after expiry`() {
        val clock = MutableClock(Instant.parse("2026-11-03T12:00:00Z"))
        val store = InMemoryRateLimitStore(clock)
        val window = Duration.ofSeconds(10)

        store.increment("key1", window, clock.instant())
        store.increment("key1", window, clock.instant())
        clock.advance(Duration.ofSeconds(10))

        val entry = store.increment("key1", window, clock.instant())
        assertEquals(1, entry.attempts)
    }

    @Test
    fun `in-memory store current returns null for unknown key`() {
        val store = InMemoryRateLimitStore()
        assertNull(store.current("unknown"))
    }

    @Test
    fun `in-memory store pruneExpired removes stale entries`() {
        val clock = MutableClock(Instant.parse("2026-11-03T12:00:00Z"))
        val store = InMemoryRateLimitStore(clock)

        store.increment("old", Duration.ofMinutes(5), clock.instant())
        clock.advance(Duration.ofHours(2))
        store.pruneExpired(clock.instant())

        assertNull(store.current("old"))
    }

    @Test
    fun `distributed limiter blocks after max attempts via store`() {
        val clock = MutableClock(Instant.parse("2026-11-03T12:00:00Z"))
        val store = InMemoryRateLimitStore(clock)
        val limiter =
            LoginAttemptLimiter(
                maxAttempts = 3,
                window = Duration.ofMinutes(5),
                clock = clock,
                store = store,
            )

        assertNull(limiter.acquire("user", "10.0.0.1"))
        assertNull(limiter.acquire("user", "10.0.0.1"))
        assertNull(limiter.acquire("user", "10.0.0.1"))
        assertNotNull(limiter.acquire("user", "10.0.0.1"))
    }

    @Test
    fun `distributed limiter resets via store`() {
        val clock = MutableClock(Instant.parse("2026-11-03T12:00:00Z"))
        val store = InMemoryRateLimitStore(clock)
        val limiter =
            LoginAttemptLimiter(
                maxAttempts = 2,
                window = Duration.ofMinutes(5),
                clock = clock,
                store = store,
            )

        assertNull(limiter.acquire("user", "10.0.0.1"))
        assertNotNull(limiter.acquire("user", "10.0.0.1"))
        limiter.reset("user", "10.0.0.1")
        assertNull(limiter.acquire("user", "10.0.0.1"))
    }

    @Test
    fun `distributed limiter shares state across instances via same store`() {
        val clock = MutableClock(Instant.parse("2026-11-03T12:00:00Z"))
        val store = InMemoryRateLimitStore(clock)
        val limiter1 = LoginAttemptLimiter(maxAttempts = 3, clock = clock, store = store)
        val limiter2 = LoginAttemptLimiter(maxAttempts = 3, clock = clock, store = store)

        assertNull(limiter1.acquire("user", "10.0.0.1"))
        assertNull(limiter2.acquire("user", "10.0.0.1"))
        assertNull(limiter1.acquire("user", "10.0.0.1"))
        // limiter2 should see the 4th attempt and block, proving shared state
        assertNotNull(limiter2.acquire("user", "10.0.0.1"))
    }

    @Test
    fun `distributed limiter records progressive delays via store`() {
        val clock = MutableClock(Instant.parse("2026-11-03T12:00:00Z"))
        val store = InMemoryRateLimitStore(clock)
        val limiter =
            LoginAttemptLimiter(
                maxAttempts = 20,
                clock = clock,
                progressiveDelayBase = Duration.ofSeconds(1),
                progressiveDelayMaximum = Duration.ofSeconds(30),
                store = store,
            )

        val delays =
            (1..5).map {
                limiter.recordLoginFailure("ana", "10.0.0.1").progressiveDelay.seconds
            }

        assertEquals(listOf(1L, 2L, 4L, 8L, 16L), delays)
    }

    @Test
    fun `distributed limiter resetLogin clears all buckets`() {
        val clock = MutableClock(Instant.parse("2026-11-03T12:00:00Z"))
        val store = InMemoryRateLimitStore(clock)
        val limiter =
            LoginAttemptLimiter(
                maxAttempts = 3,
                clock = clock,
                store = store,
            )

        limiter.recordLoginFailure("ana", "10.0.0.1")
        limiter.recordLoginFailure("ana", "10.0.0.1")
        limiter.resetLogin("ana", "10.0.0.1")
        // After reset, a new failure starts at delay=1
        assertEquals(1L, limiter.recordLoginFailure("ana", "10.0.0.1").progressiveDelay.seconds)
    }

    private class MutableClock(
        private var instant: Instant,
    ) : Clock() {
        override fun getZone(): ZoneOffset = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId): Clock = this

        override fun instant(): Instant = instant

        fun advance(duration: Duration) {
            instant = instant.plus(duration)
        }
    }
}
