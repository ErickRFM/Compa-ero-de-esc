package org.companerodeescuela.api.auth

import com.mongodb.MongoWriteException
import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.exists
import com.mongodb.client.model.Filters.gt
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bson.Document
import java.time.Instant
import java.util.Date
import java.util.Locale

/**
 * Atomically consumes the configured owner invitation digest.
 *
 * The raw code never reaches this repository. Mongo stores only the SHA-256 digest,
 * its bound recipient/expiry, and the consumption audit fields. A consumed digest
 * is a permanent tombstone; recovery issues a new random code instead of reopening it.
 */
interface OwnerInvitationRepository {
    suspend fun consume(
        digest: String,
        recipient: String,
        expiresAt: Instant,
        now: Instant,
        consumptionId: String,
    ): Boolean
}

private fun validOwnerInvitationConsumption(
    digest: String,
    recipient: String,
    expiresAt: Instant,
    now: Instant,
    consumptionId: String,
): Boolean =
    digest.matches(Regex("[a-f0-9]{64}")) &&
        recipient == recipient.trim().lowercase(Locale.ROOT) &&
        recipient.length in 3..128 &&
        recipient.matches(Regex("[^\\s@]+@[^\\s@]+")) &&
        recipient.none(Char::isISOControl) &&
        expiresAt > now &&
        expiresAt <= now.plusSeconds(7 * 24 * 60 * 60) &&
        consumptionId.matches(Regex("[A-Za-z0-9_-]{1,128}"))

class InMemoryOwnerInvitationRepository : OwnerInvitationRepository {
    private data class Consumption(
        val recipient: String,
        val expiresAt: Instant,
        val consumedAt: Instant,
        val consumptionId: String,
    )

    private val mutex = Mutex()
    private val consumed = mutableMapOf<String, Consumption>()

    override suspend fun consume(
        digest: String,
        recipient: String,
        expiresAt: Instant,
        now: Instant,
        consumptionId: String,
    ): Boolean =
        mutex.withLock {
            if (!validOwnerInvitationConsumption(digest, recipient, expiresAt, now, consumptionId)) {
                return@withLock false
            }
            if (digest in consumed) return@withLock false
            consumed[digest] = Consumption(recipient, expiresAt, now, consumptionId)
            true
        }
}

class MongoOwnerInvitationRepository(
    database: MongoDatabase,
) : OwnerInvitationRepository {
    private val records = database.getCollection<Document>("owner_registration_invitations")

    override suspend fun consume(
        digest: String,
        recipient: String,
        expiresAt: Instant,
        now: Instant,
        consumptionId: String,
    ): Boolean {
        if (!validOwnerInvitationConsumption(digest, recipient, expiresAt, now, consumptionId)) return false

        val expiry = Date.from(expiresAt)
        try {
            // The digest is the stable _id. $setOnInsert binds it once to one email and expiry.
            records.updateOne(
                eq("_id", digest),
                Updates.combine(
                    Updates.setOnInsert("recipient", recipient),
                    Updates.setOnInsert("expiresAt", expiry),
                    Updates.setOnInsert("createdAt", Date.from(now)),
                ),
                UpdateOptions().upsert(true),
            )
        } catch (error: MongoWriteException) {
            if (error.error.code != 11000) throw error
            // Another API instance may have seeded the same digest concurrently.
        }

        return records.findOneAndUpdate(
            and(
                eq("_id", digest),
                eq("recipient", recipient),
                eq("expiresAt", expiry),
                gt("expiresAt", Date.from(now)),
                exists("consumedAt", false),
            ),
            Updates.combine(
                Updates.set("consumedAt", Date.from(now)),
                Updates.set("consumedBy", consumptionId),
            ),
            FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
        ) != null
    }
}
