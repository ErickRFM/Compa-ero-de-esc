package org.companerodeescuela.api.integrations.push

import com.mongodb.MongoWriteException
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bson.Document
import java.time.Instant
import java.util.Date

/** Mongo persistence for credentials, replay nonces, and immutable inbound receipts. */
class MongoInstitutionalPushRepository(
    database: MongoDatabase,
    private val secretProtector: InstitutionalSecretProtector,
    private val now: () -> Instant = Instant::now,
) : IntegrationCredentialRepository,
    IntegrationNonceRepository,
    InstitutionalEventRepository {
    private val credentials: MongoCollection<Document> = database.getCollection(CREDENTIALS_COLLECTION)
    private val nonces: MongoCollection<Document> = database.getCollection(NONCES_COLLECTION)
    private val events: MongoCollection<Document> = database.getCollection(EVENTS_COLLECTION)
    private val indexMutex = kotlinx.coroutines.sync.Mutex()

    @Volatile private var indexesReady = false

    override suspend fun findCredential(keyId: String): IntegrationCredential? {
        ensureIndexes()
        val document = credentials.find(Document("_id", keyId)).firstOrNull() ?: return null
        return try {
            IntegrationCredential(
                keyId = document.getString("_id"),
                tenantId = document.getString("tenantId"),
                secret = secretProtector.decrypt(document.getString("secretCiphertext")),
                scopes = document.getList("scopes", String::class.java).toSet(),
                enabled = document.getBoolean("enabled", false),
                expiresAtEpochSeconds = document.getLong("expiresAtEpochSeconds"),
            )
        } catch (_: RuntimeException) {
            // Corrupt or incompatible credentials fail closed without exposing details.
            null
        }
    }

    override suspend fun reserveNonce(
        credentialId: String,
        nonce: String,
        expiresAt: Instant,
    ): Boolean {
        ensureIndexes()
        return try {
            nonces.insertOne(
                Document("credentialId", credentialId)
                    .append("nonce", nonce)
                    .append("createdAt", Date.from(now()))
                    .append("expiresAt", Date.from(expiresAt)),
            )
            true
        } catch (error: MongoWriteException) {
            if (error.error.code == DUPLICATE_KEY_CODE) false else throw error
        }
    }

    override suspend fun accept(record: InstitutionalEventRecord): InstitutionalEventAcceptance {
        ensureIndexes()
        val eventIdentity =
            Document("tenantId", record.tenantId)
                .append("eventId", record.event.eventId)
        val existingById = events.find(eventIdentity).firstOrNull()
        if (existingById != null) {
            return if (existingById.getString("payloadSha256") == record.payloadSha256) {
                InstitutionalEventAcceptance.DUPLICATE
            } else {
                InstitutionalEventAcceptance.CONFLICT
            }
        }
        val entityIdentity =
            Document("tenantId", record.tenantId)
                .append("entityType", record.event.entityType)
                .append("externalId", record.event.externalId)
        val latest =
            events
                .find(entityIdentity)
                .sort(Sorts.descending("sourceVersion"))
                .firstOrNull()
        if (latest != null && latest.getLong("sourceVersion") >= record.event.sourceVersion) {
            return if (latest.getLong("sourceVersion") == record.event.sourceVersion) {
                InstitutionalEventAcceptance.CONFLICT
            } else {
                InstitutionalEventAcceptance.STALE
            }
        }
        val document =
            Document("tenantId", record.tenantId)
                .append("eventId", record.event.eventId)
                .append("entityType", record.event.entityType)
                .append("externalId", record.event.externalId)
                .append("sourceVersion", record.event.sourceVersion)
                .append(
                    "operation",
                    record.event.operation.name
                        .lowercase(),
                ).append("occurredAtEpochSeconds", record.event.occurredAtEpochSeconds)
                .append("schemaVersion", record.event.schemaVersion)
                .append("payload", Json.encodeToString(record.event.payload))
                .append("payloadSha256", record.payloadSha256)
                .append("sourceCredentialId", record.sourceCredentialId)
                .append("receivedAt", Date.from(record.receivedAt))
        return try {
            events.insertOne(document)
            InstitutionalEventAcceptance.ACCEPTED
        } catch (error: MongoWriteException) {
            if (error.error.code != DUPLICATE_KEY_CODE) throw error
            val priorById = events.find(eventIdentity).firstOrNull()
            if (priorById != null) {
                return if (priorById.getString("payloadSha256") == record.payloadSha256) {
                    InstitutionalEventAcceptance.DUPLICATE
                } else {
                    InstitutionalEventAcceptance.CONFLICT
                }
            }
            val latestAfterRace =
                events
                    .find(entityIdentity)
                    .sort(Sorts.descending("sourceVersion"))
                    .firstOrNull() ?: throw error
            if (latestAfterRace.getLong("sourceVersion") == record.event.sourceVersion) {
                InstitutionalEventAcceptance.CONFLICT
            } else {
                InstitutionalEventAcceptance.STALE
            }
        }
    }

    /** Trusted provisioning seam for deployment tooling; never exposed as a public route. */
    suspend fun putCredential(
        credential: IntegrationCredential,
        createdAt: Instant = now(),
    ) {
        require(credential.keyId.matches(Regex("[A-Za-z0-9_-]{1,64}")))
        require(credential.tenantId.isNotBlank() && credential.tenantId.length <= 128)
        require(credential.scopes.isNotEmpty())
        ensureIndexes()
        credentials.replaceOne(
            Document("_id", credential.keyId),
            Document("_id", credential.keyId)
                .append("tenantId", credential.tenantId)
                .append("secretCiphertext", secretProtector.encrypt(credential.secret))
                .append("scopes", credential.scopes.sorted())
                .append("enabled", credential.enabled)
                .append("version", CREDENTIAL_VERSION)
                .append("createdAt", Date.from(createdAt))
                .append("expiresAtEpochSeconds", credential.expiresAtEpochSeconds),
            ReplaceOptions().upsert(true),
        )
    }

    suspend fun revokeCredential(
        keyId: String,
        revokedAt: Instant = now(),
    ): Boolean {
        ensureIndexes()
        val result =
            credentials.updateOne(
                Document("_id", keyId).append("enabled", true),
                Document("\$set", Document("enabled", false).append("revokedAt", Date.from(revokedAt))),
            )
        return result.modifiedCount == 1L
    }

    private suspend fun ensureIndexes() {
        if (indexesReady) return
        indexMutex.withLock {
            if (indexesReady) return
            credentials.createIndex(Indexes.ascending("_id"), IndexOptions().unique(true))
            nonces.createIndex(Indexes.ascending("credentialId", "nonce"), IndexOptions().unique(true))
            nonces.createIndex(Indexes.ascending("expiresAt"), IndexOptions().expireAfter(0L, java.util.concurrent.TimeUnit.SECONDS))
            events.createIndex(Indexes.ascending("tenantId", "eventId"), IndexOptions().unique(true))
            events.createIndex(
                Indexes.ascending("tenantId", "entityType", "externalId", "sourceVersion"),
                IndexOptions().unique(true),
            )
            indexesReady = true
        }
    }

    private companion object {
        const val CREDENTIALS_COLLECTION = "institutional_integration_credentials"
        const val NONCES_COLLECTION = "institutional_integration_nonces"
        const val EVENTS_COLLECTION = "institutional_integration_events"
        const val DUPLICATE_KEY_CODE = 11000
        const val CREDENTIAL_VERSION = 1
    }
}
