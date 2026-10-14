package org.companerodeescuela.api.integrations.push

import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.companerodeescuela.api.plugins.ApiJson
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class InstitutionalPushRoutesTest {
    private val now = Instant.parse("2026-10-14T12:00:00Z")
    private val secret = "route-test-secret-with-at-least-32-bytes".toByteArray()

    @Test
    fun `signed push is accepted and replay is rejected`() =
        testApplication {
            val store = TestPushStore()
            val credential =
                IntegrationCredential(
                    keyId = "key-route",
                    tenantId = "tenant-route",
                    secret = secret,
                    scopes = setOf("schedule:write"),
                    enabled = true,
                    expiresAtEpochSeconds = null,
                )
            val auth =
                InstitutionalPushAuthenticator(
                    store.apply { credentials[credential.keyId] = credential },
                    store,
                    Clock.fixed(now, ZoneOffset.UTC),
                )
            val service = InstitutionalPushService(store, Clock.fixed(now, ZoneOffset.UTC))
            application {
                install(ContentNegotiation) { json(ApiJson) }
                routing { institutionalPushRoutes(auth, service, store) }
            }
            val body =
                """{"schemaVersion":1,"eventId":"evt-route","entityType":"schedule","externalId":"class-01","sourceVersion":1,"occurredAtEpochSeconds":${now.epochSecond},"operation":"upsert","payload":{"subject":"Math"}}"""
                    .toByteArray()

            val accepted =
                client.post(InstitutionalPushAuthenticator.EVENTS_PATH) {
                    signedHeaders(body)
                    setBody(body)
                }
            val replay =
                client.post(InstitutionalPushAuthenticator.EVENTS_PATH) {
                    signedHeaders(body)
                    setBody(body)
                }

            assertEquals(HttpStatusCode.Accepted, accepted.status)
            assertEquals(HttpStatusCode.Unauthorized, replay.status)
        }

    @Test
    fun `unsigned requests do not reach persistence`() =
        testApplication {
            val store = TestPushStore()
            val auth = InstitutionalPushAuthenticator(store, store, Clock.fixed(now, ZoneOffset.UTC))
            application {
                install(ContentNegotiation) { json(ApiJson) }
                routing { institutionalPushRoutes(auth, InstitutionalPushService(store), store) }
            }
            val response =
                client.post(InstitutionalPushAuthenticator.EVENTS_PATH) {
                    contentType(ContentType.Application.Json)
                    setBody("{}")
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals(0, store.events.size)
        }

    @Test
    fun `rate limited client is rejected before reading the request body`() =
        testApplication {
            val store = TestPushStore().apply { retryAfterSeconds = 30 }
            val auth = InstitutionalPushAuthenticator(store, store, Clock.fixed(now, ZoneOffset.UTC))
            application {
                install(ContentNegotiation) { json(ApiJson) }
                routing { institutionalPushRoutes(auth, InstitutionalPushService(store), store) }
            }
            val response =
                client.post(InstitutionalPushAuthenticator.EVENTS_PATH) {
                    contentType(ContentType.Application.Json)
                    setBody("not-json")
                }

            assertEquals(HttpStatusCode.TooManyRequests, response.status)
            assertEquals("30", response.headers[HttpHeaders.RetryAfter])
            assertEquals(0, store.events.size)
        }

    private fun io.ktor.client.request.HttpRequestBuilder.signedHeaders(body: ByteArray) {
        val timestamp = now.epochSecond.toString()
        val nonce = "route-nonce-123456"
        val canonical =
            InstitutionalPushAuthenticator.canonicalMessage(
                "POST",
                InstitutionalPushAuthenticator.EVENTS_PATH,
                timestamp,
                nonce,
                body,
            )
        contentType(ContentType.Application.Json)
        header(INTEGRATION_KEY_ID_HEADER, "key-route")
        header(INTEGRATION_TIMESTAMP_HEADER, timestamp)
        header(INTEGRATION_NONCE_HEADER, nonce)
        header(INTEGRATION_SIGNATURE_HEADER, InstitutionalPushAuthenticator.sign(secret, canonical))
    }

    private class TestPushStore :
        IntegrationCredentialRepository,
        IntegrationNonceRepository,
        InstitutionalEventRepository,
        InstitutionalPushRateLimiter {
        val credentials = mutableMapOf<String, IntegrationCredential>()
        val nonces = mutableSetOf<Pair<String, String>>()
        val events = mutableMapOf<Pair<String, String>, InstitutionalEventRecord>()
        var retryAfterSeconds: Long? = null

        override suspend fun findCredential(keyId: String): IntegrationCredential? = credentials[keyId]

        override suspend fun reserveNonce(
            credentialId: String,
            nonce: String,
            expiresAt: Instant,
        ): Boolean = synchronized(nonces) { nonces.add(credentialId to nonce) }

        override suspend fun accept(record: InstitutionalEventRecord): InstitutionalEventAcceptance =
            synchronized(events) {
                val key = record.tenantId to record.event.eventId
                val prior = events[key]
                if (prior != null) {
                    if (prior.payloadSha256 == record.payloadSha256) {
                        InstitutionalEventAcceptance.DUPLICATE
                    } else {
                        InstitutionalEventAcceptance.CONFLICT
                    }
                } else {
                    events[key] = record
                    InstitutionalEventAcceptance.ACCEPTED
                }
            }

        override suspend fun acquire(clientAddress: String): Long? = retryAfterSeconds
    }
}
