package org.companerodeescuela.api.integrations.matica

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MaticaLinkCodeVerifierTest {
    private val now = Instant.parse("2026-11-08T12:00:00Z")
    private val secret = "test-secret-with-at-least-32-bytes-long".toByteArray()
    private val verifier = MaticaLinkCodeVerifier(secret)
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    @Test
    fun `verifies issued format and rejects tampering`() {
        val subject = UUID.randomUUID()
        val nonce = UUID.randomUUID()
        val code = issue(subject, nonce, now.epochSecond + 600)

        assertEquals(subject.toString(), verifier.verify(code, now)?.subject)
        assertNull(verifier.verify("$code!", now))
    }

    @Test
    fun `rejects long expiry and expired code`() {
        val subject = UUID.randomUUID()
        val nonce = UUID.randomUUID()

        assertNull(verifier.verify(issue(subject, nonce, now.epochSecond + 661), now))
        assertNull(verifier.verify(issue(subject, nonce, now.epochSecond), now))
    }

    private fun issue(
        subject: UUID,
        nonce: UUID,
        expiresAt: Long,
    ): String {
        val claimsJson =
            Json.encodeToString(
                MaticaLinkClaims.serializer(),
                MaticaLinkClaims(subject.toString(), expiresAt, nonce.toString()),
            )
        val payload = encoder.encodeToString(claimsJson.toByteArray())
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return "$payload.${encoder.encodeToString(mac.doFinal(payload.toByteArray()))}"
    }
}
