package org.companerodeescuela.api.auth

import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.companerodeescuela.shared.contracts.UserSummary

data class RefreshSession(
    val id: String,
    val tokenHash: String,
    val user: UserSummary,
    val createdAt: Instant,
    val expiresAt: Instant,
    val generation: Long = 0,
    val previousTokenHashes: Set<String> = emptySet(),
    val revokedAt: Instant? = null,
)

interface RefreshSessionRepository {
    suspend fun create(session: RefreshSession)
    suspend fun find(sessionId: String): RefreshSession?

    /** Atomically rotates the active token; a stale-token replay revokes its family. */
    suspend fun rotate(
        sessionId: String,
        currentTokenHash: String,
        nextTokenHash: String,
        user: UserSummary,
        now: Instant,
        expiresAt: Instant,
    ): RefreshSession?

    suspend fun revoke(sessionId: String, presentedTokenHash: String, now: Instant): Boolean
}

class InMemoryRefreshSessionRepository : RefreshSessionRepository {
    private val mutex = Mutex()
    private val sessions = mutableMapOf<String, RefreshSession>()

    override suspend fun create(session: RefreshSession) {
        mutex.withLock {
            check(session.id !in sessions) { "Refresh session already exists" }
            sessions[session.id] = session
        }
    }

    override suspend fun find(sessionId: String): RefreshSession? = mutex.withLock {
        sessions[sessionId]
    }

    override suspend fun rotate(
        sessionId: String,
        currentTokenHash: String,
        nextTokenHash: String,
        user: UserSummary,
        now: Instant,
        expiresAt: Instant,
    ): RefreshSession? = mutex.withLock {
        val current = sessions[sessionId] ?: return@withLock null
        if (current.revokedAt != null || current.expiresAt <= now) return@withLock null
        if (current.tokenHash != currentTokenHash) {
            if (currentTokenHash in current.previousTokenHashes) {
                sessions[sessionId] = current.copy(revokedAt = now)
            }
            return@withLock null
        }

        current.copy(
            tokenHash = nextTokenHash,
            user = user,
            generation = current.generation + 1,
            previousTokenHashes = current.previousTokenHashes + currentTokenHash,
            expiresAt = expiresAt,
        ).also { sessions[sessionId] = it }
    }

    override suspend fun revoke(
        sessionId: String,
        presentedTokenHash: String,
        now: Instant,
    ): Boolean = mutex.withLock {
        val current = sessions[sessionId] ?: return@withLock false
        if (current.revokedAt != null) return@withLock false
        if (current.tokenHash != presentedTokenHash) {
            if (presentedTokenHash in current.previousTokenHashes) {
                sessions[sessionId] = current.copy(revokedAt = now)
            }
            return@withLock false
        }
        sessions[sessionId] = current.copy(revokedAt = now)
        true
    }
}