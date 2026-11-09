package org.companerodeescuela.api.audit

import com.mongodb.MongoWriteException
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import org.bson.Document
import java.security.MessageDigest
import java.time.Instant
import java.util.Date
import java.util.UUID

internal data class PlatformAuditEvent(
    val id: String,
    val actorId: String,
    val action: String,
    val resourceType: String,
    val resourceId: String,
    val operationId: String?,
    val outcome: String,
    val createdAt: Instant,
) {
    companion object {
        fun create(
            actorId: String,
            action: String,
            resourceType: String,
            resourceId: String,
            operationId: String?,
            outcome: String,
            createdAt: Instant,
        ): PlatformAuditEvent {
            require(actorId.isNotBlank() && actorId.length <= 128)
            require(action.matches(Regex("[A-Z_]{1,64}")))
            require(resourceType.matches(Regex("[A-Z_]{1,48}")))
            require(resourceId.isNotBlank() && resourceId.length <= 128)
            require(operationId == null || operationId.matches(Regex("[A-Za-z0-9_-]{1,128}")))
            require(outcome in setOf("STARTED", "SUCCEEDED", "FAILED"))
            val identity = listOf(actorId, action, resourceType, resourceId, operationId.orEmpty()).joinToString("\n")
            val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
            val id = digest.joinToString("") { "%02x".format(it) }
            return PlatformAuditEvent(id, actorId, action, resourceType, resourceId, operationId, outcome, createdAt)
        }
    }
}

internal interface PlatformAuditLog {
    suspend fun record(event: PlatformAuditEvent): Boolean
}

internal class InMemoryPlatformAuditLog : PlatformAuditLog {
    private val events = java.util.concurrent.ConcurrentHashMap<String, PlatformAuditEvent>()

    override suspend fun record(event: PlatformAuditEvent): Boolean = events.putIfAbsent(event.id, event) == null
}

internal class MongoPlatformAuditLog(
    database: MongoDatabase,
) : PlatformAuditLog {
    private val events = database.getCollection<Document>("platform_audit_events")

    @Volatile
    private var indexesReady = false

    private suspend fun ensureIndexes() {
        if (indexesReady) return
        try {
            events.createIndex(
                Indexes.ascending("actorId", "createdAt"),
                IndexOptions().name("audit_actor_created_idx"),
            )
            events.createIndex(
                Indexes.ascending("resourceType", "resourceId", "createdAt"),
                IndexOptions().name("audit_resource_created_idx"),
            )
        } catch (_: com.mongodb.MongoCommandException) {
            // Indexes already exist.
        }
        indexesReady = true
    }

    override suspend fun record(event: PlatformAuditEvent): Boolean {
        ensureIndexes()
        val document = Document("_id", event.id)
            .append("source", "PLATFORM_ADMIN")
            .append("actorId", event.actorId)
            .append("action", event.action)
            .append("resourceType", event.resourceType)
            .append("resourceId", event.resourceId)
            .append("operationId", event.operationId)
            .append("outcome", event.outcome)
            .append("createdAt", Date.from(event.createdAt))
        return try {
            events.insertOne(document)
            true
        } catch (error: MongoWriteException) {
            if (error.error.code == 11000) false else throw error
        }
    }
}
