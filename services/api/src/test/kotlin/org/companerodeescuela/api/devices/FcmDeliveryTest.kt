package org.companerodeescuela.api.devices

import java.net.URI
import java.security.KeyPairGenerator
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Base64
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import org.companerodeescuela.api.config.ConfigurationException

class FcmDeliveryTest {
    private class MutableClock(var current: Instant = Instant.parse("2026-10-10T18:00:00Z")) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = current
        fun advance(seconds: Long) { current = current.plusSeconds(seconds) }
    }

    private fun credentials(): FirebaseServiceAccount {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val pem = "-----BEGIN PRIVATE KEY-----\\n" +
            Base64.getEncoder().encodeToString(pair.private.encoded) + "\\n-----END PRIVATE KEY-----\\n"
        val content = kotlinx.serialization.json.buildJsonObject {
            put("project_id", kotlinx.serialization.json.JsonPrimitive("companero-test-project"))
            put("client_email", kotlinx.serialization.json.JsonPrimitive("push@companero-test-project.iam.gserviceaccount.com"))
            put("private_key", kotlinx.serialization.json.JsonPrimitive(pem))
            put("token_uri", kotlinx.serialization.json.JsonPrimitive("https://oauth2.googleapis.com/token"))
        }.toString()
        return FirebaseServiceAccount.parse(content)
    }

    @Test fun `OAuth service-account exchange signs assertion and reuses access token`() = runBlocking {
        val account = credentials()
        val calls = mutableListOf<String>()
        val sender = FcmHttpV1Gateway(account, object : PushHttpTransport {
            override suspend fun post(url: URI, headers: Map<String, String>, body: ByteArray): PushHttpResponse {
                calls += url.host
                if (url.host == "oauth2.googleapis.com") {
                    val assertion = body.toString(Charsets.UTF_8).substringAfter("assertion=")
                    val claims = Json.parseToJsonElement(String(Base64.getUrlDecoder()
                        .decode(assertion.split('.')[1]))).jsonObject
                    assertEquals("https://www.googleapis.com/auth/firebase.messaging", claims["scope"]?.jsonPrimitive?.content)
                    assertEquals("https://oauth2.googleapis.com/token", claims["aud"]?.jsonPrimitive?.content)
                    return PushHttpResponse(200, """{"access_token":"${"a".repeat(80)}","expires_in":3600}""")
                }
                assertEquals("Bearer ${"a".repeat(80)}", headers["Authorization"])
                assertEquals("fcm.googleapis.com",url.host)
                assertTrue(body.toString(Charsets.UTF_8).contains("fcm_test_token"))
                return PushHttpResponse(200, """{"name":"projects/test/messages/123"}""")
            }
        })
        assertEquals(PushDeliveryResult.ACCEPTED,sender.send("fcm_test_token_123456789","Aviso","Hay cambios"))
        assertEquals(PushDeliveryResult.ACCEPTED,sender.send("fcm_test_token_123456789","Aviso","Hay cambios"))
        assertEquals(listOf("oauth2.googleapis.com","fcm.googleapis.com","fcm.googleapis.com"),calls)
    }

    @Test fun `FCM invalid registration is distinguished from transient and malformed provider errors`() = runBlocking {
        val account = credentials()
        val errors = listOf(
            PushHttpResponse(404, """{"error":{"details":[{"@type":"type.googleapis.com/google.firebase.fcm.v1.FcmError","errorCode":"UNREGISTERED"}]}}"""),
            PushHttpResponse(503, """{"error":{"status":"UNAVAILABLE"}}"""),
            PushHttpResponse(404, """{"error":{"status":"NOT_FOUND"}}"""),
        )
        var index = 0
        val sender = FcmHttpV1Gateway(account, object : PushHttpTransport {
            override suspend fun post(url: URI, headers: Map<String, String>, body: ByteArray): PushHttpResponse =
                if (url.host == "oauth2.googleapis.com") PushHttpResponse(200,"""{"access_token":"${"a".repeat(80)}","expires_in":3600}""")
                else errors[index++]
        })
        val token = "fcm_test_token_123456789"
        assertEquals(PushDeliveryResult.INVALID_TOKEN,sender.send(token,"Aviso","Texto"))
        assertEquals(PushDeliveryResult.RETRYABLE,sender.send(token,"Aviso","Texto"))
        assertEquals(PushDeliveryResult.REJECTED,sender.send(token,"Aviso","Texto"))
    }

    @Test fun `credential parser rejects untrusted OAuth host`() {
        val raw = """{"project_id":"companero-prod","client_email":"push@companero-prod.iam.gserviceaccount.com","private_key":"some-key","token_uri":"https://malicious.example/token"}"""
        assertFailsWith<ConfigurationException> { FirebaseServiceAccount.parse(raw) }
    }

    @Test fun `in-memory outbox deduplicates queued events and deactivates invalid registrations`() = runBlocking {
        val tokens = InMemoryDeviceTokenRepository()
        val outbox = InMemoryPushOutboxRepository()
        val clock = MutableClock()
        val fcm = "fcm_test_token_123456789"
        tokens.register("NATIVE:student-a",fcm,"android",null,"phone-a")
        var sends = 0
        val service = PushOutboxService(tokens,outbox,PushGateway { _, _, _ ->
            sends++
            PushDeliveryResult.INVALID_TOKEN
        },clock)
        assertEquals(1,service.enqueueForOwner("event-1","NATIVE:student-a","Nuevo aviso","Tienes un aviso"))
        assertEquals(0,service.enqueueForOwner("event-1","NATIVE:student-a","Nuevo aviso","Tienes un aviso"))
        assertTrue(service.dispatchNext())
        assertEquals(1,sends)
        assertTrue(tokens.listTokens(setOf("NATIVE:student-a")).isEmpty())
        assertFalse(service.dispatchNext())
    }

    @Test fun `lease fencing prevents stale acknowledgements and transient retries are delayed`() = runBlocking {
        val clock = MutableClock()
        val repo = InMemoryPushOutboxRepository()
        val id = "f".repeat(64)
        val row = PushOutboxItem(id,"NATIVE:student-a","fcm_test_token_123456789","Aviso","Texto")
        assertTrue(repo.enqueue(row))
        val first = assertNotNull(repo.claim(clock.instant()))
        assertEquals(null,repo.claim(clock.instant()))
        clock.advance(91)
        val second = assertNotNull(repo.claim(clock.instant()))
        assertFalse(repo.finish(first,PushDeliveryResult.ACCEPTED,clock.instant()))
        assertTrue(repo.finish(second,PushDeliveryResult.RETRYABLE,clock.instant()))
        assertEquals(null,repo.claim(clock.instant()))
        clock.advance(61)
        val third = assertNotNull(repo.claim(clock.instant()))
        assertEquals(3,third.attempts)
        assertTrue(repo.finish(third,PushDeliveryResult.ACCEPTED,clock.instant()))
        assertEquals(null,repo.claim(clock.instant()))
    }
}
