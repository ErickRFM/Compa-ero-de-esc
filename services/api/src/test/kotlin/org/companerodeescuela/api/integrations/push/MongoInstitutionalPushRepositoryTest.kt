package org.companerodeescuela.api.integrations.push

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.bson.Document
import org.companerodeescuela.shared.contracts.InstitutionalEventOperation
import org.companerodeescuela.shared.contracts.InstitutionalPushEvent
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(named = "V12_TEST_MONGODB_URI", matches = ".+")
@Timeout(120)
class MongoInstitutionalPushRepositoryTest {
    @Test
    fun `Mongo credential encryption revocation replay and event idempotency are durable`() =
        database { database ->
            val masterKey = ByteArray(32) { it.toByte() }
            val repository = MongoInstitutionalPushRepository(database, InstitutionalSecretProtector(masterKey))
            val credential =
                IntegrationCredential(
                    keyId = "institution-key",
                    tenantId = "tenant-a",
                    secret = ByteArray(32) { (it + 1).toByte() },
                    scopes = setOf("schedule:write"),
                    enabled = true,
                    expiresAtEpochSeconds = null,
                )
            repository.putCredential(credential, Instant.parse("2026-10-14T12:00:00Z"))

            val loaded = assertNotNull(repository.findCredential(credential.keyId))
            assertEquals(credential.tenantId, loaded.tenantId)
            assertTrue(credential.secret.contentEquals(loaded.secret))
            val rawStoredSecret =
                database
                    .getCollection<org.bson.Document>("institutional_integration_credentials")
                    .find(
                        com.mongodb.client.model.Filters
                            .eq("_id", credential.keyId),
                    )
            assertTrue(rawStoredSecret.firstOrNull()?.getString("secretCiphertext")?.startsWith("v1.") == true)

            val accepted =
                coroutineScope {
                    (1..24)
                        .map {
                            async {
                                repository.reserveNonce(credential.keyId, "one-time-nonce-12345", Instant.now().plusSeconds(300))
                            }
                        }.awaitAll()
                }
            assertEquals(1, accepted.count { it })
            val withinRateLimit =
                coroutineScope {
                    (1..120)
                        .map { async { repository.acquire("127.0.0.1") } }
                        .awaitAll()
                }
            assertEquals(0, withinRateLimit.count { it != null })
            assertTrue(repository.acquire("127.0.0.1") != null)

            val event =
                InstitutionalPushEvent(
                    schemaVersion = 1,
                    eventId = "event-1",
                    entityType = "schedule",
                    externalId = "course-1",
                    sourceVersion = 1,
                    occurredAtEpochSeconds = 1_792_000_000,
                    operation = InstitutionalEventOperation.UPSERT,
                    payload = kotlinx.serialization.json.buildJsonObject { put("subject", JsonPrimitive("Math")) },
                )
            val record = InstitutionalEventRecord("tenant-a", credential.keyId, event, "a".repeat(64), Instant.now())
            assertEquals(InstitutionalEventAcceptance.ACCEPTED, repository.accept(record))
            assertEquals(InstitutionalEventAcceptance.DUPLICATE, repository.accept(record))
            assertEquals(
                InstitutionalEventAcceptance.CONFLICT,
                repository.accept(record.copy(payloadSha256 = "b".repeat(64))),
            )
            val newer =
                record.copy(
                    event = event.copy(eventId = "event-3", sourceVersion = 3),
                    payloadSha256 = "c".repeat(64),
                )
            assertEquals(InstitutionalEventAcceptance.ACCEPTED, repository.accept(newer))
            assertEquals(
                InstitutionalEventAcceptance.STALE,
                repository.accept(
                    record.copy(
                        event = event.copy(eventId = "event-2", sourceVersion = 2),
                        payloadSha256 = "d".repeat(64),
                    ),
                ),
            )
            assertEquals(
                InstitutionalEventAcceptance.CONFLICT,
                repository.accept(
                    newer.copy(event = event.copy(eventId = "event-duplicate", sourceVersion = 3)),
                ),
            )
            assertTrue(repository.revokeCredential(credential.keyId))
            assertFalse(assertNotNull(repository.findCredential(credential.keyId)).enabled)
        }

    @Test
    fun `late versions remain in the receipt ledger`() =
        database { database ->
            val repository =
                MongoInstitutionalPushRepository(
                    database,
                    InstitutionalSecretProtector(ByteArray(32) { it.toByte() }),
                )

            fun record(eventId: String, version: Long): InstitutionalEventRecord {
                return InstitutionalEventRecord(
                    tenantId = "tenant-late",
                    sourceCredentialId = "institution-key",
                    event =
                        InstitutionalPushEvent(
                            schemaVersion = 1,
                            eventId = eventId,
                            entityType = "schedule",
                            externalId = "course-late",
                            sourceVersion = version,
                            occurredAtEpochSeconds = 1_792_000_000,
                            operation = InstitutionalEventOperation.UPSERT,
                            payload = kotlinx.serialization.json.buildJsonObject { put("subject", JsonPrimitive("Math")) },
                        ),
                    payloadSha256 = version.toString().padStart(64, '0'),
                    receivedAt = Instant.now(),
                )
            }

            assertEquals(InstitutionalEventAcceptance.ACCEPTED, repository.accept(record("event-5", 5)))
            assertEquals(InstitutionalEventAcceptance.ACCEPTED, repository.accept(record("event-4", 4)))

            val events = database.getCollection<Document>(MongoInstitutionalPushRepository.EVENTS_COLLECTION)
            val filter =
                Document().apply {
                    put("tenantId", "tenant-late")
                    put("entityType", "schedule")
                    put("externalId", "course-late")
                }
            val stored = events.find(filter).toList()
            assertEquals(setOf(4L, 5L), stored.map { it.getLong("sourceVersion") }.toSet())
        }

    private fun database(block: suspend (MongoDatabase) -> Unit) =
        runBlocking {
            val uri = System.getenv("V12_TEST_MONGODB_URI")
            require(uri == "mongodb://127.0.0.1:27017")
            MongoClient.create(uri).use { client ->
                val database = client.getDatabase("v12_test_${UUID.randomUUID().toString().replace("-", "")}")
                try {
                    block(database)
                } finally {
                    database.drop()
                }
            }
        }
}
