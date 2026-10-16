package org.companerodeescuela.api.integrations.push

import com.mongodb.client.model.Filters
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import org.bson.Document
import java.security.MessageDigest
import java.time.Instant
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Mongo persistence for credentials, replay nonces, and immutable inbound receipts. */
class MongoInstitutionalPushRepository(
    database: MongoDatabase,
    private val secretProtector: InstitutionalSecretProtector,
    private val now: () -> Instant = Instant::now,
) : IntegrationCredentialRepository,
    IntegrationNonceRepository,
    InstitutionalEventRepository,
    InstitutionalProjectionRepository,
    InstitutionalPushRateLimiter {
    private val credentials: MongoCollection<Document> = database.getCollection(CREDENTIALS_COLLECTION)
    private val nonces: MongoCollection<Document> = database.getCollection(NONCES_COLLECTION)
    private val events: MongoCollection<Document> = database.getCollection(EVENTS_COLLECTION)
    private val projectionVersions: MongoCollection<Document> = database.getCollection(PROJECTION_VERSIONS_COLLECTION)
    private val rateLimits: MongoCollection<Document> = database.getCollection(RATE_LIMITS_COLLECTION)
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
        } catch (error: com.mongodb.MongoException) {
            if (error.code == DUPLICATE_KEY_CODE) false else throw error
        }
    }

    override suspend fun acquire(clientAddress: String): Long? {
        ensureIndexes()
        val current = now()
        val windowStart = current.epochSecond / RATE_LIMIT_WINDOW_SECONDS * RATE_LIMIT_WINDOW_SECONDS
        val bucketId = "${sha256(clientAddress)}:$windowStart"
        val expiresAt = Date.from(Instant.ofEpochSecond(windowStart + RATE_LIMIT_RETENTION_SECONDS))
        val filter = Document("_id", bucketId)
        val update =
            Updates.combine(
                Updates.inc("count", 1),
                Updates.setOnInsert("expiresAt", expiresAt),
            )
        val updated =
            try {
                rateLimits.findOneAndUpdate(
                    filter,
                    update,
                    FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER),
                )
            } catch (error: com.mongodb.MongoException) {
                if (error.code != DUPLICATE_KEY_CODE) throw error
                rateLimits.findOneAndUpdate(
                    filter,
                    update,
                    FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
                ) ?: throw error
            } ?: return RATE_LIMIT_WINDOW_SECONDS
        val count = updated.getInteger("count", 0)
        if (count <= MAX_REQUESTS_PER_WINDOW) return null
        return (windowStart + RATE_LIMIT_WINDOW_SECONDS - current.epochSecond).coerceAtLeast(1)
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
        val entityVersionIdentity =
            Document("tenantId", record.tenantId)
                .append("entityType", record.event.entityType)
                .append("externalId", record.event.externalId)
                .append("sourceVersion", record.event.sourceVersion)
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
                .append("projectionAdapterId", adapterIdFor(record.event))
                .append("projectionStatus", InstitutionalProjectionStatus.PENDING.name.lowercase())
                .append("projectionAttemptCount", 0)
                .append("projectionAvailableAt", Date.from(record.receivedAt))
        return try {
            events.insertOne(document)
            InstitutionalEventAcceptance.ACCEPTED
        } catch (error: com.mongodb.MongoException) {
            if (error.code != DUPLICATE_KEY_CODE) throw error
            val priorById = events.find(eventIdentity).firstOrNull()
            if (priorById != null) {
                return if (priorById.getString("payloadSha256") == record.payloadSha256) {
                    InstitutionalEventAcceptance.DUPLICATE
                } else {
                    InstitutionalEventAcceptance.CONFLICT
                }
            }
            if (events.find(entityVersionIdentity).firstOrNull() != null) {
                InstitutionalEventAcceptance.CONFLICT
            } else {
                throw error
            }
        }
    }

    override suspend fun claimNext(
        tenantId: String,
        adapterId: String,
        now: Instant,
        leaseDurationSeconds: Long,
    ): InstitutionalProjectionLease? {
        require(tenantId.isNotBlank() && adapterId.isNotBlank())
        require(leaseDurationSeconds in MIN_LEASE_SECONDS..MAX_LEASE_SECONDS)
        ensureIndexes()
        val nowDate = Date.from(now)
        val leaseToken = UUID.randomUUID().toString()
        val leaseExpiresAt = now.plusSeconds(leaseDurationSeconds)
        val claimable =
            Filters.or(
                Filters.and(
                    Filters.eq("projectionStatus", InstitutionalProjectionStatus.PENDING.name.lowercase()),
                    Filters.lt("projectionAttemptCount", MAX_PROJECTION_ATTEMPTS),
                ),
                Filters.and(
                    Filters.eq("projectionStatus", InstitutionalProjectionStatus.RETRYABLE.name.lowercase()),
                    Filters.lte("projectionAvailableAt", nowDate),
                    Filters.lt("projectionAttemptCount", MAX_PROJECTION_ATTEMPTS),
                ),
                Filters.and(
                    Filters.eq("projectionStatus", InstitutionalProjectionStatus.PROCESSING.name.lowercase()),
                    Filters.lte("projectionLeaseExpiresAt", nowDate),
                    Filters.lt("projectionAttemptCount", MAX_PROJECTION_ATTEMPTS),
                ),
            )
        val filter =
            Filters.and(
                Filters.eq("tenantId", tenantId),
                Filters.eq("projectionAdapterId", adapterId),
                claimable,
            )
        val update =
            Updates.combine(
                Updates.set("projectionStatus", InstitutionalProjectionStatus.PROCESSING.name.lowercase()),
                Updates.set("projectionLeaseToken", leaseToken),
                Updates.set("projectionLeaseExpiresAt", Date.from(leaseExpiresAt)),
                Updates.inc("projectionAttemptCount", 1),
            )
        val claimed =
            events.findOneAndUpdate(
                filter,
                update,
                FindOneAndUpdateOptions()
                    .sort(Sorts.ascending("receivedAt", "sourceVersion"))
                    .returnDocument(ReturnDocument.AFTER),
            ) ?: return null
        val record =
            InstitutionalEventRecord(
                tenantId = claimed.getString("tenantId"),
                sourceCredentialId = claimed.getString("sourceCredentialId"),
                event =
                    InstitutionalPushEvent(
                        schemaVersion = claimed.getInteger("schemaVersion"),
                        eventId = claimed.getString("eventId"),
                        entityType = claimed.getString("entityType"),
                        externalId = claimed.getString("externalId"),
                        sourceVersion = claimed.getLong("sourceVersion"),
                        occurredAtEpochSeconds = claimed.getLong("occurredAtEpochSeconds"),
                        operation = InstitutionalEventOperation.valueOf(claimed.getString("operation").uppercase()),
                        payload = Json.decodeFromString(claimed.getString("payload")),
                    ),
                payloadSha256 = claimed.getString("payloadSha256"),
                receivedAt = claimed.getDate("receivedAt").toInstant(),
            )
        return InstitutionalProjectionLease(
            record = record,
            adapterId = claimed.getString("projectionAdapterId"),
            leaseToken = leaseToken,
            leaseExpiresAt = leaseExpiresAt,
            attemptCount = claimed.getInteger("projectionAttemptCount"),
        )
    }

    override suspend fun renew(
        lease: InstitutionalProjectionLease,
        now: Instant,
        leaseDurationSeconds: Long,
    ): Boolean {
        require(leaseDurationSeconds in MIN_LEASE_SECONDS..MAX_LEASE_SECONDS)
        ensureIndexes()
        val updated =
            events.updateOne(
                activeLeaseFilter(lease, now),
                Updates.set("projectionLeaseExpiresAt", Date.from(now.plusSeconds(leaseDurationSeconds))),
            )
        return updated.modifiedCount == 1L
    }

    override suspend fun complete(
        lease: InstitutionalProjectionLease,
        now: Instant,
    ): Boolean {
        ensureIndexes()
        val filter = activeLeaseFilter(lease, now)
        if (events.find(filter).firstOrNull() == null) return false
        val advanced =
            advanceAppliedVersion(
                tenantId = lease.record.tenantId,
                entityType = lease.record.event.entityType,
                externalId = lease.record.event.externalId,
                sourceVersion = lease.record.event.sourceVersion,
                now = now,
            )
        val status =
            if (advanced) {
                InstitutionalProjectionStatus.PROJECTED
            } else {
                InstitutionalProjectionStatus.SUPERSEDED
            }
        val completed =
            events.updateOne(
                filter,
                Updates.combine(
                    Updates.set("projectionStatus", status.name.lowercase()),
                    Updates.unset("projectionLeaseToken"),
                    Updates.unset("projectionLeaseExpiresAt"),
                    Updates.set("projectionCompletedAt", Date.from(now)),
                ),
            )
        return completed.modifiedCount == 1L
    }

    override suspend fun advanceAppliedVersion(
        tenantId: String,
        entityType: String,
        externalId: String,
        sourceVersion: Long,
        now: Instant,
    ): Boolean {
        require(tenantId.isNotBlank() && entityType.isNotBlank() && externalId.isNotBlank())
        require(sourceVersion > 0)
        ensureIndexes()
        val identity =
            Document("tenantId", tenantId)
                .append("entityType", entityType)
                .append("externalId", externalId)
        val filter =
            Document(identity)
                .append(
                    "\$or",
                    listOf(
                        Document("sourceVersion", Document("\$exists", false)),
                        Document("sourceVersion", Document("\$lt", sourceVersion)),
                    ),
                )
        val update =
            Document("\$set", Document("sourceVersion", sourceVersion).append("updatedAt", Date.from(now)))
                .append(
                    "\$setOnInsert",
                    Document("tenantId", tenantId)
                        .append("entityType", entityType)
                        .append("externalId", externalId)
                        .append("createdAt", Date.from(now)),
                )
        return try {
            val result = projectionVersions.updateOne(filter, update, UpdateOptions().upsert(true))
            result.modifiedCount == 1L || result.upsertedId != null
        } catch (error: com.mongodb.MongoException) {
            if (error.code == DUPLICATE_KEY_CODE) false else throw error
        }
    }

    override suspend fun findAppliedVersion(
        tenantId: String,
        entityType: String,
        externalId: String,
    ): Long? {
        ensureIndexes()
        val identity =
            Document("tenantId", tenantId)
                .append("entityType", entityType)
                .append("externalId", externalId)
        return projectionVersions.find(identity).firstOrNull()?.getLong("sourceVersion")
    }

    override suspend fun reconcile(
        tenantId: String,
        adapterId: String,
        now: Instant,
    ): Long {
        require(tenantId.isNotBlank() && adapterId.isNotBlank())
        ensureIndexes()
        val result =
            events.updateMany(
                Filters.and(
                    Filters.eq("tenantId", tenantId),
                    Filters.eq("projectionAdapterId", adapterId),
                    Filters.`in`(
                        "projectionStatus",
                        listOf(
                            InstitutionalProjectionStatus.PENDING_IDENTITY.name.lowercase(),
                            InstitutionalProjectionStatus.UNSUPPORTED.name.lowercase(),
                            InstitutionalProjectionStatus.FAILED.name.lowercase(),
                        ),
                    ),
                ),
                Updates.combine(
                    Updates.set("projectionStatus", InstitutionalProjectionStatus.PENDING.name.lowercase()),
                    Updates.set("projectionAvailableAt", Date.from(now)),
                    Updates.set("projectionAttemptCount", 0),
                    Updates.unset("projectionFailureCode"),
                    Updates.unset("projectionLeaseToken"),
                    Updates.unset("projectionLeaseExpiresAt"),
                ),
            )
        return result.modifiedCount
    }

    private fun activeLeaseFilter(
        lease: InstitutionalProjectionLease,
        now: Instant,
    ) = Filters.and(
        Filters.eq("tenantId", lease.record.tenantId),
        Filters.eq("eventId", lease.record.event.eventId),
        Filters.eq("projectionAdapterId", lease.adapterId),
        Filters.eq("projectionStatus", InstitutionalProjectionStatus.PROCESSING.name.lowercase()),
        Filters.eq("projectionLeaseToken", lease.leaseToken),
        Filters.gt("projectionLeaseExpiresAt", Date.from(now)),
    )

    private fun adapterIdFor(event: InstitutionalPushEvent): String = "${event.entityType}:v${event.schemaVersion}"

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
            nonces.createIndex(Indexes.ascending("credentialId", "nonce"), IndexOptions().unique(true))
            nonces.createIndex(Indexes.ascending("expiresAt"), IndexOptions().expireAfter(0L, java.util.concurrent.TimeUnit.SECONDS))
            events.createIndex(Indexes.ascending("tenantId", "eventId"), IndexOptions().unique(true))
            events.createIndex(
                Indexes.ascending("tenantId", "entityType", "externalId", "sourceVersion"),
                IndexOptions().unique(true),
            )
            projectionVersions.createIndex(
                Indexes.ascending("tenantId", "entityType", "externalId"),
                IndexOptions().unique(true),
            )
            rateLimits.createIndex(
                Indexes.ascending("expiresAt"),
                IndexOptions().expireAfter(0L, java.util.concurrent.TimeUnit.SECONDS),
            )
            indexesReady = true
        }
    }

    private companion object {
        const val CREDENTIALS_COLLECTION = "institutional_integration_credentials"
        const val NONCES_COLLECTION = "institutional_integration_nonces"
        const val EVENTS_COLLECTION = "institutional_integration_events"
        const val RATE_LIMITS_COLLECTION = "institutional_integration_rate_limits"
        const val PROJECTION_VERSIONS_COLLECTION = "institutional_integration_projection_versions"
        const val MAX_REQUESTS_PER_WINDOW = 120
        const val RATE_LIMIT_WINDOW_SECONDS = 60L
        const val RATE_LIMIT_RETENTION_SECONDS = 120L
        const val DUPLICATE_KEY_CODE = 11000
        const val CREDENTIAL_VERSION = 1
        const val MAX_PROJECTION_ATTEMPTS = 8
        const val MIN_LEASE_SECONDS = 1L
        const val MAX_LEASE_SECONDS = 900L
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
