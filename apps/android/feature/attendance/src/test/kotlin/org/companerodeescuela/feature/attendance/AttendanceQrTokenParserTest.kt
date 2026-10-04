package org.companerodeescuela.feature.attendance

import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AttendanceQrTokenParserTest {
    @Test
    fun `extracts only the session routing hint from a v1 token`() {
        val encodedSession = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("session-123".toByteArray(StandardCharsets.UTF_8))
        val token = "v1." + encodedSession + ".nonce.100.120.signature"

        assertEquals("session-123", AttendanceQrTokenParser.sessionId(token))
    }

    @Test
    fun `rejects malformed tokens locally`() {
        assertNull(AttendanceQrTokenParser.sessionId("not-a-token"))
    }
}
