package org.companerodeescuela.api.auth

import com.auth0.jwt.interfaces.Payload
import java.time.Clock
import kotlinx.coroutines.CancellationException
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.IntegrationException
import org.companerodeescuela.api.integrations.identity.IdentityProvider
import org.companerodeescuela.shared.contracts.UserSummary

/** Resolves live identity from the source that authenticated this session; never guesses another source. */
class PlatformSessionAuthority(
    private val accounts: PlatformAccountRepository,
    private val identityProvider: IdentityProvider,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun permits(payload: Payload, session: RefreshSession, tokens: AuthTokenService): Boolean {
        val now = clock.instant()
        if (payload.subject != session.user.id || session.revokedAt != null || session.expiresAt <= now ||
            payload.expiresAt?.toInstant()?.isAfter(now) != true ||
            tokens.sessionGenerationFrom(payload) != session.generation) return false
        val claimed = runCatching { tokens.userFrom(payload) }.getOrNull() ?: return false
        if (!claimed.active) return false
        val current = currentUser(session) ?: return false
        return current.id == claimed.id && current.roles == claimed.roles
    }

    suspend fun currentUser(session: RefreshSession): UserSummary? = try {
        when (session.identitySource) {
            SessionIdentitySource.LEGACY -> null // Unknown source requires fresh credentials, not inference.
            SessionIdentitySource.NATIVE -> accounts.findById(session.user.id)?.let {
                UserSummary(it.id, it.displayName, it.email, it.roles, active = it.active)
            }
            SessionIdentitySource.INSTITUTIONAL -> identityProvider.currentState(session.user.id)?.let {
                if (it.externalId != session.user.id) null else session.user.copy(roles = it.roles, active = it.active)
            }
        }?.takeIf { it.id == session.user.id && it.active && it.roles.isNotEmpty() }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: IntegrationException) {
        if (error.category in setOf(IntegrationException.Category.NOT_FOUND, IntegrationException.Category.UNAUTHORIZED)) null
        else throw ApiException.DependencyUnavailable("Identity authority is temporarily unavailable")
    } catch (_: Exception) {
        // Never propagate database URLs, provider payloads or account identifiers to technical logs.
        throw ApiException.DependencyUnavailable("Identity authority is temporarily unavailable")
    }
}
