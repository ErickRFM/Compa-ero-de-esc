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
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.companerodeescuela.api.integrations.identity.IdentityProvider
import org.companerodeescuela.api.integrations.mock.MockIdentityProvider
import org.companerodeescuela.api.plugins.configurePlugins
import org.companerodeescuela.api.test.TestFixtures
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.LoginRequest
import org.companerodeescuela.shared.contracts.LoginResponse
import org.companerodeescuela.shared.contracts.UserRole
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

class LiveSessionAuthorityRoutesTest {
    private val settings = TestFixtures.settings().copy(jwtSecret = "0123456789abcdef0123456789abcdef".toCharArray())
    private val json = Json

    @Test
    fun `signed claims cannot elevate role cross owner omit generation or omit active state`() = testApplication {
        val sessions = InMemoryRefreshSessionRepository()
        val accounts = InMemoryPlatformAccountRepository()
        val provider = MockIdentityProvider()
        application {
            configurePlugins(settings, refreshSessions = sessions, sessionAuthority = PlatformSessionAuthority(accounts, provider))
            routing { authRoutes(settings, provider, sessions = sessions, accounts = accounts) }
        }
        val initial = login()
        for (case in listOf("role", "owner", "missing-generation", "missing-active", "inactive")) {
            val builder = JWT.create().withIssuer(settings.jwtIssuer).withAudience(settings.jwtAudience)
                .withSubject(if (case == "owner") "other-owner" else initial.user.id)
                .withIssuedAt(Date()).withExpiresAt(Date(System.currentTimeMillis() + 60_000))
                .withClaim("session_id", initial.sessionId).withClaim("display_name", initial.user.displayName)
                .withClaim("roles", if (case == "role") listOf(UserRole.ADMIN.name) else initial.user.roles.map { it.name })
            if (case != "missing-generation") builder.withClaim("session_generation", 0L)
            if (case != "missing-active") builder.withClaim("active", case != "inactive")
            assertEquals(HttpStatusCode.Unauthorized,
                me(builder.sign(Algorithm.HMAC256(settings.jwtSecret!!.concatToString()))), case)
        }
    }

    @Test
    fun `session repository outage fails closed as dependency unavailable`() = testApplication {
        val backing = InMemoryRefreshSessionRepository()
        var unavailable = false
        val sessions = object : RefreshSessionRepository by backing {
            override suspend fun find(sessionId: String): RefreshSession? {
                if (unavailable) throw IllegalStateException("qa-private-identity")
                return backing.find(sessionId)
            }
        }
        val accounts = InMemoryPlatformAccountRepository()
        val provider = MockIdentityProvider()
        application {
            configurePlugins(settings, refreshSessions = sessions, sessionAuthority = PlatformSessionAuthority(accounts, provider))
            routing { authRoutes(settings, provider, sessions = sessions, accounts = accounts) }
        }
        val initial = login()
        unavailable = true
        assertEquals(HttpStatusCode.ServiceUnavailable, me(initial.accessToken))
        assertEquals(HttpStatusCode.ServiceUnavailable, refresh(initial).status)
    }

    @Test
    fun `signed token without expiration cannot authenticate`() = testApplication {
        val sessions = InMemoryRefreshSessionRepository()
        val accounts = InMemoryPlatformAccountRepository()
        val provider = MockIdentityProvider()
        application {
            configurePlugins(settings, refreshSessions = sessions, sessionAuthority = PlatformSessionAuthority(accounts, provider))
            routing { authRoutes(settings, provider, sessions = sessions, accounts = accounts) }
        }
        val initial = login()
        val missingExpiry = JWT.create().withIssuer(settings.jwtIssuer).withAudience(settings.jwtAudience)
            .withSubject(initial.user.id).withIssuedAt(Date())
            .withClaim("session_id", initial.sessionId).withClaim("session_generation", 0L)
            .withClaim("display_name", initial.user.displayName).withClaim("roles", initial.user.roles.map { it.name })
            .withClaim("active", true).sign(Algorithm.HMAC256(settings.jwtSecret!!.concatToString()))
        assertEquals(HttpStatusCode.Unauthorized, me(missingExpiry))
    }

    @Test
    fun `institutional deactivation denies current access and refresh`() = testApplication {
        val sessions = InMemoryRefreshSessionRepository()
        val accounts = InMemoryPlatformAccountRepository()
        var active = true
        val provider = object : IdentityProvider by MockIdentityProvider() {
            override suspend fun currentState(externalId: String) =
                org.companerodeescuela.api.integrations.identity.InstitutionalIdentityState(externalId, active, setOf(UserRole.STUDENT))
        }
        application {
            configurePlugins(settings, refreshSessions = sessions, sessionAuthority = PlatformSessionAuthority(accounts, provider))
            routing { authRoutes(settings, provider, sessions = sessions, accounts = accounts) }
        }
        val initial = login()
        assertEquals(HttpStatusCode.OK, me(initial.accessToken))
        active = false
        assertEquals(HttpStatusCode.Unauthorized, me(initial.accessToken))
        assertEquals(HttpStatusCode.Unauthorized, refresh(initial).status)
    }

    @Test
    fun `rotation denies prior access token and accepts current generation`() = testApplication {
        val sessions = InMemoryRefreshSessionRepository()
        val accounts = InMemoryPlatformAccountRepository()
        val provider = MockIdentityProvider()
        application {
            configurePlugins(settings, refreshSessions = sessions, sessionAuthority = PlatformSessionAuthority(accounts, provider))
            routing { authRoutes(settings, provider, sessions = sessions, accounts = accounts) }
        }
        val initial = login()
        assertEquals(HttpStatusCode.OK, me(initial.accessToken))
        val refreshed = refresh(initial)
        assertEquals(HttpStatusCode.OK, refreshed.status)
        val current = json.decodeFromString<ApiResponse<LoginResponse>>(refreshed.bodyAsText()).data
        assertEquals(HttpStatusCode.Unauthorized, me(initial.accessToken))
        assertEquals(HttpStatusCode.OK, me(current.accessToken))
    }

