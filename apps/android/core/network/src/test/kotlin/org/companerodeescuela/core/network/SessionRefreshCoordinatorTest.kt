package org.companerodeescuela.core.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.security.RefreshSessionCredentials
import org.companerodeescuela.core.security.SessionTokenStore

class SessionRefreshCoordinatorTest {
    @Test
    fun `401 refreshes once and retries the request with the rotated access token`() = runTest {
        val initial = token("session-1", "old-user", 4_102_444_800)
        val rotated = token("session-1", "new-user", 4_102_444_800, generation = 1)
        val store = FakeSessionStore(initial)
        val refreshCalls = AtomicInteger()
        val protectedTokens = mutableListOf<String?>()
        val client = createApiClient(
            ApiEnvironment("https://example.test/", "test"),
            MockEngine { request ->
                if (request.url.encodedPath.endsWith("/auth/refresh")) {
                    refreshCalls.incrementAndGet()
                    respond(refreshBody(rotated), HttpStatusCode.OK, jsonHeaders())
                } else {
                    protectedTokens += request.headers[HttpHeaders.Authorization]
                    if (request.headers[HttpHeaders.Authorization] == "Bearer $initial") {
                        respond("", HttpStatusCode.Unauthorized, jsonHeaders())
                    } else {
                        respond("", HttpStatusCode.NoContent, jsonHeaders())
                    }
                }
            },
        )
        val coordinator = SessionRefreshCoordinator(client, store)

        val result = coordinator.execute(initial) { accessToken ->
            apiCall { client.get("private") { bearerAuth(accessToken) }.requireUnit() }
        }

        assertTrue(result is Outcome.Success<*>)
        assertEquals(1, refreshCalls.get())
        assertEquals(listOf<String?>("Bearer $initial", "Bearer $rotated"), protectedTokens)
        assertEquals(rotated, store.accessToken)
        assertEquals("rotated-refresh", store.refreshSession?.refreshToken)
    }

    @Test
    fun `concurrent unauthorized requests share one refresh request`() = runTest {
        val initial = token("session-1", "student-1", 4_102_444_800)
        val rotated = token("session-1", "student-1", 4_102_444_800, generation = 1)
        val store = FakeSessionStore(initial)
        val refreshCalls = AtomicInteger()
        val client = createApiClient(
            ApiEnvironment("https://example.test/", "test"),
            MockEngine { request ->
                if (request.url.encodedPath.endsWith("/auth/refresh")) {
                    refreshCalls.incrementAndGet()
                    respond(refreshBody(rotated), HttpStatusCode.OK, jsonHeaders())
                } else if (request.headers[HttpHeaders.Authorization] == "Bearer $initial") {
                    respond("", HttpStatusCode.Unauthorized, jsonHeaders())
                } else {
                    respond("", HttpStatusCode.NoContent, jsonHeaders())
                }
            },
        )
        val coordinator = SessionRefreshCoordinator(client, store)

        val results = listOf("first", "second").map { path ->
            async {
                coordinator.execute(initial) { accessToken ->
                    apiCall { client.get(path) { bearerAuth(accessToken) }.requireUnit() }
                }
            }
        }.awaitAll()

        assertEquals(listOf(Outcome.Success(Unit), Outcome.Success(Unit)), results)
        assertEquals(1, refreshCalls.get())
    }

    @Test
    fun `retry is bounded and final 401 clears only the matching session`() = runTest {
        val initial = token("session-1", "student-1", 4_102_444_800)
        val rotated = token("session-1", "student-1", 4_102_444_800)
        val store = FakeSessionStore(initial)
        var protectedCalls = 0
        val client = createApiClient(
            ApiEnvironment("https://example.test/", "test"),
            MockEngine { request ->
                if (request.url.encodedPath.endsWith("/auth/refresh")) {
                    respond(refreshBody(rotated), HttpStatusCode.OK, jsonHeaders())
                } else {
                    protectedCalls++
                    respond("", HttpStatusCode.Unauthorized, jsonHeaders())
                }
            },
        )
        val coordinator = SessionRefreshCoordinator(client, store)

        val result = coordinator.execute(initial) { accessToken ->
            apiCall { client.get("private") { bearerAuth(accessToken) }.requireUnit() }
        }

        assertIs<Outcome.Failure>(result)
        assertEquals(2, protectedCalls)
        assertNull(store.accessToken)
        assertNull(store.refreshSession)
    }

    @Test
    fun `transient refresh network error preserves encrypted session for later retry`() = runTest {
        val initial = token("session-1", "student-1", 4_102_444_800)
        val store = FakeSessionStore(initial)
        val client = createApiClient(
            ApiEnvironment("https://example.test/", "test"),
            MockEngine { request ->
                if (request.url.encodedPath.endsWith("/auth/refresh")) {
                    throw IOException("offline")
                }
                respond("", HttpStatusCode.Unauthorized, jsonHeaders())
            },
        )
        val coordinator = SessionRefreshCoordinator(client, store)

        val result = coordinator.execute(initial) { accessToken ->
            apiCall { client.get("private") { bearerAuth(accessToken) }.requireUnit() }
        }

        assertIs<AppError.Network>(assertIs<Outcome.Failure>(result).error)
        assertEquals(initial, store.accessToken)
        assertEquals("refresh-1", store.refreshSession?.refreshToken)
    }

    private class FakeSessionStore(
        accessToken: String,
    ) : SessionTokenStore {
        var accessToken: String? = accessToken
        var refreshSession: RefreshSessionCredentials? =
            RefreshSessionCredentials("session-1", "refresh-1")

        override suspend fun readAccessToken(): String? = accessToken
        override suspend fun readRefreshSession(): RefreshSessionCredentials? = refreshSession

        override suspend fun writeAccessToken(token: String) {
            accessToken = token
        }

        override suspend fun writeSession(accessToken: String, sessionId: String, refreshToken: String) {
            this.accessToken = accessToken
            refreshSession = RefreshSessionCredentials(sessionId, refreshToken)
        }

        override suspend fun clear() {
            accessToken = null
            refreshSession = null
        }
    }

    private fun refreshBody(accessToken: String): String = """
        {
          "data": {
            "accessToken": "$accessToken",
            "expiresAtEpochSeconds": 4102444800,
            "sessionId": "session-1",
            "refreshToken": "rotated-refresh",
            "user": {
              "id": "student-1",
              "displayName": "Ana López",
              "email": null,
              "roles": ["student"],
              "active": true
            }
          }
        }
    """.trimIndent()

    private fun jsonHeaders() = headersOf(
        HttpHeaders.ContentType,
        ContentType.Application.Json.toString(),
    )

    private fun token(
        sessionId: String,
        userId: String,
        expiresAt: Long,
        generation: Long = 0,
    ): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val payload = """{"sub":"$userId","session_id":"$sessionId","session_generation":$generation,"exp":$expiresAt}"""
        return listOf(
            encoder.encodeToString("{}".toByteArray()),
            encoder.encodeToString(payload.toByteArray()),
            "test",
        ).joinToString(".")
    }
}