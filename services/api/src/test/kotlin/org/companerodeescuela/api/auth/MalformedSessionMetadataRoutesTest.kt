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
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.bson.Document
import org.companerodeescuela.api.integrations.mock.MockIdentityProvider
import org.companerodeescuela.api.plugins.configurePlugins
import org.companerodeescuela.api.test.TestFixtures
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.LoginResponse

class MalformedSessionMetadataRoutesTest {
    private val json = Json
    private val secret = "0123456789abcdef0123456789abcdef"

    @Test
    fun `explicit revoked suspended unknown or null persisted snapshot never becomes live authority`() = scenario { fixture, initial ->
        val original = Document(fixture.document.get("user", Document::class.java))
        for (status in listOf("REVOKED", "SUSPENDED", "unknown", null)) {
            fixture.document["user"] = Document(original).append("accountStatus", status)
            assertEquals(HttpStatusCode.Unauthorized, me(initial.accessToken), status)
            assertEquals(HttpStatusCode.Unauthorized, refresh(initial), status)
        }
        fixture.document["user"] = Document(original).append("active", false)
        assertEquals(HttpStatusCode.Unauthorized, me(initial.accessToken))
        assertEquals(HttpStatusCode.Unauthorized, refresh(initial))
    }

    @Test
    fun `persisted fractional nonfinite negative null and string revisions fail closed`() = scenario { fixture, initial ->
        val original = Document(fixture.document.get("user", Document::class.java))
        for (revision in listOf<Any?>(0.5, -0.5, 0.0, Double.NaN, Double.POSITIVE_INFINITY, -1L, "0", null)) {
            fixture.document["user"] = Document(original).append("authRevision", revision)
            assertEquals(HttpStatusCode.Unauthorized, me(initial.accessToken), revision.toString())
            assertEquals(HttpStatusCode.Unauthorized, refresh(initial), revision.toString())
        }
    }

    @Test
    fun `server signed fractional overflow null and string JWT revisions cannot authenticate`() = scenario { _, initial ->
        for (revision in listOf("0.5", "-0.5", "0.0", "9223372036854775808", "null", "\"0\"", "[]", "{}")) {
            assertEquals(HttpStatusCode.Unauthorized, me(rewriteSignedRevision(initial.accessToken, revision)), revision)
        }
    }

    @Test
    fun `persisted malformed generation cannot authenticate or refresh`() = scenario { fixture, initial ->
        for (generation in listOf<Any?>(0.5, -0.5, 0.0, Double.NaN, -1L, "0", null)) {
            fixture.document["generation"] = generation
            assertEquals(HttpStatusCode.Unauthorized, me(initial.accessToken), generation.toString())
            assertEquals(HttpStatusCode.Unauthorized, refresh(initial), generation.toString())
        }
    }

    @Test
    fun `server signed malformed generation cannot authenticate`() = scenario { _, initial ->
        for (generation in listOf("0.5", "-0.5", "0.0", "9223372036854775808", "null", "\"0\"")) {
            assertEquals(HttpStatusCode.Unauthorized, me(rewriteSignedRevision(initial.accessToken, generation, "session_generation")), generation)
        }
    }

    @Test
    fun `truly absent legacy snapshot lifecycle fields remain compatible`() = scenario { fixture, initial ->
        val user = fixture.document.get("user", Document::class.java)
        user.remove("accountStatus")
        user.remove("authRevision")
        assertEquals(HttpStatusCode.OK, me(initial.accessToken))
        assertEquals(HttpStatusCode.OK, refresh(initial))
    }

    private fun scenario(block: suspend io.ktor.server.testing.ApplicationTestBuilder.(MongoAuthFixture, LoginResponse) -> Unit) = testApplication {
        val settings = TestFixtures.settings().copy(jwtSecret = secret.toCharArray())
        val backing = InMemoryRefreshSessionRepository()
        var persisted: MongoAuthFixture? = null
        val sessions = object : RefreshSessionRepository by backing {
            override suspend fun find(sessionId: String): RefreshSession? = persisted?.let {
                MongoRefreshSessionRepository(it.database).find(sessionId)
            } ?: backing.find(sessionId)
        }
        val accounts = InMemoryPlatformAccountRepository()
        val provider = MockIdentityProvider()
        application {
            configurePlugins(settings, sessions, PlatformSessionAuthority(accounts, provider))
            routing { authRoutes(settings, provider, sessions = sessions, accounts = accounts) }
        }
        val registered = client.post("/auth/register") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"displayName":"QA","email":"qa@example.test","password":"test-password","accountType":"STUDENT"}""")
        }
        assertEquals(HttpStatusCode.Created, registered.status)
        val initial = json.decodeFromString<ApiResponse<LoginResponse>>(registered.bodyAsText()).data
        val fixture = MongoAuthFixture(RefreshSessionDocumentCodec.encode(assertNotNull(backing.find(initial.sessionId))))
        persisted = fixture
        block(fixture, initial)
    }

    private fun rewriteSignedRevision(token: String, rawRevision: String, claim: String = "auth_revision"): String {
        val parts = token.split('.')
        val payload = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
            .replace(Regex("\"$claim\":0"), "\"$claim\":$rawRevision")
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val input = parts[0] + "." + encoder.encodeToString(payload.toByteArray(Charsets.UTF_8))
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return input + "." + encoder.encodeToString(mac.doFinal(input.toByteArray(Charsets.UTF_8)))
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.me(token: String) = client.get("/auth/me") {
        header(HttpHeaders.Authorization, "Bearer $token")
    }.status
    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.refresh(session: LoginResponse) = client.post("/auth/refresh") {
        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        setBody("""{"sessionId":"${session.sessionId}","refreshToken":"${session.refreshToken}"}""")
    }.status
}