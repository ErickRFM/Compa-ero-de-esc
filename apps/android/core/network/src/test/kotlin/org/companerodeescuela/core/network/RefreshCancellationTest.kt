package org.companerodeescuela.core.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.core.security.RefreshSessionCredentials
import org.companerodeescuela.core.security.SessionTokenStore

class RefreshCancellationTest {
    @Test fun cancelledRefreshStorageDoesNotBecomeAFailureAndContinueTheSession() = runTest {
        fun token(expiry: Long): String {
            val encoder = Base64.getUrlEncoder().withoutPadding()
            val payload = """{"sub":"user-1","session_id":"session-1","exp":""" + expiry + "}"
            return encoder.encodeToString("{}".toByteArray()) + "." + encoder.encodeToString(payload.toByteArray()) + ".test"
        }
        var attemptedWrite = false
        val store = object : SessionTokenStore {
            override suspend fun readAccessToken() = token(1)
            override suspend fun readRefreshSession() = RefreshSessionCredentials("session-1", "fixture-refresh")
            override suspend fun writeAccessToken(token: String) = Unit
            override suspend fun writeSession(accessToken: String, sessionId: String, refreshToken: String) {
                attemptedWrite = true
                throw CancellationException("cancelled")
            }
            override suspend fun clear() = Unit
        }
        val client = createApiClient(ApiEnvironment("https://example.test/", "test"), MockEngine {
            val body = """{"data":{"accessToken":"""" + token(4102444800) + """","expiresAtEpochSeconds":4102444800,"sessionId":"session-1","refreshToken":"fixture-next","user":{"id":"user-1","displayName":"User","email":"u@example.edu","roles":["student"],"active":true}}}"""
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        })
        client.use {
            val result = runCatching { SessionRefreshCoordinator(client, store).refreshSession() }
            kotlin.test.assertTrue(attemptedWrite, "The refresh payload must be validated before persistence")
            kotlin.test.assertIs<CancellationException>(result.exceptionOrNull())
        }
    }
}
