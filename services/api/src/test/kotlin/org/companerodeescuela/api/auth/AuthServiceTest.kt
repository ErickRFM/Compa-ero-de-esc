package org.companerodeescuela.api.auth

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.Duration
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.config.Environment
import org.companerodeescuela.api.config.MongoSettings
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.mock.MockIdentityProvider
import org.companerodeescuela.shared.contracts.LoginRequest
import org.companerodeescuela.shared.contracts.RefreshSessionRequest
import org.companerodeescuela.shared.contracts.UserRole

class AuthServiceTest {

    @Test
    fun `valid institutional credentials become a platform session`() = runTest {
        val service = service()

        val response = service.login(
            LoginRequest(
                username = MockIdentityProvider.MOCK_USERNAME,
                password = MockIdentityProvider.MOCK_PASSWORD,
            ),
        )

        assertEquals(MockIdentityProvider.MOCK_ACCOUNT.externalId, response.user.id)
        assertEquals(setOf(UserRole.STUDENT), response.user.roles)
    }

    @Test
    fun `development teacher credentials expose teacher role`() = runTest {
        val response = service().login(
            LoginRequest(
                username = MockIdentityProvider.MOCK_TEACHER_USERNAME,
                password = MockIdentityProvider.MOCK_TEACHER_PASSWORD,
            ),
        )

        assertEquals(MockIdentityProvider.MOCK_TEACHER_ACCOUNT.externalId, response.user.id)
        assertEquals(setOf(UserRole.TEACHER), response.user.roles)
    }

    @Test
    fun `wrong password and unknown user share the same public error`() = runTest {
        val service = service()

        val wrongPassword = assertFailsWith<ApiException.Unauthorized> {
            service.login(LoginRequest(MockIdentityProvider.MOCK_USERNAME, "wrong"))
        }
        val unknownUser = assertFailsWith<ApiException.Unauthorized> {
            service.login(LoginRequest("nobody", "wrong"))
        }

        assertEquals(wrongPassword.userMessage, unknownUser.userMessage)
        assertEquals("Invalid username or password", wrongPassword.userMessage)
    }

    @Test
    fun `login stores only hash of opaque refresh token and refresh rotates it`() = runTest {
        val sessions = InMemoryRefreshSessionRepository()
        val service = service(sessions = sessions)
        val login = service.login(
            LoginRequest(MockIdentityProvider.MOCK_USERNAME, MockIdentityProvider.MOCK_PASSWORD),
        )
        val stored = assertNotNull(sessions.find(login.sessionId))

        assertNotEquals(login.refreshToken, stored.tokenHash)
        assertEquals(0L, stored.generation)
        assertEquals(BASE_TIME.plus(Duration.ofDays(30)), stored.expiresAt)

        val rotated = service.refresh(RefreshSessionRequest(login.sessionId, login.refreshToken))
        val afterRotation = assertNotNull(sessions.find(login.sessionId))

        assertEquals(login.sessionId, rotated.sessionId)
        assertNotEquals(login.refreshToken, rotated.refreshToken)
        assertEquals(1L, afterRotation.generation)
        assertNotEquals(stored.tokenHash, afterRotation.tokenHash)
    }

    @Test
    fun `replayed refresh token revokes the complete session family`() = runTest {
        val sessions = InMemoryRefreshSessionRepository()
        val service = service(sessions = sessions)
        val login = service.login(
            LoginRequest(MockIdentityProvider.MOCK_USERNAME, MockIdentityProvider.MOCK_PASSWORD),
        )
        val rotated = service.refresh(RefreshSessionRequest(login.sessionId, login.refreshToken))

        assertFailsWith<ApiException.Unauthorized> {
            service.refresh(RefreshSessionRequest(login.sessionId, login.refreshToken))
        }
        assertFailsWith<ApiException.Unauthorized> {
            service.refresh(RefreshSessionRequest(rotated.sessionId, rotated.refreshToken))
        }
    }

    @Test
    fun `unknown refresh token does not revoke a valid session`() = runTest {
        val service = service()
        val login = service.login(
            LoginRequest(MockIdentityProvider.MOCK_USERNAME, MockIdentityProvider.MOCK_PASSWORD),
        )

        assertFailsWith<ApiException.Unauthorized> {
            service.refresh(RefreshSessionRequest(login.sessionId, "unrecognized-token"))
        }
        val validRotation = service.refresh(RefreshSessionRequest(login.sessionId, login.refreshToken))

        assertNotEquals(login.refreshToken, validRotation.refreshToken)
    }

