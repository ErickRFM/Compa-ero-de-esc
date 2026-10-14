package org.companerodeescuela.api.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.get
import io.ktor.client.request.options
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import java.time.Instant
import java.util.Date
import org.companerodeescuela.api.auth.AuthTokenService
import org.companerodeescuela.api.auth.InMemoryPlatformAccountRepository
import org.companerodeescuela.api.auth.InMemoryRefreshSessionRepository
import org.companerodeescuela.api.auth.PlatformSessionAuthority
import org.companerodeescuela.api.config.Environment
import org.companerodeescuela.api.test.TestFixtures
import org.companerodeescuela.api.integrations.mock.MockIdentityProvider

class PluginsTest {
    @Test
    fun `invalid request id is replaced with a generated id`() = testApplication {
        application {
            configurePlugins(TestFixtures.settings())
            routing { get("/probe") { call.respondText(call.requestId().orEmpty()) } }
        }

        val response = client.get("/probe") { header(REQUEST_ID_HEADER, "bad request id!") }

        assertEquals(HttpStatusCode.OK, response.status)
        val requestId = response.headers[REQUEST_ID_HEADER]
        assertTrue(!requestId.isNullOrBlank())
        assertNotEquals("bad request id!", requestId)
        assertTrue(requestId!!.matches(Regex("[A-Za-z0-9._-]{1,64}")))
    }

    @Test
    fun `staging does not allow localhost CORS origin`() = testApplication {
        application { configurePlugins(TestFixtures.settings(environment = Environment.STAGING)) }

        val response = client.options("/probe") {
            header(HttpHeaders.Origin, "http://localhost")
            header(HttpHeaders.AccessControlRequestMethod, "GET")
        }

        assertEquals(null, response.headers[HttpHeaders.AccessControlAllowOrigin])
    }

    @Test
    fun `malformed signed identity is rejected instead of reported as an authority outage`() = testApplication {
        val secret = "0123456789abcdef0123456789abcdef".toCharArray()
        val settings = TestFixtures.settings().copy(jwtSecret = secret)
        val token = JWT.create()
            .withIssuer(settings.jwtIssuer)
            .withAudience(settings.jwtAudience)
            .withClaim("session_id", "missing-session")
            .withExpiresAt(Date.from(Instant.now().plusSeconds(60)))
            .sign(Algorithm.HMAC256(secret.concatToString()))
        val sessions = InMemoryRefreshSessionRepository()
        val identity = MockIdentityProvider()

        application {
            configurePlugins(settings, sessions, PlatformSessionAuthority(InMemoryPlatformAccountRepository(), identity))
            routing {
                authenticate(AuthTokenService.PROVIDER_NAME) {
                    get("/protected") { call.respondText("ok") }
                }
            }
        }

        val response = client.get("/protected") { header(HttpHeaders.Authorization, "Bearer $token") }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
