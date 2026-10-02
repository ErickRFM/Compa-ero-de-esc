package org.companerodeescuela.api.auth

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.config.Environment
import org.companerodeescuela.api.config.MongoSettings
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary

class AuthTokenServiceTest {

    @Test
    fun `issued token round-trips only public platform identity`() {
        val service = AuthTokenService(
            settings = settings(),
            clock = Clock.fixed(Instant.parse("2026-10-02T12:00:00Z"), ZoneOffset.UTC),
        )
        val user = UserSummary(
            id = "student-1",
            displayName = "Ana López",
            email = "ana@example.edu",
            roles = setOf(UserRole.STUDENT),
        )

        val response = service.issue(user)
        val decoded = service.verifier.verify(response.accessToken)
        val restored = service.userFrom(decoded)

        assertEquals(user, restored)
        assertEquals(Instant.parse("2026-10-02T12:15:00Z").epochSecond, response.expiresAtEpochSeconds)
        assertTrue(response.accessToken.isNotBlank())
        assertTrue(response.accessToken.contains('.'))
    }

    private fun settings(): ApiSettings = ApiSettings(
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
    )
}
