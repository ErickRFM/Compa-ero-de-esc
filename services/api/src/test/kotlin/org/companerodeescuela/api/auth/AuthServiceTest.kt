package org.companerodeescuela.api.auth

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.Duration
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
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
    fun `unknown local account performs dummy password verification before provider fallback`() = runTest {
        val hasher = CountingPasswordHasher()

        service(passwordHasher = hasher).login(
            LoginRequest(MockIdentityProvider.MOCK_USERNAME, MockIdentityProvider.MOCK_PASSWORD),
        )

        assertEquals(1, hasher.verifyCalls)
    }

    @Test
    fun `successful login upgrades a legacy password hash`() = runTest {
        val accounts = InMemoryPlatformAccountRepository()
        val password = "Correct horse battery staple"
        val oldHash = legacyHash(password, 210_000)
        val account = PlatformAccount("local-1", "Ana Lopez", "ana@example.test", oldHash, setOf(UserRole.STUDENT))
        assertEquals(true, accounts.create(account))

        val response = service(accounts = accounts).login(LoginRequest(account.email, password))

        assertEquals(account.id, response.user.id)
        assertEquals(600_000, requireNotNull(accounts.findById(account.id)).passwordHash.split(":")[1].toInt())
    }

    @Test
    fun `login rejects oversized and control character usernames`() = runTest {
        val service = service()

        assertFailsWith<ApiException.Validation> {
            service.login(LoginRequest("u".repeat(10_000), "password"))
        }
        assertFailsWith<ApiException.Validation> {
            service.login(LoginRequest("ana\nlopez", "password"))
        }
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
        val clock = MutableClock(BASE_TIME)
        val service = service(sessions = sessions, clock = clock)
        val login = service.login(
            LoginRequest(MockIdentityProvider.MOCK_USERNAME, MockIdentityProvider.MOCK_PASSWORD),
        )
        val rotated = service.refresh(RefreshSessionRequest(login.sessionId, login.refreshToken))
        clock.advance(Duration.ofSeconds(61))

        assertFailsWith<ApiException.Unauthorized> {
            service.refresh(RefreshSessionRequest(login.sessionId, login.refreshToken))
        }
        assertFailsWith<ApiException.Unauthorized> {
            service.refresh(RefreshSessionRequest(rotated.sessionId, rotated.refreshToken))
        }
    }

    @Test
    fun `refresh session has a ninety day absolute lifetime`() = runTest {
        val clock = MutableClock(BASE_TIME)
        val sessions = InMemoryRefreshSessionRepository()
        val service = service(sessions = sessions, clock = clock)
        val login = service.login(LoginRequest(MockIdentityProvider.MOCK_USERNAME, MockIdentityProvider.MOCK_PASSWORD))
        var latest = login
        repeat(3) {
            clock.advance(Duration.ofDays(29))
            latest = service.refresh(RefreshSessionRequest(latest.sessionId, latest.refreshToken))
        }
        clock.advance(Duration.ofDays(2))
        latest = service.refresh(RefreshSessionRequest(latest.sessionId, latest.refreshToken))
        assertEquals(BASE_TIME.plus(Duration.ofDays(90)), sessions.find(latest.sessionId)?.expiresAt)

        clock.advance(Duration.ofDays(1))
        assertFailsWith<ApiException.Unauthorized> {
            service.refresh(RefreshSessionRequest(latest.sessionId, latest.refreshToken))
        }
    }

    @Test
    fun `unknown refresh hash is rejected before refreshing external authority`() = runTest {
        val identity = CountingIdentityProvider()
        val service = service(identityProvider = identity)
        val login = service.login(LoginRequest(MockIdentityProvider.MOCK_USERNAME, MockIdentityProvider.MOCK_PASSWORD))
        val callsAfterLogin = identity.currentStateCalls

        assertFailsWith<ApiException.Unauthorized> {
            service.refresh(RefreshSessionRequest(login.sessionId, "unrecognized-token"))
        }

        assertEquals(callsAfterLogin, identity.currentStateCalls)
    }

    @Test
    fun `refresh history retains only the latest five hashes`() = runTest {
        val sessions = InMemoryRefreshSessionRepository()
        val service = service(sessions = sessions)
        var response = service.login(LoginRequest(MockIdentityProvider.MOCK_USERNAME, MockIdentityProvider.MOCK_PASSWORD))
        repeat(8) {
            response = service.refresh(RefreshSessionRequest(response.sessionId, response.refreshToken))
        }

        assertEquals(5, sessions.find(response.sessionId)?.previousTokenHashes?.size)
    }

    @Test
    fun `retry with immediately previous refresh token inside grace does not revoke family`() = runTest {
        val sessions = InMemoryRefreshSessionRepository()
        val service = service(sessions = sessions)
        val login = service.login(LoginRequest(MockIdentityProvider.MOCK_USERNAME, MockIdentityProvider.MOCK_PASSWORD))
        service.refresh(RefreshSessionRequest(login.sessionId, login.refreshToken))

        val retried = service.refresh(RefreshSessionRequest(login.sessionId, login.refreshToken))
        val next = service.refresh(RefreshSessionRequest(retried.sessionId, retried.refreshToken))

        assertNotEquals(login.refreshToken, retried.refreshToken)
        assertNotEquals(retried.refreshToken, next.refreshToken)
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
    fun `concurrent retry of immediately previous token stays inside grace window`() = runTest {
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

        assertEquals(2, outcomes.count(Result<*>::isSuccess))
        assertEquals(0, outcomes.count(Result<*>::isFailure))
    }

    private fun service(
        sessions: RefreshSessionRepository = InMemoryRefreshSessionRepository(),
        clock: Clock = Clock.fixed(BASE_TIME, ZoneOffset.UTC),
        identityProvider: org.companerodeescuela.api.integrations.identity.IdentityProvider =
            MockIdentityProvider(),
        passwordHasher: PasswordHasher = PasswordHasher(),
        accounts: PlatformAccountRepository = InMemoryPlatformAccountRepository(),
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
        accounts = accounts,
        passwordHasher = passwordHasher,
        clock = clock,
    )

    private fun legacyHash(password: String, iterations: Int): String {
        val salt = ByteArray(16) { it.toByte() }
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        val digest = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        val encoder = Base64.getUrlEncoder().withoutPadding()
        return listOf("pbkdf2-sha256", iterations.toString(), encoder.encodeToString(salt), encoder.encodeToString(digest)).joinToString(":")
    }

    private class CountingPasswordHasher : PasswordHasher() {
        var verifyCalls = 0
        override fun verify(password: String, encoded: String): Boolean {
            verifyCalls++
            return super.verify(password, encoded)
        }
    }

    private companion object {
        val BASE_TIME: Instant = Instant.parse("2026-10-02T12:00:00Z")
    }

    private class MutableClock(private var value: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId): Clock = this
        override fun instant(): Instant = value
        fun advance(duration: Duration) { value = value.plus(duration) }
    }

    private class CountingIdentityProvider : org.companerodeescuela.api.integrations.identity.IdentityProvider by MockIdentityProvider() {
        var currentStateCalls = 0
        override suspend fun currentState(externalId: String): org.companerodeescuela.api.integrations.identity.InstitutionalIdentityState {
            currentStateCalls++
            return requireNotNull(MockIdentityProvider().currentState(externalId))
        }
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
