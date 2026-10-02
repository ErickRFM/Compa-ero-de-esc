package org.companerodeescuela.api.auth

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LoginAttemptLimiterTest {

    @Test
    fun `allows attempts until the configured limit and then returns retry seconds`() {
        val clock = MutableClock(Instant.parse("2026-10-02T12:00:00Z"))
        val limiter = LoginAttemptLimiter(
            maxAttempts = 2,
            window = Duration.ofMinutes(5),
            clock = clock,
        )

        assertNull(limiter.acquire("ana", "10.0.0.1"))
        assertNull(limiter.acquire("ana", "10.0.0.1"))
        assertEquals(300, limiter.acquire("ana", "10.0.0.1"))

        clock.advance(Duration.ofSeconds(30))
        assertEquals(270, limiter.acquire("ana", "10.0.0.1"))
    }

    @Test
    fun `same school address does not share buckets across different usernames`() {
        val limiter = LoginAttemptLimiter(maxAttempts = 1)

        assertNull(limiter.acquire("ana", "10.0.0.1"))
        assertEquals(300, limiter.acquire("ana", "10.0.0.1"))
        assertNull(limiter.acquire("luis", "10.0.0.1"))
    }

    @Test
    fun `successful login reset clears the bucket`() {
        val limiter = LoginAttemptLimiter(maxAttempts = 1)

        assertNull(limiter.acquire("ana", "10.0.0.1"))
        limiter.reset("ana", "10.0.0.1")
        assertNull(limiter.acquire("ana", "10.0.0.1"))
    }

    @Test
    fun `window expiry starts a fresh bucket`() {
        val clock = MutableClock(Instant.parse("2026-10-02T12:00:00Z"))
        val limiter = LoginAttemptLimiter(
            maxAttempts = 1,
            window = Duration.ofSeconds(10),
            clock = clock,
        )

        assertNull(limiter.acquire("ana", "10.0.0.1"))
        assertEquals(10, limiter.acquire("ana", "10.0.0.1"))
        clock.advance(Duration.ofSeconds(10))
        assertNull(limiter.acquire("ana", "10.0.0.1"))
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
