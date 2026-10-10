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
import io.ktor.server.routing.get
import io.ktor.server.response.respond
import io.ktor.server.auth.authenticate
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

class AccountLifecycleRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }
    @Test
    fun `suspended revoked and unknown account state override legacy active flag`() = scenario { fixture, initial ->
        for (status in listOf("SUSPENDED", "REVOKED", "unknown", null)) {
            fixture.document["accountStatus"] = status
            assertEquals(HttpStatusCode.Unauthorized, me(initial).status, status)
            assertEquals(HttpStatusCode.Unauthorized, refresh(initial).status, status)
            assertEquals(HttpStatusCode.Unauthorized, login().status, status)
        }
    }

    @Test
    fun `permission revision invalidates access and refresh even after account is reactivated`() = scenario { fixture, initial ->
        fixture.document["accountStatus"] = "SUSPENDED"
        fixture.document["authRevision"] = 1L
        fixture.document["accountStatus"] = "ACTIVE"
        fixture.document["authRevision"] = 2L
        assertEquals(HttpStatusCode.Unauthorized, me(initial).status)
        assertEquals(HttpStatusCode.Unauthorized, refresh(initial).status)
        val newLogin = login()
        assertEquals(HttpStatusCode.OK, newLogin.status)
        val current = decode(newLogin.bodyAsText())
        assertEquals(HttpStatusCode.OK, me(current).status)
    }

    @Test
    fun `pending identity can inspect own session without academic permissions`() = scenario { fixture, _ ->
        for (status in listOf("PENDING_VERIFICATION", "PENDING_APPROVAL")) {
            fixture.document["accountStatus"] = status
            val response = login()
            assertEquals(HttpStatusCode.OK, response.status, status)
            val pending = decode(response.bodyAsText())
            assertEquals(emptySet(), pending.user.roles, status)
            assertEquals(HttpStatusCode.OK, me(pending).status, status)
            assertEquals(HttpStatusCode.Unauthorized, client.get("/academic-probe") {
                header(HttpHeaders.Authorization, "Bearer ${pending.accessToken}")
            }.status, status)
        }
    }

    @Test
    fun `malformed authority revision cannot authenticate`() = scenario { fixture, initial ->
        for (revision in listOf<Any?>(-1L, "0", null)) {
            fixture.document["authRevision"] = revision
            assertEquals(HttpStatusCode.Unauthorized, me(initial).status, revision.toString())
            assertEquals(HttpStatusCode.Unauthorized, refresh(initial).status, revision.toString())
            assertEquals(HttpStatusCode.Unauthorized, login().status, revision.toString())
        }
    }

    private fun scenario(block: suspend io.ktor.server.testing.ApplicationTestBuilder.(MongoAuthFixture, LoginResponse) -> Unit) = testApplication {
        val settings = TestFixtures.settings().copy(jwtSecret = "0123456789abcdef0123456789abcdef".toCharArray())
        val fixture = MongoAuthFixture(Document("_id", "native-lifecycle")
            .append("displayName", "QA").append("email", "native@example.test").append("emailNormalized", "native@example.test")
            .append("passwordHash", PasswordHasher().hash("test-password")).append("roles", listOf("STUDENT"))
            .append("createdAt", Date()).append("active", true))
        val accounts = MongoPlatformAccountRepository(fixture.database)
        val sessions = InMemoryRefreshSessionRepository()
        val provider = MockIdentityProvider()
        application {
            configurePlugins(settings, sessions, PlatformSessionAuthority(accounts, provider))
            routing {
                authRoutes(settings, provider, sessions = sessions, accounts = accounts)
                authenticate(AuthTokenService.PROVIDER_NAME) { get("/academic-probe") { call.respond("academic") } }
            }
        }
        val first = login()
        assertEquals(HttpStatusCode.OK, first.status)
        block(fixture, decode(first.bodyAsText()))
    }

    private fun decode(body: String): LoginResponse = json
        .decodeFromString<ApiResponse<LoginResponse>>(body).data

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.login() = client.post("/auth/login") {
        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        setBody("""{"username":"native@example.test","password":"test-password"}""")
    }
    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.me(session: LoginResponse) = client.get("/auth/me") {
        header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
    }
    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.refresh(session: LoginResponse) = client.post("/auth/refresh") {
        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        setBody("""{"sessionId":"${session.sessionId}","refreshToken":"${session.refreshToken}"}""")
    }
}