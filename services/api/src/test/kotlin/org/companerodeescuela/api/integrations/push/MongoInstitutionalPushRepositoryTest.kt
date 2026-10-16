package org.companerodeescuela.api.integrations.push

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
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
                InstitutionalEventAcceptance.ACCEPTED,
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

            fun record(
                eventId: String,
                version: Long,
            ): InstitutionalEventRecord =
                InstitutionalEventRecord(
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
                            payload =
                                kotlinx.serialization.json.buildJsonObject {
                                    put("subject", JsonPrimitive("Math"))
                                },
                        ),
                    payloadSha256 = version.toString().padStart(64, '0'),
                    receivedAt = Instant.now(),
                )

            assertEquals(InstitutionalEventAcceptance.ACCEPTED, repository.accept(record("event-5", 5)))
            assertEquals(InstitutionalEventAcceptance.ACCEPTED, repository.accept(record("event-4", 4)))

            val events = database.getCollection<Document>("institutional_integration_events")
            val filter =
                Document().apply {
                    put("tenantId", "tenant-late")
                    put("entityType", "schedule")
                    put("externalId", "course-late")
                }
            val stored = events.find(filter).toList()
            assertEquals(setOf(4L, 5L), stored.map { it.getLong("sourceVersion") }.toSet())
        }

    @Test
    fun `projection claim is tenant scoped`() =
        database { database ->
            val repository =
                MongoInstitutionalPushRepository(
                    database,
                    InstitutionalSecretProtector(ByteArray(32) { it.toByte() }),
                )
            val now = Instant.parse("2026-10-15T12:00:00Z")
            repository.accept(projectionRecord("tenant-a", "event-a"))
            repository.accept(projectionRecord("tenant-b", "event-b"))

            val claimed = repository.claimNext("tenant-b", "schedule:v1", now, 30)

            assertEquals("tenant-b", claimed?.record?.tenantId)
            assertEquals("event-b", claimed?.record?.event?.eventId)
        }

    @Test
    fun `only one worker claims an event`() =
        database { database ->
            val repository =
                MongoInstitutionalPushRepository(
                    database,
                    InstitutionalSecretProtector(ByteArray(32) { it.toByte() }),
                )
            val now = Instant.parse("2026-10-15T12:00:00Z")
            repository.accept(projectionRecord("tenant-a", "event-one"))

            val claims =
                coroutineScope {
                    (1..16)
                        .map { async { repository.claimNext("tenant-a", "schedule:v1", now, 30) } }
                        .awaitAll()
                }

            assertEquals(1, claims.count { it != null })
        }

    @Test
    fun `expired lease can be reclaimed`() =
        database { database ->
            val repository =
                MongoInstitutionalPushRepository(
                    database,
                    InstitutionalSecretProtector(ByteArray(32) { it.toByte() }),
                )
            val now = Instant.parse("2026-10-15T12:00:00Z")
            repository.accept(projectionRecord("tenant-a", "event-lease"))

            val first = assertNotNull(repository.claimNext("tenant-a", "schedule:v1", now, 30))
            val reclaimed = assertNotNull(repository.claimNext("tenant-a", "schedule:v1", now.plusSeconds(31), 30))

            assertTrue(first.leaseToken != reclaimed.leaseToken)
            assertEquals(2, reclaimed.attemptCount)
            assertFalse(repository.renew(first, now.plusSeconds(31), 30))
        }

    @Test
    fun `projection retry attempts stop at configured limit`() =
        database { database ->
            val repository =
                MongoInstitutionalPushRepository(
                    database,
                    InstitutionalSecretProtector(ByteArray(32) { it.toByte() }),
                )
            var now = Instant.parse("2026-10-15T12:00:00Z")
            repository.accept(projectionRecord("tenant-a", "event-retry"))

            repeat(8) { index ->
                val lease = assertNotNull(repository.claimNext("tenant-a", "schedule:v1", now, 1))
                assertEquals(index + 1, lease.attemptCount)
                assertTrue(repository.retry(lease, now, now, "TEMPORARY"))
                now = now.plusSeconds(2)
            }

            assertEquals(null, repository.claimNext("tenant-a", "schedule:v1", now, 30))
            val stored =
                database
                    .getCollection<Document>("institutional_integration_events")
                    .find(Document("eventId", "event-retry"))
                    .firstOrNull()
            assertEquals("failed", stored?.getString("projectionStatus"))
            assertEquals("RETRIES_EXHAUSTED", stored?.getString("projectionFailureCode"))
        }

    @Test
    fun `projection completion requires the current lease and records applied version`() =
        database { database ->
            val repository =
                MongoInstitutionalPushRepository(
                    database,
                    InstitutionalSecretProtector(ByteArray(32) { it.toByte() }),
                )
            val now = Instant.parse("2026-10-15T12:00:00Z")
            repository.accept(projectionRecord("tenant-a", "event-complete"))

            val original = assertNotNull(repository.claimNext("tenant-a", "schedule:v1", now, 30))
            val current = assertNotNull(repository.claimNext("tenant-a", "schedule:v1", now.plusSeconds(31), 30))

            assertFalse(repository.complete(original, now.plusSeconds(31)))
            assertTrue(repository.complete(current, now.plusSeconds(31)))
            assertEquals(1L, repository.findAppliedVersion("tenant-a", "schedule", "course-a"))
            assertEquals(
                "projected",
                database
                    .getCollection<Document>("institutional_integration_events")
                    .find(Document("eventId", "event-complete"))
                    .firstOrNull()
                    ?.getString("projectionStatus"),
            )
            assertEquals(null, repository.claimNext("tenant-a", "schedule:v1", now.plusSeconds(32), 30))
        }

    @Test
    fun `applied version never moves backwards`() =
        database { database ->
            val repository =
                MongoInstitutionalPushRepository(
                    database,
                    InstitutionalSecretProtector(ByteArray(32) { it.toByte() }),
                )
            val now = Instant.parse("2026-10-15T12:00:00Z")

            assertTrue(repository.advanceAppliedVersion("tenant-a", "schedule", "course-a", 5, now))
            assertFalse(repository.advanceAppliedVersion("tenant-a", "schedule", "course-a", 4, now.plusSeconds(1)))
            assertTrue(repository.advanceAppliedVersion("tenant-a", "schedule", "course-a", 8, now.plusSeconds(2)))

            assertEquals(8L, repository.findAppliedVersion("tenant-a", "schedule", "course-a"))
        }

    @Test
    fun `active lease can be renewed`() =
        database { database ->
            val repository =
                MongoInstitutionalPushRepository(
                    database,
                    InstitutionalSecretProtector(ByteArray(32) { it.toByte() }),
                )
            val now = Instant.parse("2026-10-15T12:00:00Z")
            repository.accept(projectionRecord("tenant-a", "event-renew"))

            val lease = assertNotNull(repository.claimNext("tenant-a", "schedule:v1", now, 30))

            assertTrue(repository.renew(lease, now.plusSeconds(10), 60))
            assertEquals(null, repository.claimNext("tenant-a", "schedule:v1", now.plusSeconds(40), 30))
            assertNotNull(repository.claimNext("tenant-a", "schedule:v1", now.plusSeconds(71), 30))
        }

    @Test
    fun `reconciliation requeues only matching tenant and adapter events`() =
        database { database ->
            val repository =
                MongoInstitutionalPushRepository(
                    database,
                    InstitutionalSecretProtector(ByteArray(32) { it.toByte() }),
                )
            val now = Instant.parse("2026-10-15T12:00:00Z")
            val events = database.getCollection<Document>("institutional_integration_events")
            repository.accept(projectionRecord("tenant-a", "event-matching"))
            repository.accept(projectionRecord("tenant-a", "event-other-adapter", entityType = "course"))
            repository.accept(projectionRecord("tenant-b", "event-other-tenant"))
            events.updateOne(
                Document("eventId", "event-matching"),
                Document("\$set", Document("projectionStatus", "unsupported")),
            )
            events.updateOne(
                Document("eventId", "event-other-adapter"),
                Document("\$set", Document("projectionStatus", "unsupported")),
            )
            events.updateOne(
                Document("eventId", "event-other-tenant"),
                Document("\$set", Document("projectionStatus", "unsupported")),
            )

            assertEquals(1, repository.reconcile("tenant-a", "schedule:v1", now))
            assertEquals(
                "pending",
                events.find(Document("eventId", "event-matching")).firstOrNull()?.getString("projectionStatus"),
            )
            assertEquals(
                "unsupported",
                events.find(Document("eventId", "event-other-adapter")).firstOrNull()?.getString("projectionStatus"),
            )
            assertEquals(
                "unsupported",
                events.find(Document("eventId", "event-other-tenant")).firstOrNull()?.getString("projectionStatus"),
            )
        }

    private fun projectionRecord(
        tenantId: String,
        eventId: String,
        entityType: String = "schedule",
        externalId: String = "course-a",
        version: Long = 1,
    ) = InstitutionalEventRecord(
        tenantId = tenantId,
        sourceCredentialId = "institution-key",
        event =
            InstitutionalPushEvent(
                schemaVersion = 1,
                eventId = eventId,
                entityType = entityType,
                externalId = externalId,
                sourceVersion = version,
                occurredAtEpochSeconds = 1_792_000_000,
                operation = InstitutionalEventOperation.UPSERT,
                payload =
                    kotlinx.serialization.json.buildJsonObject {
                        put("subject", JsonPrimitive("Math"))
                    },
            ),
        payloadSha256 = version.toString().padStart(64, '0'),
        receivedAt = Instant.parse("2026-10-15T12:00:00Z"),
    )

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
