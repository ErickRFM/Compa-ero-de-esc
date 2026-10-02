package org.companerodeescuela.core.security

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionTokenInspectorTest {

    @Test
    fun `claims expose account identity and expiry`() {
        val token = token(
            userId = "student-1",
            displayName = "Ana López",
            expiresAt = 2_000_000_000,
        )

        val claims = SessionTokenInspector.inspect(token)

        assertEquals("student-1", claims?.userId)
        assertEquals("Ana López", claims?.displayName)
        assertEquals(2_000_000_000, claims?.expiresAtEpochSeconds)
    }

    @Test
    fun `expired and malformed values are not usable sessions`() {
        val clock = Clock.fixed(Instant.ofEpochSecond(200), ZoneOffset.UTC)

        assertFalse(
            SessionTokenInspector.isUsable(
                token("student-1", "Ana", 100),
                clock,
            ),
        )
        assertFalse(SessionTokenInspector.isUsable("invalid", clock))
        assertNull(SessionTokenInspector.inspect("invalid"))
        assertTrue(
            SessionTokenInspector.isUsable(
                token("student-1", "Ana", 1_000),
                clock,
            ),
        )
    }

    private fun token(
        userId: String,
        displayName: String,
        expiresAt: Long,
    ): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("{}".toByteArray())
        val payload = encoder.encodeToString(
            """{"sub":"$userId","display_name":"$displayName","exp":$expiresAt}"""
                .toByteArray(),
        )
        return listOf(header, payload, "test").joinToString(".")
    }
}
