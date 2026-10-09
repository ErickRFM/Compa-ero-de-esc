package org.companerodeescuela.api.auth

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.util.Date
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import org.bson.Document
import org.companerodeescuela.api.integrations.mock.MockIdentityProvider
import org.companerodeescuela.api.plugins.configurePlugins
import org.companerodeescuela.api.test.TestFixtures
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.LoginResponse

class MongoIdentityAuthorityRoutesTest {
    @Test
    fun `native Mongo account requires explicit active true for access refresh and login`() = testApplication {
        val settings = TestFixtures.settings().copy(jwtSecret = "0123456789abcdef0123456789abcdef".toCharArray())
        val fixture = MongoAuthFixture(Document("_id", "native-1")
            .append("displayName", "QA").append("email", "native@example.test").append("emailNormalized", "native@example.test")
            .append("passwordHash", PasswordHasher().hash("test-password")).append("roles", listOf("STUDENT"))
            .append("createdAt", Date()).append("active", true))
        val accounts = MongoPlatformAccountRepository(fixture.database)
        val sessions = InMemoryRefreshSessionRepository()
        val provider = MockIdentityProvider()
        application {
            configurePlugins(settings, sessions, PlatformSessionAuthority(accounts, provider))
            routing { authRoutes(settings, provider, sessions = sessions, accounts = accounts) }
        }
        suspend fun login() = client.post("/auth/login") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"username":"native@example.test","password":"test-password"}""")
        }
        val valid = login()
        assertEquals(HttpStatusCode.OK, valid.status)
        val initial = Json.decodeFromString<ApiResponse<LoginResponse>>(valid.bodyAsText()).data
        for (state in listOf("missing", "null", "false", "string")) {
            when (state) {
                "missing" -> fixture.document.remove("active")
                "null" -> fixture.document["active"] = null
                "false" -> fixture.document["active"] = false
                "string" -> fixture.document["active"] = "true"
            }
            assertEquals(HttpStatusCode.Unauthorized, client.get("/auth/me") {
                header(HttpHeaders.Authorization, "Bearer ${initial.accessToken}")
            }.status, state)
            assertEquals(HttpStatusCode.Unauthorized, client.post("/auth/refresh") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"sessionId":"${initial.sessionId}","refreshToken":"${initial.refreshToken}"}""")
            }.status, state)
            assertEquals(HttpStatusCode.Unauthorized, login().status, state)
        }
    }
}
