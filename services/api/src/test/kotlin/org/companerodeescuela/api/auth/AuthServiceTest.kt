package org.companerodeescuela.api.auth

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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

    private fun service(): AuthService = AuthService(
        identityProvider = MockIdentityProvider(),
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
            clock = Clock.fixed(Instant.parse("2026-10-02T12:00:00Z"), ZoneOffset.UTC),
        ),
    )
}