    @Test
    fun `native deactivation denies existing access and refresh immediately`() = testApplication {
        val sessions = InMemoryRefreshSessionRepository()
        val accounts = MutableAccounts()
        val provider = MockIdentityProvider()
        application {
            configurePlugins(settings, refreshSessions = sessions, sessionAuthority = PlatformSessionAuthority(accounts, provider))
            routing { authRoutes(settings, provider, sessions = sessions, accounts = accounts) }
        }
        val initial = register()
        assertEquals(HttpStatusCode.OK, me(initial.accessToken))
        accounts.account = assertNotNull(accounts.account).copy(active = false)
        assertEquals(HttpStatusCode.Unauthorized, me(initial.accessToken))
        assertEquals(HttpStatusCode.Unauthorized, refresh(initial).status)
    }

    @Test
    fun `role removal denies old permissions and refresh publishes only current roles`() = testApplication {
        val sessions = InMemoryRefreshSessionRepository()
        val accounts = MutableAccounts()
        val provider = MockIdentityProvider()
        application {
            configurePlugins(settings, refreshSessions = sessions, sessionAuthority = PlatformSessionAuthority(accounts, provider))
            routing { authRoutes(settings, provider, sessions = sessions, accounts = accounts) }
        }
        register()
        accounts.account = assertNotNull(accounts.account).copy(roles = setOf(UserRole.ADMIN))
        val privileged = login("native@example.test", "test-password")
        assertEquals(HttpStatusCode.OK, me(privileged.accessToken))
        accounts.account = assertNotNull(accounts.account).copy(roles = setOf(UserRole.STUDENT))
        assertEquals(HttpStatusCode.Unauthorized, me(privileged.accessToken))
        val refreshed = refresh(privileged)
        assertEquals(HttpStatusCode.OK, refreshed.status)
        val current = json.decodeFromString<ApiResponse<LoginResponse>>(refreshed.bodyAsText()).data
        assertEquals(setOf(UserRole.STUDENT), current.user.roles)
        assertEquals(HttpStatusCode.OK, me(current.accessToken))
    }

    @Test
    fun `deleted native identity cannot refresh through institutional fallback`() = testApplication {
        val sessions = InMemoryRefreshSessionRepository()
        val accounts = MutableAccounts()
        val provider = object : IdentityProvider by MockIdentityProvider() {
            override suspend fun refreshRoles(externalId: String) = setOf(UserRole.SUPER_ADMIN)
            override suspend fun currentState(externalId: String) =
                org.companerodeescuela.api.integrations.identity.InstitutionalIdentityState(externalId, true, setOf(UserRole.SUPER_ADMIN))
        }
        application {
            configurePlugins(settings, refreshSessions = sessions, sessionAuthority = PlatformSessionAuthority(accounts, provider))
            routing { authRoutes(settings, provider, sessions = sessions, accounts = accounts) }
        }
        val initial = register()
        accounts.account = null
        assertEquals(HttpStatusCode.Unauthorized, refresh(initial).status)
    }

    @Test
    fun `persisted session without identity source requires fresh login`() = testApplication {
        val issuerSessions = InMemoryRefreshSessionRepository()
        val provider = MockIdentityProvider()
        val initial = AuthService(provider, AuthTokenService(settings), issuerSessions)
            .login(LoginRequest(MockIdentityProvider.MOCK_USERNAME, MockIdentityProvider.MOCK_PASSWORD))
        val document = RefreshSessionDocumentCodec.encode(assertNotNull(issuerSessions.find(initial.sessionId)))
        document.remove("identitySource")
        val sessions = InMemoryRefreshSessionRepository()
        sessions.create(RefreshSessionDocumentCodec.decode(document))
        val accounts = InMemoryPlatformAccountRepository()
        application {
            configurePlugins(settings, refreshSessions = sessions, sessionAuthority = PlatformSessionAuthority(accounts, provider))
            routing { authRoutes(settings, provider, sessions = sessions, accounts = accounts) }
        }
        assertEquals(HttpStatusCode.Unauthorized, me(initial.accessToken))
        assertEquals(HttpStatusCode.Unauthorized, refresh(initial).status)
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.login(
        username: String = MockIdentityProvider.MOCK_USERNAME,
        password: String = MockIdentityProvider.MOCK_PASSWORD,
    ): LoginResponse {
        val response = client.post("/auth/login") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"username":"$username","password":"$password"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return json.decodeFromString<ApiResponse<LoginResponse>>(response.bodyAsText()).data
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.register(): LoginResponse {
        val response = client.post("/auth/register") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"displayName":"QA Native","email":"native@example.test","password":"test-password","accountType":"STUDENT"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return json.decodeFromString<ApiResponse<LoginResponse>>(response.bodyAsText()).data
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.me(token: String) = client.get("/auth/me") {
        header(HttpHeaders.Authorization, "Bearer $token")
    }.status

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.refresh(session: LoginResponse) = client.post("/auth/refresh") {
        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        setBody("""{"sessionId":"${session.sessionId}","refreshToken":"${session.refreshToken}"}""")
    }

    private class MutableAccounts : PlatformAccountRepository {
        var account: PlatformAccount? = null
        override suspend fun findByIdentifier(identifier: String) = account?.takeIf { it.email == identifier || it.id == identifier }
        override suspend fun findById(id: String) = account?.takeIf { it.id == id }
        override suspend fun create(account: PlatformAccount): Boolean {
            if (this.account != null) return false
            this.account = account
            return true
        }
    }
}
