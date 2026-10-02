package org.companerodeescuela.feature.auth

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.network.ApiEnvironment
import org.companerodeescuela.core.network.createApiClient
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
    }

    private fun platformToken(userId: String, expiresAt: Long): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val payload = """{"sub":"$userId","display_name":"Ana López","exp":$expiresAt}"""
        return listOf(
            encoder.encodeToString("{}".toByteArray()),
            encoder.encodeToString(payload.toByteArray()),
            "test",
        ).joinToString(".")
    }

    private class FakeTokenStore : SessionTokenStore {
        var token: String? = null

        override suspend fun readAccessToken(): String? = token

        override suspend fun writeAccessToken(token: String) {
            this.token = token
        }

        override suspend fun clear() {
            token = null
        }
    }
}
