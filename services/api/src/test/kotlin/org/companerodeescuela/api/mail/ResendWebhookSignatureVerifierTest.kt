package org.companerodeescuela.api.mail

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResendWebhookSignatureVerifierTest {
    private val now = Instant.parse("2026-11-09T22:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val key = ByteArray(32) { it.toByte() }
    private val secret = "whsec_" + Base64.getEncoder().encodeToString(key)
    private val messageId = "msg_123456789"
    private val timestamp = now.epochSecond.toString()
    private val body = """{"type":"email.delivered","created_at":"2026-11-09T22:00:00Z","data":{"email_id":"email-123"}}"""

    @Test
    @DisplayName("Accepts the exact raw body signed with the active Svix secret")
    fun acceptsValidSignature() {
        val verifier = ResendWebhookSignatureVerifier(secret.toCharArray(), clock)
        assertTrue(verifier.verify(body.toByteArray(), messageId, timestamp, sign(body)))
    }

    @Test
    @DisplayName("Rejects modified payloads and invalid signatures")
    fun rejectsModifiedPayloadAndSignature() {
        val verifier = ResendWebhookSignatureVerifier(secret.toCharArray(), clock)
        assertFalse(verifier.verify((body + " ").toByteArray(), messageId, timestamp, sign(body)))
        assertFalse(verifier.verify(body.toByteArray(), messageId, timestamp, "v1,ZmFrZQ=="))
    }

    @Test
    @DisplayName("Rejects stale timestamps, missing headers and oversized payloads")
    fun rejectsReplayAndMalformedRequests() {
        val verifier = ResendWebhookSignatureVerifier(secret.toCharArray(), clock)
        val stale = (now.epochSecond - 301).toString()
        assertFalse(verifier.verify(body.toByteArray(), messageId, stale, sign(body, stale)))
        assertFalse(verifier.verify(body.toByteArray(), null, timestamp, sign(body)))
        assertFalse(verifier.verify(body.toByteArray(), messageId, timestamp, null))
        assertFalse(verifier.verify(ByteArray(32 * 1024 + 1), messageId, timestamp, sign(body)))
    }

    @Test
    @DisplayName("Extracts invitation correlation from Resend's object tags and legacy tag arrays")
    fun extractsInvitationIdFromProviderTags() {
        val invitationId = "A".repeat(43)
        val objectTags =
            (Json.parseToJsonElement(
                """{"tags":{"category":"companero_invitation","invitation_id":"$invitationId"}}""",
            ) as JsonObject)["tags"]
        val arrayTags =
            (Json.parseToJsonElement(
                """{"tags":[{"name":"category","value":"companero_invitation"},{"name":"invitation_id","value":"$invitationId"}]}""",
            ) as JsonObject)["tags"]
        val malformed =
            (Json.parseToJsonElement("""{"tags":"invalid"}""") as JsonObject)["tags"]

        assertTrue(extractResendInvitationId(objectTags) == invitationId)
        assertTrue(extractResendInvitationId(arrayTags) == invitationId)
        assertFalse(extractResendInvitationId(malformed) != null)
    }

    private fun sign(
        payload: String,
        signedTimestamp: String = timestamp,
    ): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        val signed = "$messageId.$signedTimestamp.$payload".toByteArray(Charsets.UTF_8)
        return "v1," + Base64.getEncoder().encodeToString(mac.doFinal(signed))
    }
}
