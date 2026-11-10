package org.companerodeescuela.api.mail

import com.mongodb.MongoWriteException
import com.mongodb.client.model.Filters
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import org.bson.Document
import java.util.Date

/**
 * Stores a minimal, durable audit trail for provider delivery events.
 * Event IDs are the Svix message IDs, so Resend retries are idempotent.
 * Recipient addresses and raw email payloads are deliberately not persisted.
 */
internal class MongoResendWebhookAuditRepository(
    database: MongoDatabase,
) {
    private val auditEvents = database.getCollection<Document>("platform_audit_events")
    private val invitations = database.getCollection<Document>("platform_invitations")

    @Volatile
    private var indexesReady = false

    private suspend fun ensureIndexes() {
        if (indexesReady) return
        try {
            auditEvents.createIndex(
                Indexes.ascending("resourceId", "eventAt"),
                IndexOptions().name("audit_resource_event_time_idx"),
            )
        } catch (_: com.mongodb.MongoCommandException) {
            // Index already exists.
        }
        indexesReady = true
    }

    suspend fun record(event: VerifiedResendWebhookEvent): Boolean {
        ensureIndexes()
        val status = deliveryStatus(event.type)
        if (status != null && event.invitationId != null && event.emailId != null) {
            invitations.updateOne(
                Filters.and(
                    Filters.eq("_id", event.invitationId),
                    Filters.or(
                        Filters.exists("deliveryEventAt", false),
                        Filters.lt("deliveryEventAt", Date.from(event.createdAt)),
                    ),
                ),
                Updates.combine(
                    Updates.set("deliveryStatus", status),
                    Updates.set("providerEmailId", event.emailId),
                    Updates.set("deliveryEventType", event.type),
                    Updates.set("deliveryEventAt", Date.from(event.createdAt)),
                ),
            )
        }

        val audit =
            Document("_id", event.messageId)
                .append("source", "RESEND_WEBHOOK")
                .append("action", event.type.uppercase().replace('.', '_'))
                .append("resourceType", if (event.invitationId == null) "EMAIL" else "INVITATION")
                .append("resourceId", event.invitationId ?: event.emailId)
                .append("providerEmailId", event.emailId)
                .append("eventAt", Date.from(event.createdAt))
                .append("receivedAt", Date.from(event.receivedAt))
                .append("processed", true)

        return try {
            auditEvents.insertOne(audit)
            true
        } catch (error: MongoWriteException) {
            if (error.error.code == 11000) {
                false
            } else {
                throw error
            }
        }
    }

    private fun deliveryStatus(type: String): String? =
        when (type) {
            "email.sent" -> "ACCEPTED"
            "email.delivered" -> "DELIVERED"
            "email.delivery_delayed" -> "DELAYED"
            "email.bounced" -> "BOUNCED"
            "email.failed" -> "FAILED"
            "email.suppressed" -> "SUPPRESSED"
            "email.complained" -> "COMPLAINED"
            else -> null
        }
}
