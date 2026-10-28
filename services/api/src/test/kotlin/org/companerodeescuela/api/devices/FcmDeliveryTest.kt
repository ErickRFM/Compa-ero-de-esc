package org.companerodeescuela.api.devices

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.companerodeescuela.api.config.ConfigurationException
import java.net.URI
import java.security.KeyPairGenerator
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FcmDeliveryTest {
    private class MutableClock(
        var current: Instant = Instant.parse("2026-10-10T18:00:00Z"),
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = current

        fun advance(seconds: Long) {
            current = current.plusSeconds(seconds)
        }
    }

    private fun credentials(): FirebaseServiceAccount {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val pem =
            ("-----BEGIN " + "PRIVATE KEY-----\n") +
                Base64.getEncoder().encodeToString(pair.private.encoded) + ("\n-----END " + "PRIVATE KEY-----\n")
        val content =
            kotlinx.serialization.json
                .buildJsonObject {
                    put("project_id", kotlinx.serialization.json.JsonPrimitive("companero-test-project"))
                    put("client_email", kotlinx.serialization.json.JsonPrimitive("push@companero-test-project.iam.gserviceaccount.com"))
                    put("private_key", kotlinx.serialization.json.JsonPrimitive(pem))
                    put("token_uri", kotlinx.serialization.json.JsonPrimitive("https://oauth2.googleapis.com/token"))
                }.toString()
        return FirebaseServiceAccount.parse(content)
    }

    @Test fun `OAuth service-account exchange signs assertion and reuses access token`() =
        runBlocking {
            val account = credentials()
            val calls = mutableListOf<String>()
            var fcmPayload: kotlinx.serialization.json.JsonObject? = null
            val sender =
                FcmHttpV1Gateway(
                    account,
                    object : PushHttpTransport {
                        override suspend fun post(
                            url: URI,
                            headers: Map<String, String>,
                            body: ByteArray,
                        ): PushHttpResponse {
                            calls += url.host
                            if (url.host == "oauth2.googleapis.com") {
                                val assertion = body.toString(Charsets.UTF_8).substringAfter("assertion=")
                                val claims =
                                    Json
                                        .parseToJsonElement(
                                            String(
                                                Base64
                                                    .getUrlDecoder()
                                                    .decode(assertion.split('.')[1]),
                                            ),
                                        ).jsonObject
                                assertEquals("https://www.googleapis.com/auth/firebase.messaging", claims["scope"]?.jsonPrimitive?.content)
                                assertEquals("https://oauth2.googleapis.com/token", claims["aud"]?.jsonPrimitive?.content)
                                return PushHttpResponse(200, """{"access_token":"${"a".repeat(80)}","expires_in":3600}""")
                            }
                            assertEquals("Bearer ${"a".repeat(80)}", headers["Authorization"])
                            assertEquals("fcm.googleapis.com", url.host)
                            fcmPayload = Json.parseToJsonElement(body.toString(Charsets.UTF_8)).jsonObject
                            assertTrue(body.toString(Charsets.UTF_8).contains("fcm_test_token"))
                            return PushHttpResponse(200, """{"name":"projects/test/messages/123"}""")
                        }
                    },
                )
            assertEquals(PushDeliveryResult.ACCEPTED, sender.send("fcm_test_token_123456789", "Aviso", "Hay cambios"))
            assertEquals(PushDeliveryResult.ACCEPTED, sender.send("fcm_test_token_123456789", "Aviso", "Hay cambios"))
            assertEquals(listOf("oauth2.googleapis.com", "fcm.googleapis.com", "fcm.googleapis.com"), calls)
            assertEquals(null, fcmPayload?.get("message")?.jsonObject?.get("data"))
            assertEquals(
                null,
                fcmPayload
                    ?.get("message")
                    ?.jsonObject
                    ?.get("android")
                    ?.jsonObject
                    ?.get("notification"),
            )
        }

    @Test fun `attendance data survives the outbox and becomes a notification click action`() =
        runBlocking {
            val devices = InMemoryDeviceTokenRepository()
            val outbox = InMemoryPushOutboxRepository()
            val token = "fcm_test_token_123456789"
            devices.register("NATIVE:student-a", token, "android", null, "device-a")
            val expectedData = mapOf("type" to "attendance_session_opened", "sessionId" to "11111111-1111-4111-8111-111111111111")
            var deliveredData: Map<String, String>? = null
            val service =
                PushOutboxService(
                    devices,
                    outbox,
                    object : PushGateway {
                        override suspend fun send(
                            token: String,
                            title: String,
                            body: String,
                        ) = PushDeliveryResult.ACCEPTED

                        override suspend fun send(
                            token: String,
                            title: String,
                            body: String,
                            data: Map<String, String>,
                        ): PushDeliveryResult {
                            deliveredData = data
                            return PushDeliveryResult.ACCEPTED
                        }
                    },
                    authorizeRecipient = { true },
                    authorizeSession = { _, _ -> true },
                )
            assertEquals(
                1,
                service.enqueueForOwner("attendance-session-open:test", "NATIVE:student-a", "Pase", "Toca para revisar", expectedData),
            )
            assertTrue(service.dispatchNext())
            assertEquals(expectedData, deliveredData)

            val account = credentials()
            var message: kotlinx.serialization.json.JsonObject? = null
            val gateway =
                FcmHttpV1Gateway(
                    account,
                    object : PushHttpTransport {
                        override suspend fun post(
                            url: URI,
                            headers: Map<String, String>,
                            body: ByteArray,
                        ): PushHttpResponse {
                            if (url.host ==
                                "oauth2.googleapis.com"
                            ) {
                                return PushHttpResponse(200, """{"access_token":"${"a".repeat(80)}","expires_in":3600}""")
                            }
                            message = Json.parseToJsonElement(body.toString(Charsets.UTF_8)).jsonObject["message"]?.jsonObject
                            return PushHttpResponse(200, "{}")
                        }
                    },
                )
            assertEquals(PushDeliveryResult.ACCEPTED, gateway.send(token, "Pase", "Toca para revisar", deliveredData!!))
            assertEquals(expectedData, message?.get("data")?.jsonObject?.mapValues { it.value.jsonPrimitive.content })
            val androidNotification =
                message
                    ?.get("android")
                    ?.jsonObject
                    ?.get("notification")
                    ?.jsonObject
            assertEquals("companero_academic_updates", androidNotification?.get("channel_id")?.jsonPrimitive?.content)
            assertEquals(
                "attendance-session:${expectedData.getValue("sessionId")}",
                androidNotification?.get("tag")?.jsonPrimitive?.content,
            )
            assertEquals(
                "org.companerodeescuela.OPEN_ATTENDANCE",
                message
                    ?.get("android")
                    ?.jsonObject
                    ?.get("notification")
                    ?.jsonObject
                    ?.get("click_action")
                    ?.jsonPrimitive
                    ?.content,
            )
        }

    @Test fun `FCM invalid registration is distinguished from transient and malformed provider errors`() =
        runBlocking {
            val account = credentials()
            val errors =
                listOf(
                    PushHttpResponse(
                        404,
                        """{"error":{"details":[{"@type":"type.googleapis.com/google.firebase.fcm.v1.FcmError","errorCode":"UNREGISTERED"}]}}""",
                    ),
                    PushHttpResponse(503, """{"error":{"status":"UNAVAILABLE"}}"""),
                    PushHttpResponse(404, """{"error":{"status":"NOT_FOUND"}}"""),
                )
            var index = 0
            val sender =
                FcmHttpV1Gateway(
                    account,
                    object : PushHttpTransport {
                        override suspend fun post(
                            url: URI,
                            headers: Map<String, String>,
                            body: ByteArray,
                        ): PushHttpResponse =
                            if (url.host ==
                                "oauth2.googleapis.com"
                            ) {
                                PushHttpResponse(200, """{"access_token":"${"a".repeat(80)}","expires_in":3600}""")
                            } else {
                                errors[index++]
                            }
                    },
                )
            val token = "fcm_test_token_123456789"
            assertEquals(PushDeliveryResult.INVALID_TOKEN, sender.send(token, "Aviso", "Texto"))
            assertEquals(PushDeliveryResult.RETRYABLE, sender.send(token, "Aviso", "Texto"))
            assertEquals(PushDeliveryResult.REJECTED, sender.send(token, "Aviso", "Texto"))
        }

    @Test fun `credential parser rejects untrusted OAuth host`() {
        val raw =
            """{"project_id":"companero-prod","client_email":"push@companero-prod.iam.gserviceaccount.com", """ +
                """"private_key":"some-key","token_uri":"https://malicious.example/token"}"""
        assertFailsWith<ConfigurationException> { FirebaseServiceAccount.parse(raw) }
    }

    @Test fun `in-memory outbox deduplicates queued events and deactivates invalid registrations`() =
        runBlocking {
            val tokens = InMemoryDeviceTokenRepository()
            val outbox = InMemoryPushOutboxRepository()
            val clock = MutableClock()
            val fcm = "fcm_test_token_123456789"
            tokens.register("NATIVE:student-a", fcm, "android", null, "phone-a")
            var sends = 0
            val service =
                PushOutboxService(
                    tokens,
                    outbox,
                    PushGateway { _, _, _ ->
                        sends++
                        PushDeliveryResult.INVALID_TOKEN
                    },
                    clock,
                    authorizeRecipient = { true },
                    authorizeSession = { _, _ -> true },
                )
            assertEquals(1, service.enqueueForOwner("event-1", "NATIVE:student-a", "Nuevo aviso", "Tienes un aviso"))
            assertEquals(0, service.enqueueForOwner("event-1", "NATIVE:student-a", "Nuevo aviso", "Tienes un aviso"))
            assertTrue(service.dispatchNext())
            assertEquals(1, sends)
            assertTrue(tokens.listTokens(setOf("NATIVE:student-a")).isEmpty())
            assertFalse(service.dispatchNext())
        }

    @Test fun `revoked student identity cannot receive a queued device notification`() =
        runBlocking {
            val devices = InMemoryDeviceTokenRepository()
            val outbox = InMemoryPushOutboxRepository()
            val token = "fcm_test_token_123456789"
            devices.register("NATIVE:student-revoked", token, "android", null, "device-1")
            var sends = 0
            val service =
                PushOutboxService(
                    devices,
                    outbox,
                    PushGateway { _, _, _ ->
                        sends++
                        PushDeliveryResult.ACCEPTED
                    },
                    authorizeRecipient = { false },
                )
            assertEquals(1, service.enqueueForOwner("event-revoked", "NATIVE:student-revoked", "Aviso", "Texto"))
            assertTrue(service.dispatchNext())
            assertEquals(0, sends)
            // A suspended account is not sufficient reason to delete a valid FCM token.
            assertEquals(listOf(token), devices.listTokens(setOf("NATIVE:student-revoked")))
            assertFalse(service.dispatchNext())
        }

    @Test fun `logged out session cannot receive push even if registration remains`() =
        runBlocking {
            val devices = InMemoryDeviceTokenRepository()
            val outbox = InMemoryPushOutboxRepository()
            val token = "fcm_test_token_123456789"
            devices.register("NATIVE:student-a", token, "android", null, "device-1", "session-revoked")
            var sent = false
            val service =
                PushOutboxService(
                    devices,
                    outbox,
                    PushGateway { _, _, _ ->
                        sent = true
                        PushDeliveryResult.ACCEPTED
                    },
                    authorizeRecipient = { true },
                    authorizeSession = { _, _ -> false },
                )
            assertEquals("session-revoked", devices.sessionIdFor("NATIVE:student-a", token))
            assertEquals(1, service.enqueueForOwner("event-logout", "NATIVE:student-a", "Aviso", "Texto"))
            assertTrue(service.dispatchNext())
            assertFalse(sent)
            assertFalse(service.dispatchNext())
        }

    @Test fun `lease fencing prevents stale acknowledgements and transient retries are delayed`() =
        runBlocking {
            val clock = MutableClock()
            val repo = InMemoryPushOutboxRepository()
            val id = "f".repeat(64)
            val row = PushOutboxItem(id, "NATIVE:student-a", "fcm_test_token_123456789", "Aviso", "Texto")
            assertTrue(repo.enqueue(row))
            val first = assertNotNull(repo.claim(clock.instant()))
            assertEquals(null, repo.claim(clock.instant()))
            clock.advance(91)
            val second = assertNotNull(repo.claim(clock.instant()))
            assertFalse(repo.finish(first, PushDeliveryResult.ACCEPTED, clock.instant()))
            assertTrue(repo.finish(second, PushDeliveryResult.RETRYABLE, clock.instant()))
            assertEquals(null, repo.claim(clock.instant()))
            clock.advance(61)
            val third = assertNotNull(repo.claim(clock.instant()))
            assertEquals(3, third.attempts)
            assertTrue(repo.finish(third, PushDeliveryResult.ACCEPTED, clock.instant()))
            assertEquals(null, repo.claim(clock.instant()))
        }
}