    @Test
    fun `logout revokes refresh session`() = runTest {
        val sessions = InMemoryRefreshSessionRepository()
        val service = service(sessions = sessions)
        val login = service.login(
            LoginRequest(MockIdentityProvider.MOCK_USERNAME, MockIdentityProvider.MOCK_PASSWORD),
        )

        service.logout(RefreshSessionRequest(login.sessionId, login.refreshToken))

        assertFailsWith<ApiException.Unauthorized> {
            service.refresh(RefreshSessionRequest(login.sessionId, login.refreshToken))
        }
    }

    @Test
    fun `expired refresh session cannot be restored`() = runTest {
        val sessions = InMemoryRefreshSessionRepository()
        val login = service(sessions = sessions).login(
            LoginRequest(MockIdentityProvider.MOCK_USERNAME, MockIdentityProvider.MOCK_PASSWORD),
        )
        val expiredService = service(
            sessions = sessions,
            clock = Clock.fixed(BASE_TIME.plus(Duration.ofDays(31)), ZoneOffset.UTC),
        )

        assertFailsWith<ApiException.Unauthorized> {
            expiredService.refresh(RefreshSessionRequest(login.sessionId, login.refreshToken))
        }
    }

    @Test
    fun `refresh resolves current roles instead of preserving stale token roles`() = runTest {
        val service = service(identityProvider = RoleChangingIdentityProvider())
        val login = service.login(
            LoginRequest(MockIdentityProvider.MOCK_USERNAME, MockIdentityProvider.MOCK_PASSWORD),
        )

        val refreshed = service.refresh(RefreshSessionRequest(login.sessionId, login.refreshToken))

        assertEquals(setOf(UserRole.TEACHER), refreshed.user.roles)
    }

    @Test
    fun `concurrent use of one refresh token grants at most one rotation and revokes replay`() = runTest {
        val sessions = InMemoryRefreshSessionRepository()
        val service = service(sessions = sessions)
        val login = service.login(
            LoginRequest(MockIdentityProvider.MOCK_USERNAME, MockIdentityProvider.MOCK_PASSWORD),
        )
        val request = RefreshSessionRequest(login.sessionId, login.refreshToken)

        val outcomes = coroutineScope {
            listOf(
                async { runCatching { service.refresh(request) } },
                async { runCatching { service.refresh(request) } },
            ).map { it.await() }
        }

        assertEquals(1, outcomes.count(Result<*>::isSuccess))
        assertEquals(1, outcomes.count(Result<*>::isFailure))
        assertFailsWith<ApiException.Unauthorized> {
            service.refresh(RefreshSessionRequest(login.sessionId, login.refreshToken))
        }
    }

    private fun service(
        sessions: RefreshSessionRepository = InMemoryRefreshSessionRepository(),
        clock: Clock = Clock.fixed(BASE_TIME, ZoneOffset.UTC),
        identityProvider: org.companerodeescuela.api.integrations.identity.IdentityProvider =
            MockIdentityProvider(),
    ): AuthService = AuthService(
        identityProvider = identityProvider,
        tokenService = AuthTokenService(
            settings = ApiSettings(
                serviceName = "test",
                environment = Environment.LOCAL,
                host = "127.0.0.1",
                port = 8080,
                version = "test",
                apiVersion = "v1",
                mongo = MongoSettings(uri = "", databaseName = "test"),
                jwtSecret = "0123456789abcdef0123456789abcdef".toCharArray(),
                jwtIssuer = "test-issuer",
                jwtAudience = "test-audience",
            ),
            clock = clock,
        ),
        sessions = sessions,
        clock = clock,
    )

    private companion object {
        val BASE_TIME: Instant = Instant.parse("2026-10-02T12:00:00Z")
    }

    private class RoleChangingIdentityProvider :
        org.companerodeescuela.api.integrations.identity.IdentityProvider by MockIdentityProvider() {
        override suspend fun refreshRoles(externalId: String): Set<UserRole> =
            setOf(UserRole.TEACHER)
        private var firstState = true
        override suspend fun currentState(externalId: String): org.companerodeescuela.api.integrations.identity.InstitutionalIdentityState {
            val roles = if (firstState) setOf(UserRole.STUDENT) else setOf(UserRole.TEACHER)
            firstState = false
            return org.companerodeescuela.api.integrations.identity.InstitutionalIdentityState(externalId, true, roles)
        }
    }
}
