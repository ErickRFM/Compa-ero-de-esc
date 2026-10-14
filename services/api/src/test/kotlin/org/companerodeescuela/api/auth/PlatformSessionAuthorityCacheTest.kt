package org.companerodeescuela.api.auth

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.integrations.identity.AuthenticatedAccount
import org.companerodeescuela.api.integrations.identity.IdentityProvider
import org.companerodeescuela.api.integrations.identity.InstitutionalCredentials
import org.companerodeescuela.api.integrations.identity.InstitutionalIdentityState
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary

class PlatformSessionAuthorityCacheTest {
    @Test
    fun `configured session cache reuses live identity only until its ttl`() = runTest {
        var nowNanos = 0L
        val provider = CountingIdentityProvider()
        val authority = PlatformSessionAuthority(
            InMemoryPlatformAccountRepository(),
            provider,
            Clock.fixed(Instant.parse("2026-10-13T00:00:00Z"), ZoneOffset.UTC),
            currentUserCacheTtlMillis = 5_000,
            monotonicNanos = { nowNanos },
        )
        val session = session()

        assertEquals(setOf(UserRole.TEACHER), authority.currentUser(session)?.roles)
        provider.roles = setOf(UserRole.STUDENT)
        nowNanos = 4_999_000_000L
        assertEquals(setOf(UserRole.TEACHER), authority.currentUser(session)?.roles)
        assertEquals(1, provider.calls)

        nowNanos = 5_000_000_000L
        assertEquals(setOf(UserRole.STUDENT), authority.currentUser(session)?.roles)
        assertEquals(2, provider.calls)
    }

    @Test
    fun `zero ttl bypasses session cache`() = runTest {
        val provider = CountingIdentityProvider()
        val authority = PlatformSessionAuthority(InMemoryPlatformAccountRepository(), provider)

        authority.currentUser(session())
        authority.currentUser(session())

        assertEquals(2, provider.calls)
    }

    private fun session() = RefreshSession(
        id = "session-1",
        tokenHash = "hash",
        user = UserSummary(
            id = "teacher-1",
            displayName = "Teacher",
            roles = setOf(UserRole.TEACHER),
            accountStatus = AccountStatus.ACTIVE,
        ),
        createdAt = Instant.parse("2026-10-13T00:00:00Z"),
        expiresAt = Instant.parse("2026-10-14T00:00:00Z"),
        generation = 1,
        identitySource = SessionIdentitySource.INSTITUTIONAL,
    )

    private class CountingIdentityProvider : IdentityProvider {
        override val id = "counting"
        override val displayName = "Counting"
        var calls = 0
        var roles = setOf(UserRole.TEACHER)

        override suspend fun authenticate(credentials: InstitutionalCredentials): AuthenticatedAccount? = null
        override suspend fun refreshRoles(externalId: String): Set<UserRole> = roles
        override suspend fun currentState(externalId: String): InstitutionalIdentityState {
            calls += 1
            return InstitutionalIdentityState(externalId, active = true, roles = roles)
        }
    }
}
