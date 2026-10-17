package org.companerodeescuela.api.application

import com.mongodb.kotlin.client.coroutine.MongoClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.companerodeescuela.api.auth.AccountIdentityChange
import org.companerodeescuela.api.auth.AuthTokenService
import org.companerodeescuela.api.auth.MongoPlatformAccountRepository
import org.companerodeescuela.api.auth.MongoRefreshSessionRepository
import org.companerodeescuela.api.auth.PasswordHasher
import org.companerodeescuela.api.auth.PlatformAccount
import org.companerodeescuela.api.auth.RefreshSession
import org.companerodeescuela.api.auth.SessionIdentitySource
import org.companerodeescuela.api.config.Environment
import org.companerodeescuela.api.config.MongoSettings
import org.companerodeescuela.api.database.DriverMongoConnectionFactory
import org.companerodeescuela.api.test.TestFixtures
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.LoginResponse
import org.companerodeescuela.shared.contracts.RefreshSessionRequest
import org.companerodeescuela.shared.contracts.UserRole
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(named = "V12_TEST_MONGODB_URI", matches = ".+")
@Timeout(120)
class NativeOnlyProductionTest {
    @Test
    fun `default production boots real Mongo native auth and never promotes colliding institutional sessions`() =
        runBlocking {
            val uri = System.getenv("V12_TEST_MONGODB_URI")
            require(uri == "mongodb://127.0.0.1:27017")
            val name = "v12_test_native_only_" + UUID.randomUUID().toString().replace("-", "")
            val settings =
                TestFixtures.settings(Environment.PRODUCTION, uri).copy(
                    mongo = MongoSettings(uri, name),
                    jwtSecret = "synthetic-native-only-production-secret-2026".toCharArray(),
                    mfaEncryptionKey = ByteArray(32) { it.toByte() },
                )
            MongoClient.create(uri).use { mongo ->
                val db = mongo.getDatabase(name)
                try {
                    val accounts = MongoPlatformAccountRepository(db)
                    val password = "Synthetic-native-only-2026!"
                    val user =
                        PlatformAccount(
                            "native-fixture",
                            "Native Only Fixture",
                            "native-only@example.test",
                            PasswordHasher().hash(password),
                            setOf(UserRole.STUDENT),
                            institutionId = "fixture-institution",
                            emailVerifiedAt = Instant.now(),
                        )
                    assertTrue(accounts.create(user))
                    testApplication {
                        application { module(settings, DriverMongoConnectionFactory().create(settings.mongo)) }
                        assertEquals(HttpStatusCode.OK, client.get("/health").status)
                        assertEquals(HttpStatusCode.OK, client.get("/ready").status)

                        suspend fun login(
                            username: String,
                            supplied: String,
                        ) = client.post("/auth/login") {
                            contentType(ContentType.Application.Json)
                            setBody(Json.encodeToString(mapOf("username" to username, "password" to supplied)))
                        }
                        assertEquals(HttpStatusCode.Unauthorized, login("unknown@example.test", password).status)
                        assertEquals(HttpStatusCode.Unauthorized, login(user.email, "wrong-password").status)
                        val loggedIn = login(user.email, password)
                        assertEquals(HttpStatusCode.OK, loggedIn.status)
                        val session = Json.decodeFromString<ApiResponse<LoginResponse>>(loggedIn.bodyAsText()).data
                        assertEquals(user.id, session.user.id)
                        assertEquals(HttpStatusCode.OK, client.get("/auth/me") { bearerAuth(session.accessToken) }.status)
                        val load = client.get("/academic/load") { bearerAuth(session.accessToken) }
                        assertEquals(HttpStatusCode.OK, load.status)
                        assertTrue(load.bodyAsText().contains("Native Only Fixture"))
                        assertEquals(
                            HttpStatusCode.OK,
                            client.get("/academic/schedule/v2?weekOf=2026-10-17") { bearerAuth(session.accessToken) }.status,
                        )
                        val sessions = MongoRefreshSessionRepository(db)
                        val tokens = AuthTokenService(settings)
                        for (source in listOf(SessionIdentitySource.INSTITUTIONAL, SessionIdentitySource.LEGACY)) {
                            val id = UUID.randomUUID().toString()
                            sessions.create(
                                RefreshSession(
                                    id,
                                    "fixture-hash",
                                    user.toUserSummary(),
                                    Instant.now(),
                                    Instant.now().plusSeconds(3600),
                                    identitySource = source,
                                ),
                            )
                            val token = tokens.issue(user.toUserSummary(), id).value
                            assertEquals(HttpStatusCode.Unauthorized, client.get("/auth/me") { bearerAuth(token) }.status)
                            assertEquals(HttpStatusCode.Unauthorized, client.get("/academic/load") { bearerAuth(token) }.status)
                        }
                        val rotated =
                            client.post("/auth/refresh") {
                                contentType(ContentType.Application.Json)
                                setBody(Json.encodeToString(RefreshSessionRequest(session.sessionId, session.refreshToken)))
                            }
                        assertEquals(HttpStatusCode.OK, rotated.status)
                        val current = Json.decodeFromString<ApiResponse<LoginResponse>>(rotated.bodyAsText()).data
                        assertEquals(
                            HttpStatusCode.NoContent,
                            client
                                .post("/auth/logout") {
                                    contentType(ContentType.Application.Json)
                                    setBody(Json.encodeToString(RefreshSessionRequest(current.sessionId, current.refreshToken)))
                                }.status,
                        )
                        assertEquals(HttpStatusCode.Unauthorized, client.get("/auth/me") { bearerAuth(current.accessToken) }.status)
                        assertEquals(
                            HttpStatusCode.Unauthorized,
                            client
                                .post("/auth/refresh") {
                                    contentType(ContentType.Application.Json)
                                    setBody(Json.encodeToString(RefreshSessionRequest(current.sessionId, current.refreshToken)))
                                }.status,
                        )
                        val freshLogin = login(user.email, password)
                        assertEquals(HttpStatusCode.OK, freshLogin.status)
                        val beforeSuspension = Json.decodeFromString<ApiResponse<LoginResponse>>(freshLogin.bodyAsText()).data
                        assertNotNull(
                            accounts.changeIdentity(
                                user.id,
                                0,
                                AccountIdentityChange(
                                    "fixture-suspend",
                                    "fixture-admin",
                                    AccountStatus.SUSPENDED,
                                    user.roles,
                                    user.institutionId,
                                    Instant.now(),
                                ),
                            ),
                        )
                        assertEquals(
                            HttpStatusCode.Unauthorized,
                            client.get("/auth/me") { bearerAuth(beforeSuspension.accessToken) }.status,
                        )
                        assertEquals(
                            HttpStatusCode.Unauthorized,
                            client
                                .post("/auth/refresh") {
                                    contentType(ContentType.Application.Json)
                                    setBody(
                                        Json.encodeToString(
                                            RefreshSessionRequest(beforeSuspension.sessionId, beforeSuspension.refreshToken),
                                        ),
                                    )
                                }.status,
                        )
                    }
                } finally {
                    db.drop()
                }
            }
        }
}
