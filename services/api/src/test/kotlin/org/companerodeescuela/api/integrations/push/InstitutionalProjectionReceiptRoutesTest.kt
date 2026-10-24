package org.companerodeescuela.api.integrations.push

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.companerodeescuela.api.plugins.ApiJson
import org.companerodeescuela.shared.contracts.InstitutionalEventOperation
import org.companerodeescuela.shared.contracts.InstitutionalPushEvent
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(named = "V12_TEST_MONGODB_URI", matches = "mongodb://127\\.0\\.0\\.1:27017")
@Timeout(120)
class InstitutionalProjectionReceiptRoutesTest {
    private val now = Instant.parse("2026-10-24T12:00:00Z")
    private val secret = ByteArray(32) { (it + 17).toByte() }
    private val keyId = "receipt-test-key"
    private val tenantId = UUID.randomUUID().toString()

    @Test
    fun `unsigned projection status requests are unauthorized`() = withMongo { db ->
        val inbox = prepare(db)
        application(inbox) {
            val response = client.post(InstitutionalPushAuthenticator.PROJECTION_STATUS_PATH) {
                contentType(ContentType.Application.Json)
                setBody(requestBody(listOf(UUID.randomUUID().toString())))
            }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    @Test
    fun `receipt lookup is isolated to the credential tenant`() = withMongo { db ->
        val inbox = prepare(db)
        val otherTenantEvent = storeEvent(inbox, UUID.randomUUID().toString())
        application(inbox) {
            val response = signedRequest(listOf(otherTenantEvent))
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(Json.parseToJsonElement(response.bodyAsText()).jsonObject.getValue("receipts").jsonArray.isEmpty())
        }
    }

    @Test
    fun `valid signed query returns the durable projection receipt`() = withMongo { db ->
        val inbox = prepare(db)
        val eventId = storeEvent(inbox, tenantId)
        application(inbox) {
            val response = signedRequest(listOf(eventId))
            assertEquals(HttpStatusCode.OK, response.status)
            val result = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("siii.projection_status.v1", result.getValue("contract").jsonPrimitive.content)
            val receipt = result.getValue("receipts").jsonArray.single().jsonObject
            assertEquals(eventId, receipt.getValue("eventId").jsonPrimitive.content)
            assertEquals("pending", receipt.getValue("status").jsonPrimitive.content)
            assertEquals(1, receipt.getValue("sourceVersion").jsonPrimitive.content.toInt())
            assertEquals(0, receipt.getValue("attempts").jsonPrimitive.content.toInt())
            assertEquals(now.epochSecond, receipt.getValue("receivedAtEpochSeconds").jsonPrimitive.content.toLong())
        }
    }

    @Test
    fun `unknown event identifiers remain absent from the receipt result`() = withMongo { db ->
        val inbox = prepare(db)
        val unknownEvent = UUID.randomUUID().toString()
        application(inbox) {
            val response = signedRequest(listOf(unknownEvent))
            assertEquals(HttpStatusCode.OK, response.status)
            val result = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertTrue(result.getValue("receipts").jsonArray.isEmpty())
        }
    }

    @Test
    fun `revoked integration credential cannot read projection receipts`() = withMongo { db ->
        val inbox = prepare(db)
        val eventId = storeEvent(inbox, tenantId)
        assertTrue(inbox.revokeCredential(keyId, now))
        application(inbox) {
            val response = signedRequest(listOf(eventId))
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    private suspend fun prepare(db: MongoDatabase): MongoInstitutionalPushRepository {
        val inbox = MongoInstitutionalPushRepository(db, InstitutionalSecretProtector(ByteArray(32) { it.toByte() }))
        inbox.putCredential(
            IntegrationCredential(keyId, tenantId, secret.copyOf(), setOf("group:write"), true, null),
            now,
        )
        return inbox
    }

    private suspend fun storeEvent(inbox: MongoInstitutionalPushRepository, tenant: String): String {
        val eventId = UUID.randomUUID().toString()
        val event =
            InstitutionalPushEvent(
                schemaVersion = 1,
                eventId = eventId,
                entityType = "group",
                externalId = UUID.randomUUID().toString(),
                sourceVersion = 1,
                occurredAtEpochSeconds = now.epochSecond,
                operation = InstitutionalEventOperation.UPSERT,
                payload = kotlinx.serialization.json.buildJsonObject {
                    put("contract", kotlinx.serialization.json.JsonPrimitive("fixture.v1"))
                },
            )
        val body = Json.encodeToString(InstitutionalPushEvent.serializer(), event).toByteArray()
        inbox.accept(
            InstitutionalEventRecord(
                tenant,
                keyId,
                event,
                MessageDigest.getInstance("SHA-256").digest(body).joinToString("") { "%02x".format(it) },
                now,
            ),
        )
        return eventId
    }

    private fun requestBody(eventIds: List<String>) =
        """{"contract":"siii.projection_status_request.v1","eventIds":${Json.encodeToString(eventIds)}}"""

    private fun io.ktor.client.request.HttpRequestBuilder.signed(body: String) {
        val bytes = body.toByteArray()
        val timestamp = now.epochSecond.toString()
        val nonce = UUID.randomUUID().toString().replace("-", "")
        val canonical =
            InstitutionalPushAuthenticator.canonicalMessage(
                "POST",
                InstitutionalPushAuthenticator.PROJECTION_STATUS_PATH,
                timestamp,
                nonce,
                bytes,
            )
        contentType(ContentType.Application.Json)
        header(INTEGRATION_KEY_ID_HEADER, keyId)
        header(INTEGRATION_TIMESTAMP_HEADER, timestamp)
        header(INTEGRATION_NONCE_HEADER, nonce)
        header(INTEGRATION_SIGNATURE_HEADER, InstitutionalPushAuthenticator.sign(secret, canonical))
        setBody(body)
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.signedRequest(eventIds: List<String>) =
        client.post(InstitutionalPushAuthenticator.PROJECTION_STATUS_PATH) {
            signed(requestBody(eventIds))
        }

    private fun application(inbox: MongoInstitutionalPushRepository, block: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) =
        testApplication {
            application {
                install(ContentNegotiation) { json(ApiJson) }
                routing {
                    institutionalPushRoutes(
                        InstitutionalPushAuthenticator(inbox, inbox, Clock.fixed(now, ZoneOffset.UTC)),
                        InstitutionalPushService(inbox, Clock.fixed(now, ZoneOffset.UTC)),
                        inbox,
                        inbox,
                        Clock.fixed(now, ZoneOffset.UTC),
                    )
                }
            }
            block()
        }

    private fun withMongo(block: suspend (MongoDatabase) -> Unit) =
        runBlocking {
            val uri = requireNotNull(System.getenv("V12_TEST_MONGODB_URI"))
            require(uri == "mongodb://127.0.0.1:27017")
            MongoClient.create(uri).use { client ->
                val db = client.getDatabase("v12_test_${UUID.randomUUID().toString().replace("-", "")}")
                try {
                    block(db)
                } finally {
                    db.drop()
                }
            }
        }
}
