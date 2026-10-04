package org.companerodeescuela.feature.channel

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
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

class ChannelRepositoryTest {
    @Test
    fun `channels are loaded with the platform bearer token`() = runTest {
        val token = usableToken()
        val engine = MockEngine { request ->
            assertEquals("/channels", request.url.encodedPath)
            assertEquals("Bearer $token", request.headers[HttpHeaders.Authorization])
            respond(
                content = """
                    {
                      "data": [
                        {
                          "id": "C-9001",
                          "courseId": "C-9001",
                          "subjectCode": "MAT-101",
                          "subjectName": "Álgebra Lineal",
                          "groupName": "101-A",
                          "term": "2026-1",
                          "teacherId": "T-0001",
                          "teacherDisplayName": "Mtra. Elena Ríos Salgado",
                          "canPublish": false
                        }
                      ]
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val repository = ChannelRepository(
            client = createApiClient(
                environment = ApiEnvironment("https://example.test/", "test"),
                engine = engine,
            ),
            tokenStore = FakeTokenStore(token),
        )

        val result = repository.channels()

        val success = assertIs<Outcome.Success<*>>(result)
        val channels = success.value as List<*>
        assertEquals(1, channels.size)
    }

    @Test
    fun `expired local session fails before network access`() = runTest {
        var requests = 0
        val engine = MockEngine {
            requests += 1
            error("Network must not be called for an expired token")
        }
        val repository = ChannelRepository(
            client = createApiClient(
                environment = ApiEnvironment("https://example.test/", "test"),
                engine = engine,
            ),
            tokenStore = FakeTokenStore(expiredToken()),
        )

        val result = repository.channels()

        val failure = assertIs<Outcome.Failure>(result)
        assertEquals(401, (failure.error as org.companerodeescuela.core.common.result.AppError.Http).status)
        assertEquals(0, requests)
    }

    private fun usableToken(): String = tokenWithExpiry(4_102_444_800L)

    private fun expiredToken(): String = tokenWithExpiry(1L)

    private fun tokenWithExpiry(expiry: Long): String {
        val payload = """{"sub":"student-1","display_name":"Ana","roles":["STUDENT"],"exp":$expiry}"""
        val encoded = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(payload.toByteArray())
        return "e30.$encoded.signature"
    }

    private class FakeTokenStore(
        private var token: String?,
    ) : SessionTokenStore {
        override suspend fun readAccessToken(): String? = token

        override suspend fun writeAccessToken(token: String) {
            this.token = token
        }

        override suspend fun clear() {
            token = null
        }
    }
}
