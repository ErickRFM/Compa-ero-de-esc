package org.companerodeescuela.api.auth

import io.ktor.client.request.header
import io.ktor.client.request.get
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
import kotlin.test.assertNotEquals
import kotlinx.serialization.json.Json
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.config.Environment
import org.companerodeescuela.api.config.MongoSettings
import org.companerodeescuela.api.plugins.configurePlugins
import org.companerodeescuela.api.test.TestFixtures
import org.companerodeescuela.api.integrations.mock.MockIdentityProvider
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.LoginResponse

class AuthRoutesTest {
    private val json = Json

    @Test
    fun `login refresh rotates token and logout revokes it`() = testApplication {
        val sessions = InMemoryRefreshSessionRepository()
        val settings = authenticatedSettings()
        application {
            configurePlugins(settings, refreshSessions = sessions)
            routing {
                authRoutes(settings, MockIdentityProvider(), sessions = sessions)
            }
        }

        val login = client.post("/auth/login") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"username":"ana.lopez","password":"development-only"}""")
        }
        assertEquals(HttpStatusCode.OK, login.status)
        val initial = json.decodeFromString<ApiResponse<LoginResponse>>(login.bodyAsText()).data

        val refresh = client.post("/auth/refresh") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                """{"sessionId":"${initial.sessionId}","refreshToken":"${initial.refreshToken}"}""",
            )
        }
        assertEquals(HttpStatusCode.OK, refresh.status)
        val rotated = json.decodeFromString<ApiResponse<LoginResponse>>(refresh.bodyAsText()).data
        assertNotEquals(initial.refreshToken, rotated.refreshToken)

        val logout = client.post("/auth/logout") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                """{"sessionId":"${rotated.sessionId}","refreshToken":"${rotated.refreshToken}"}""",
            )
        }
        assertEquals(HttpStatusCode.NoContent, logout.status)

        val meAfterLogout = client.get("/auth/me") {
            header(HttpHeaders.Authorization, "Bearer ${rotated.accessToken}")
        }
        assertEquals(HttpStatusCode.Unauthorized, meAfterLogout.status)

        val afterLogout = client.post("/auth/refresh") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                """{"sessionId":"${rotated.sessionId}","refreshToken":"${rotated.refreshToken}"}""",
            )
        }
        assertEquals(HttpStatusCode.Unauthorized, afterLogout.status)
    }

    @Test
    fun `unknown session is denied by refresh route`() = testApplication {
        val settings = authenticatedSettings()
        val sessions = InMemoryRefreshSessionRepository()
        application {
            configurePlugins(settings, refreshSessions = sessions)
            routing {
                authRoutes(settings, MockIdentityProvider(), sessions = sessions)
            }
        }

        val response = client.post("/auth/refresh") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"sessionId":"missing","refreshToken":"not-a-token"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    private fun authenticatedSettings(): ApiSettings = TestFixtures.settings().copy(
        jwtSecret = "0123456789abcdef0123456789abcdef".toCharArray(),
    )
}