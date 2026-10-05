package org.companerodeescuela.api.auth

import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.gt
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import java.time.Instant
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bson.Document
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary

/** Stores only a one-way hash of the current refresh token for each device session. */
class MongoRefreshSessionRepository(
    database: MongoDatabase,
) : RefreshSessionRepository {
    private val sessions: MongoCollection<Document> = database.getCollection(COLLECTION)
    private val indexMutex = Mutex()

    @Volatile
    private var indexesReady = false

    override suspend fun create(session: RefreshSession) {
        ensureIndexes()
        sessions.insertOne(RefreshSessionDocumentCodec.encode(session))
    }

    override suspend fun find(sessionId: String): RefreshSession? {
        ensureIndexes()
        return sessions.find(eq("_id", sessionId)).firstOrNull()
            ?.let(RefreshSessionDocumentCodec::decode)
    }

    override suspend fun rotate(
        sessionId: String,
        currentTokenHash: String,
        nextTokenHash: String,
        user: UserSummary,
        now: Instant,
        expiresAt: Instant,
    ): RefreshSession? {
        ensureIndexes()
        val filter = and(
            eq("_id", sessionId),
            eq("tokenHash", currentTokenHash),
            eq("revokedAt", null),
            gt("expiresAt", Date.from(now)),
        )
        val update = Updates.combine(
            Updates.set("tokenHash", nextTokenHash),
            Updates.set("user", RefreshSessionDocumentCodec.encodeUser(user)),
            Updates.set("lastRotatedAt", Date.from(now)),
            Updates.set("expiresAt", Date.from(expiresAt)),
            Updates.addToSet("previousTokenHashes", currentTokenHash),
            Updates.inc("generation", 1L),
        )
        sessions.findOneAndUpdate(
            filter,
            update,
            FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
        )?.let { return RefreshSessionDocumentCodec.decode(it) }

        revokeOnReplay(sessionId, currentTokenHash, now)
        return null
    }

    override suspend fun revoke(
        sessionId: String,
        presentedTokenHash: String,
        now: Instant,
    ): Boolean {
        ensureIndexes()
        val active = and(eq("_id", sessionId), eq("revokedAt", null))
        val result = sessions.updateOne(
            and(active, eq("tokenHash", presentedTokenHash)),
            Updates.set("revokedAt", Date.from(now)),
        )
        if (result.modifiedCount > 0L) return true
        revokeOnReplay(sessionId, presentedTokenHash, now)
        return false
    }

    private suspend fun revokeOnReplay(sessionId: String, presentedHash: String, now: Instant) {
        val current = sessions.find(eq("_id", sessionId)).firstOrNull() ?: return
        val previousHashes = current.getList("previousTokenHashes", String::class.java).orEmpty()
        if (
            current.getDate("revokedAt") != null ||
            current.getString("tokenHash") == presentedHash ||
            presentedHash !in previousHashes
        ) return
        sessions.updateOne(
            and(
                eq("_id", sessionId),
                eq("tokenHash", current.getString("tokenHash")),
                eq("revokedAt", null),
            ),
            Updates.set("revokedAt", Date.from(now)),
        )
    }

    private suspend fun ensureIndexes() {
        if (indexesReady) return
        indexMutex.withLock {
            if (indexesReady) return@withLock
            sessions.createIndex(
                Indexes.ascending("expiresAt"),
                IndexOptions().expireAfter(0L, TimeUnit.SECONDS).name("ttl_auth_refresh_session"),
            )
            indexesReady = true
        }
    }

    private companion object {
        const val COLLECTION = "auth_refresh_sessions"
    }
}

internal object RefreshSessionDocumentCodec {
    fun encode(session: RefreshSession): Document = Document()
        .append("_id", session.id)
        .append("tokenHash", session.tokenHash)
        .append("user", encodeUser(session.user))
        .append("createdAt", Date.from(session.createdAt))
        .append("expiresAt", Date.from(session.expiresAt))
        .append("generation", session.generation)
        .append("previousTokenHashes", session.previousTokenHashes.toList())
        .append("revokedAt", session.revokedAt?.let(Date::from))

    fun decode(document: Document): RefreshSession {
        val userDocument = document.get("user", Document::class.java)
        return RefreshSession(
            id = document.getString("_id"),
            tokenHash = document.getString("tokenHash"),
            user = decodeUser(userDocument),
            createdAt = document.getDate("createdAt").toInstant(),
            expiresAt = document.getDate("expiresAt").toInstant(),
            generation = (document.get("generation") as? Number)?.toLong() ?: 0L,
            previousTokenHashes = document.getList("previousTokenHashes", String::class.java)
                .orEmpty()
                .toSet(),
            revokedAt = document.getDate("revokedAt")?.toInstant(),
        )
    }

    fun encodeUser(user: UserSummary): Document = Document()
        .append("id", user.id)
        .append("displayName", user.displayName)
        .append("email", user.email)
        .append("roles", user.roles.map { it.name })
        .append("active", user.active)

    private fun decodeUser(document: Document): UserSummary = UserSummary(
        id = document.getString("id"),
        displayName = document.getString("displayName"),
        email = document.getString("email"),
        roles = document.getList("roles", String::class.java).mapNotNull { encoded ->
            runCatching { UserRole.valueOf(encoded) }.getOrNull()
        }.toSet(),
        active = document.getBoolean("active", true),
    )
}