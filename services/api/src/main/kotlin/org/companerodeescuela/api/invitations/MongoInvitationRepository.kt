package org.companerodeescuela.api.invitations

import com.mongodb.MongoWriteException
import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.gt
import com.mongodb.client.model.Filters.`in`
import com.mongodb.client.model.Filters.lte
import com.mongodb.client.model.Filters.nin
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.firstOrNull
import org.bson.Document
import java.time.Instant
import java.util.Date
import java.util.Locale

/** Private persistence primitive, not an authorization service or public endpoint. */
internal class MongoInvitationRepository(
    database: MongoDatabase,
) : InvitationRepository {
    private val records = database.getCollection<Document>("platform_invitations")

    override suspend fun create(record: InvitationRecord): Boolean {
        val canonical = record.canonicalForStorage()
        if (!canonical.validForCreation()) return false
        records.createIndex(Indexes.ascending("tokenHash"), IndexOptions().unique(true))
        return try {
            records.insertOne(canonical.toDocument())
            true
        } catch (error: MongoWriteException) {
            if (error.error.code == 11000) false else throw error
        }
    }

    override suspend fun find(id: String): InvitationRecord? = records.find(eq("_id", id)).firstOrNull()?.toInvitation()

    override suspend fun markSent(
        id: String,
        now: Instant,
    ): Boolean =
        records
            .updateOne(
                and(
                    eq("_id", id),
                    lte("createdAt", Date.from(now)),
                    eq("status", InvitationStatus.CREATED.name),
                ),
                Updates.set("status", InvitationStatus.SENT.name),
            ).matchedCount == 1L

    override suspend fun expireDue(now: Instant): Int =
        records
            .updateMany(
                and(
                    lte("expiresAt", Date.from(now)),
                    `in`("status", InvitationStatus.CREATED.name, InvitationStatus.SENT.name),
                ),
                Updates.set("status", InvitationStatus.EXPIRED.name),
            ).modifiedCount
            .toInt()

    override suspend fun retry(
        id: String,
        newTokenHash: String,
        now: Instant,
    ): Boolean =
        records
            .updateOne(
                and(
                    eq("_id", id),
                    `in`("status", InvitationStatus.FAILED.name, InvitationStatus.SENT.name),
                    gt("expiresAt", Date.from(now)),
                ),
                Updates.set("status", InvitationStatus.SENT.name),
                Updates.set("tokenHash", newTokenHash),
            ).matchedCount == 1L

    override suspend fun accept(
        id: String,
        tokenHash: String,
        accountEmail: String,
        emailVerified: Boolean,
        accountId: String,
        operationId: String,
        now: Instant,
    ): InvitationRecord? {
        if (!validAcceptanceInput(emailVerified, accountId, operationId) || !tokenHash.matches(Regex("[A-Za-z0-9_-]{43}"))) return null
        val common =
            and(
                eq("_id", id),
                eq("recipient", accountEmail.trim().lowercase(Locale.ROOT)),
                eq("tokenHash", tokenHash),
                lte("createdAt", Date.from(now)),
            )
        val updated =
            records.findOneAndUpdate(
                and(common, gt("expiresAt", Date.from(now)), eq("status", InvitationStatus.SENT.name)),
                Updates.combine(
                    Updates.set("status", InvitationStatus.ACCEPTED.name),
                    Updates.set("acceptedBy", accountId),
                    Updates.set("acceptedOperation", operationId),
                    Updates.set("acceptedAt", Date.from(now)),
                ),
                FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
            )
        if (updated != null) return updated.toInvitation()
        return records
            .find(
                and(
                    common,
                    eq("status", InvitationStatus.ACCEPTED.name),
                    eq("acceptedBy", accountId),
                    eq("acceptedOperation", operationId),
                ),
            ).firstOrNull()
            ?.toInvitation()
    }

    override suspend fun revoke(
        id: String,
        now: Instant,
    ): Boolean =
        records
            .updateOne(
                and(
                    eq("_id", id),
                    lte("createdAt", Date.from(now)),
                    nin("status", InvitationStatus.ACTIVE.name, InvitationStatus.REVOKED.name),
                ),
                Updates.combine(Updates.set("status", InvitationStatus.REVOKED.name), Updates.set("revokedAt", Date.from(now))),
            ).matchedCount == 1L

    private fun InvitationRecord.toDocument(): Document =
        Document("_id", id)
            .append("recipient", recipient)
            .append("institutionId", institutionId)
            .append("profile", profile.name)
            .append("issuerId", issuerId)
            .append("createdAt", Date.from(createdAt))
            .append("expiresAt", Date.from(expiresAt))
            .append("status", status.name)
            .append("tokenHash", tokenHash)

    private fun Document.toInvitation(): InvitationRecord =
        InvitationRecord(
            getString("_id"),
            getString("recipient"),
            getString("institutionId"),
            InvitationProfile.valueOf(getString("profile")),
            getString("issuerId"),
            getDate("createdAt").toInstant(),
            getDate("expiresAt").toInstant(),
            InvitationStatus.valueOf(getString("status")),
            getString("tokenHash"),
            getString("acceptedBy"),
            getString("acceptedOperation"),
            getDate("acceptedAt")?.toInstant(),
            getDate("revokedAt")?.toInstant(),
        )
}
