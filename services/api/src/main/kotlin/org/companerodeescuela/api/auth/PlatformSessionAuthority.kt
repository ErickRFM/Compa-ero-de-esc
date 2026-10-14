package org.companerodeescuela.api.auth

import com.auth0.jwt.interfaces.Payload
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.IntegrationException
import org.companerodeescuela.api.integrations.identity.IdentityProvider
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.UserSummary

/** Resolves live identity from the source that authenticated this session; never guesses another source. */
class PlatformSessionAuthority(
    private val accounts: PlatformAccountRepository,
    private val identityProvider: IdentityProvider,
    private val clock: Clock = Clock.systemUTC(),
    private val currentUserCacheTtlMillis: Int = 0,
    private val monotonicNanos: () -> Long = System::nanoTime,
) {
    private val currentUserCache = ConcurrentHashMap<String, CachedUser>()

    init {
        require(currentUserCacheTtlMillis in 0..MAX_CURRENT_USER_CACHE_TTL_MILLIS)
    }

    suspend fun permits(payload: Payload, session: RefreshSession, tokens: AuthTokenService): Boolean {
        val now = clock.instant()
        if (payload.subject != session.user.id || session.revokedAt != null || session.expiresAt <= now ||
            payload.expiresAt?.toInstant()?.isAfter(now) != true ||
            tokens.sessionGenerationFrom(payload) != session.generation) return false
        val claimed = runCatching { tokens.userFrom(payload) }.getOrNull() ?: return false
        if (!claimed.active) return false
        val current = currentUser(session) ?: return false
        return current.id == claimed.id && current.roles == claimed.roles &&
            current.accountStatus == claimed.accountStatus && current.authRevision == claimed.authRevision &&
            current.institutionId == claimed.institutionId
    }

    suspend fun currentUser(session: RefreshSession): UserSummary? = try {
        // Validate the persisted snapshot before resolving or replacing live authority.
        if (
            !session.user.active
                || !session.user.accountStatus.permitsSession
                || session.user.authRevision < 0
                || session.generation < 0
        ) {
            null
        } else {
            cachedUser(session) ?: resolveCurrentUser(session)?.also { liveUser ->
                cacheUser(session, liveUser)
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: IntegrationException) {
        if (error.category in setOf(IntegrationException.Category.NOT_FOUND, IntegrationException.Category.UNAUTHORIZED)) null
        else throw ApiException.DependencyUnavailable("Identity authority is temporarily unavailable")
    } catch (_: Exception) {
        // Never propagate database URLs, provider payloads or account identifiers to technical logs.
        throw ApiException.DependencyUnavailable("Identity authority is temporarily unavailable")
    }

    private suspend fun resolveCurrentUser(session: RefreshSession): UserSummary? =
        when (session.identitySource) {
            SessionIdentitySource.LEGACY -> null // Unknown source requires fresh credentials, not inference.
            SessionIdentitySource.NATIVE ->
                accounts.findById(session.user.id)?.let {
                    if (it.authRevision != session.user.authRevision) null else it.toUserSummary()
                }
            SessionIdentitySource.INSTITUTIONAL ->
                identityProvider.currentState(session.user.id)?.let {
                    if (it.externalId != session.user.id) null else session.user.copy(roles = it.roles, active = it.active)
                }
        }?.takeIf {
            it.id == session.user.id && it.active && it.accountStatus.permitsSession
                && (it.roles.isNotEmpty() || it.accountStatus != AccountStatus.ACTIVE)
        }

    private fun cachedUser(session: RefreshSession): UserSummary? {
        if (currentUserCacheTtlMillis == 0) return null
        val now = monotonicNanos()
        val cached = currentUserCache[session.id] ?: return null
        if (
            cached.generation == session.generation &&
            cached.authRevision == session.user.authRevision &&
            cached.identitySource == session.identitySource &&
            now - cached.expiresAtNanos < 0
        ) {
            return cached.user
        }
        currentUserCache.remove(session.id, cached)
        return null
    }

    private fun cacheUser(
        session: RefreshSession,
        user: UserSummary,
    ) {
        if (currentUserCacheTtlMillis == 0) return
        val now = monotonicNanos()
        val entry = CachedUser(
            generation = session.generation,
            authRevision = session.user.authRevision,
            identitySource = session.identitySource,
            expiresAtNanos = now + currentUserCacheTtlMillis * NANOS_PER_MILLISECOND,
            user = user,
        )
        synchronized(currentUserCache) {
            if (currentUserCache.size >= MAX_CURRENT_USER_CACHE_ENTRIES) {
                currentUserCache.entries.removeIf { now - it.value.expiresAtNanos >= 0 }
            }
            if (currentUserCache.size >= MAX_CURRENT_USER_CACHE_ENTRIES) {
                currentUserCache.keys.firstOrNull()?.let(currentUserCache::remove)
            }
            currentUserCache[session.id] = entry
        }
    }

    private data class CachedUser(
        val generation: Long,
        val authRevision: Long,
        val identitySource: SessionIdentitySource,
        val expiresAtNanos: Long,
        val user: UserSummary,
    )

    private companion object {
        const val MAX_CURRENT_USER_CACHE_TTL_MILLIS = 5_000
        const val MAX_CURRENT_USER_CACHE_ENTRIES = 10_000
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
