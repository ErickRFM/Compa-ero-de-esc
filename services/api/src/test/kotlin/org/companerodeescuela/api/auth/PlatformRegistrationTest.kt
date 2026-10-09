package org.companerodeescuela.api.auth

import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.config.Environment
import org.companerodeescuela.api.config.MongoSettings
import org.companerodeescuela.api.integrations.mock.MockIdentityProvider
import org.companerodeescuela.api.plugins.configurePlugins
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.LoginResponse
import org.companerodeescuela.shared.contracts.UserRole

class PlatformRegistrationTest {
    private val json = Json

    @Test
    fun `student can register and login without institutional identity`() = testApplication {
        val sessions = InMemoryRefreshSessionRepository()
        val accounts = InMemoryPlatformAccountRepository()
        val settings = authenticatedSettings()
        application {
            configurePlugins(settings, refreshSessions = sessions,
                sessionAuthority = PlatformSessionAuthority(accounts, MockIdentityProvider()))
            routing {
                authRoutes(
                    settings = settings,
                    identityProvider = MockIdentityProvider(),
                    sessions = sessions,
                    accounts = accounts,
                )
            }
        }

        val registration = client.post("/auth/register") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                """{"displayName":"Ricardo","email":"ricardo@example.com","password":"password-123","accountType":"STUDENT"}""",
            )
        }
        assertEquals(HttpStatusCode.Created, registration.status)
        val registered = json.decodeFromString<ApiResponse<LoginResponse>>(
            registration.bodyAsText(),
        ).data
        assertEquals(setOf(UserRole.STUDENT), registered.user.roles)

        val login = client.post("/auth/login") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"username":"ricardo@example.com","password":"password-123"}""")
        }
        assertEquals(HttpStatusCode.OK, login.status)
    }

    @Test
    fun `teacher registration is pending and duplicate email is rejected`() = testApplication {
        val sessions = InMemoryRefreshSessionRepository()
        val accounts = InMemoryPlatformAccountRepository()
        val settings = authenticatedSettings()
        application {
            configurePlugins(settings, refreshSessions = sessions,
                sessionAuthority = PlatformSessionAuthority(accounts, MockIdentityProvider()))
            routing {
                authRoutes(
                    settings = settings,
                    identityProvider = MockIdentityProvider(),
                    sessions = sessions,
                    accounts = accounts,
                )
            }
        }

        val body =
            """{"displayName":"Profa. Elena","email":"teacher@example.com","password":"password-123","accountType":"TEACHER"}"""
        val first = client.post("/auth/register") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(body)
        }
        assertEquals(HttpStatusCode.Created, first.status)
        val registered = json.decodeFromString<ApiResponse<LoginResponse>>(first.bodyAsText()).data
        assertEquals(setOf(UserRole.TEACHER_PENDING), registered.user.roles)

        val duplicate = client.post("/auth/register") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(body)
        }
        assertEquals(HttpStatusCode.Conflict, duplicate.status)
    }

    private fun authenticatedSettings(): ApiSettings = ApiSettings(
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
