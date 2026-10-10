package org.companerodeescuela.feature.auth

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.network.ApiEnvironment
import org.companerodeescuela.core.network.createApiClient
import org.companerodeescuela.core.security.RefreshSessionCredentials
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.UserSummary

class AuthRepositoryTest {

    @Test
    fun `successful login persists a usable platform access token`() = runTest {
        val validToken = platformToken("student-1", 4_102_444_800)
        val engine = MockEngine {
            respond(
                content = """
                    {
                      "data": {
                        "accessToken": "$validToken",
                        "expiresAtEpochSeconds": 4102444800,
                          "sessionId": "session-1",
                          "refreshToken": "refresh-session-token",
                        "user": {
                          "id": "student-1",
                          "displayName": "Ana López",
                          "email": "ana@example.edu",
                          "roles": ["student"],
                          "active": true
                        }
                      }
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val tokenStore = FakeTokenStore()
        val repository = AuthRepository(
            client = createApiClient(
                environment = ApiEnvironment("https://example.test/", "test"),
                engine = engine,
            ),
            tokenStore = tokenStore,
        )

        val result = repository.login("ana", "secret")

        val success = assertIs<Outcome.Success<UserSummary>>(result)
        assertEquals("Ana López", success.value.displayName)
        assertEquals(validToken, tokenStore.token)
        assertEquals("session-1", tokenStore.refreshSession?.sessionId)
        assertEquals("refresh-session-token", tokenStore.refreshSession?.refreshToken)
    }

    @Test
    fun `secure storage failure is classified instead of becoming unknown`() = runTest {
        val validToken = platformToken("student-1", 4_102_444_800)
        val engine = MockEngine {
            respond(
                content = """
                    {
                      "data": {
                        "accessToken": "$validToken",
                        "expiresAtEpochSeconds": 4102444800,
                          "sessionId": "session-1",
                          "refreshToken": "refresh-session-token",
                        "user": {
                          "id": "student-1",
                          "displayName": "Ana López",
                          "email": "ana@example.edu",
                          "roles": ["student"],
                          "active": true
                        }
                      }
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val repository = AuthRepository(
            client = createApiClient(
                environment = ApiEnvironment("https://example.test/", "test"),
                engine = engine,
            ),
            tokenStore = FakeTokenStore(failOnWrite = true),
        )

        val result = repository.login("ana", "secret")

        val failure = assertIs<Outcome.Failure>(result)
        assertIs<AppError.Storage>(failure.error)
        assertEquals(
            "No pudimos guardar tu sesión de forma segura. Inténtalo nuevamente.",
            failure.error.userMessage,
        )
    }

    @Test
    fun `startup restores expired access token using the stored refresh session`() = runTest {
        val currentToken = platformToken("student-1", 1)
        val nextToken = platformToken("student-1", 4_102_444_800)
        val tokenStore = FakeTokenStore(
            initialAccessToken = currentToken,
            initialRefreshSession = RefreshSessionCredentials("session-1", "refresh-before"),
        )
        var refreshCount = 0
        val client = createApiClient(
            ApiEnvironment("https://example.test/", "test"),
            MockEngine { request ->
                assertEquals("/auth/refresh", request.url.encodedPath)
                refreshCount++
                respond(
                    content = loginResponse(nextToken, "refresh-after"),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            },
        )
        val repository = AuthRepository(client, tokenStore)

        assertTrue(repository.hasSession())
        assertEquals(1, refreshCount)
        assertEquals(nextToken, tokenStore.token)
        assertEquals("refresh-after", tokenStore.refreshSession?.refreshToken)
    }

    @Test
    fun `logout revokes remotely and clears local session`() = runTest {
        val tokenStore = FakeTokenStore(
            initialAccessToken = "access-token",
            initialRefreshSession = RefreshSessionCredentials("session-1", "refresh-before"),
        )
        var logoutPath: String? = null
        val client = createApiClient(
            ApiEnvironment("https://example.test/", "test"),
            MockEngine { request ->
                logoutPath = request.url.encodedPath
                respond("", HttpStatusCode.NoContent)
            },
        )
        val repository = AuthRepository(client, tokenStore)

        repository.logout()

        assertEquals("/auth/logout", logoutPath)
        assertNull(tokenStore.token)
        assertNull(tokenStore.refreshSession)
    }

    @Test
    fun `logout clears locally if revocation request fails offline`() = runTest {
        val tokenStore = FakeTokenStore(
            initialAccessToken = "access-token",
            initialRefreshSession = RefreshSessionCredentials("session-1", "refresh-before"),
        )
        val client = createApiClient(
            ApiEnvironment("https://example.test/", "test"),
            MockEngine { throw IOException("offline") },
        )
        val repository = AuthRepository(client, tokenStore)

        repository.logout()

        assertNull(tokenStore.token)
        assertNull(tokenStore.refreshSession)
    }

    @Test
    fun `cancelledSecureStorageDoesNotBecomeAStorageFailure`() = runTest {
        val store = object : SessionTokenStore by FakeTokenStore() {
            override suspend fun writeSession(accessToken: String, sessionId: String, refreshToken: String) {
                throw kotlinx.coroutines.CancellationException("cancelled")
            }
        }
        val client = createApiClient(ApiEnvironment("https://example.test/", "test"), MockEngine {
            respond(loginResponse(platformToken("student-1", 4_102_444_800), "fixture-refresh"),
                HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        })
        client.use {
            kotlin.test.assertFailsWith<kotlinx.coroutines.CancellationException> {
                AuthRepository(client, store).login("student", "fixture")
            }
        }
    }

    @Test fun `universal registration sends preferences without requested privileges`() = runTest {
        var requestBody = ""
        val store = FakeTokenStore()
        val access = platformToken("student-1", 4102444800)
        val repo = AuthRepository(createApiClient(ApiEnvironment("https://example.test/", "test"), MockEngine { request ->
            requestBody = (request.body as io.ktor.http.content.TextContent).text
            respond(loginResponse(access, "refresh"), HttpStatusCode.Created, headersOf(HttpHeaders.ContentType, "application/json"))
        }), store)
        val result = repo.register(org.companerodeescuela.shared.contracts.RegisterRequest("QA", "qa@example.test", "test-password",
            org.companerodeescuela.shared.contracts.RegistrationAccountType.TUTOR, "campus", "reference", "preference"))
        assertIs<Outcome.Success<UserSummary>>(result)
        assertTrue(requestBody.contains("requestedInstitutionId"))
        assertTrue(requestBody.contains("preference"))
        assertTrue(!requestBody.contains("roles"))
        assertEquals(access, store.token)
    }

    @Test fun `confirmation persists fresh credentials and failed confirmation preserves prior session`() = runTest {
        val old = platformToken("student-1", 4102444800, "old-session")
        val fresh = platformToken("student-1", 4102444800, "new-session")
        val store = FakeTokenStore(initialAccessToken = old, initialRefreshSession = RefreshSessionCredentials("old-session", "old-refresh"))
        var reject = true
        val repo = AuthRepository(createApiClient(ApiEnvironment("https://example.test/", "test"), MockEngine { request ->
            assertEquals("/auth/verification/confirm", request.url.encodedPath)
            assertEquals("Bearer $old", request.headers[HttpHeaders.Authorization])
            if (reject) respond("""{"code":"VALIDATION_ERROR","message":"Invalid code"}""", HttpStatusCode.BadRequest, headersOf(HttpHeaders.ContentType, "application/json"))
            else respond(loginResponse(fresh, "new-refresh"), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }), store)
        assertIs<AppError.Http>(assertIs<Outcome.Failure>(repo.confirmVerification("A".repeat(43))).error)
        assertEquals(old, store.token)
        reject = false
        assertIs<Outcome.Success<UserSummary>>(repo.confirmVerification("B".repeat(43)))
        assertEquals(fresh, store.token)
        assertEquals("new-refresh", store.refreshSession?.refreshToken)
    }

    @Test fun `institution directory is public and verification resend uses current native session`() = runTest {
        val access = platformToken("student-1", 4102444800)
        val repo = AuthRepository(createApiClient(ApiEnvironment("https://example.test/", "test"), MockEngine { request ->
            val content = when (request.url.encodedPath) {
                "/institutions" -> {
                    assertNull(request.headers[HttpHeaders.Authorization])
                    """{"data":[{"id":"campus","displayName":"Actual campus"}]}"""
                }
                "/auth/verification/resend" -> {
                    assertEquals("Bearer $access", request.headers[HttpHeaders.Authorization])
                    """{"data":{"status":"UNAVAILABLE","resendAfterSeconds":60}}"""
                }
                else -> error("Unexpected endpoint")
            }
            respond(content, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }), FakeTokenStore(initialAccessToken = access))
        assertEquals("campus", assertIs<Outcome.Success<List<org.companerodeescuela.shared.contracts.InstitutionSummary>>>(repo.institutions()).value.single().id)
        assertEquals(org.companerodeescuela.shared.contracts.VerificationDeliveryStatus.UNAVAILABLE,
            assertIs<Outcome.Success<org.companerodeescuela.shared.contracts.VerificationDeliveryReceipt>>(repo.resendVerification()).value.status)
    }

    private fun platformToken(
        userId: String,
        expiresAt: Long,
        sessionId: String = "session-1",
    ): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val payload = """{"sub":"$userId","display_name":"Ana López","session_id":"$sessionId","exp":$expiresAt}"""
        return listOf(
            encoder.encodeToString("{}".toByteArray()),
            encoder.encodeToString(payload.toByteArray()),
            "test",
        ).joinToString(".")
    }

        private fun loginResponse(token: String, refreshToken: String): String = """
                {
                    "data": {
                        "accessToken": "$token",
                        "expiresAtEpochSeconds": 4102444800,
                        "sessionId": "session-1",
                        "refreshToken": "$refreshToken",
                        "user": {
                            "id": "student-1",
                            "displayName": "Ana López",
                            "email": "ana@example.edu",
                            "roles": ["student"],
                            "active": true
                        }
                    }
                }
        """.trimIndent()

    private class FakeTokenStore(
        private val failOnWrite: Boolean = false,
        initialAccessToken: String? = null,
        initialRefreshSession: RefreshSessionCredentials? = null,
    ) : SessionTokenStore {
        var token: String? = initialAccessToken
        var refreshSession: RefreshSessionCredentials? = initialRefreshSession

        override suspend fun readAccessToken(): String? = token

        override suspend fun readRefreshSession() = refreshSession

        override suspend fun writeAccessToken(token: String) {
            if (failOnWrite) error("simulated secure storage failure")
            this.token = token
        }

        override suspend fun writeSession(
            accessToken: String,
            sessionId: String,
            refreshToken: String,
        ) {
            writeAccessToken(accessToken)
            refreshSession = RefreshSessionCredentials(sessionId, refreshToken)
        }

        override suspend fun clear() {
            token = null
            refreshSession = null
        }
    }
}
