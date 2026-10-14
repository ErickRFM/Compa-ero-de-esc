package org.companerodeescuela.feature.grading

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.network.ApiEnvironment
import org.companerodeescuela.core.network.createApiClient
import org.companerodeescuela.core.security.SessionTokenStore
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

class StudentGradebookRepositoryTest {
    @Test
    fun `student reads published grades through the authenticated class and period endpoint`() =
        runTest {
            var requestedPath: String? = null
            var requestedPeriod: String? = null
            var authorization: String? = null
            val client =
                createApiClient(
                    ApiEnvironment("https://example.test/", "test"),
                    MockEngine(
                        MockEngineConfig().apply {
                            addHandler { request ->
                                requestedPath = request.url.encodedPath
                                requestedPeriod = request.url.parameters["gradingPeriod"]
                                authorization = request.headers[HttpHeaders.Authorization]
                                respond(
                                    """{"data":null}""",
                                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                )
                            }
                        },
                    ),
                )
            val payload =
                Base64
                    .getUrlEncoder()
                    .withoutPadding()
                    .encodeToString("""{"sub":"student-1","roles":["STUDENT"],"exp":4102444800}""".toByteArray())
            val token = "e30.$payload.signature"
            val tokens =
                object : SessionTokenStore {
                    override suspend fun readAccessToken() = token

                    override suspend fun writeAccessToken(token: String) = Unit

                    override suspend fun clear() = Unit
                }
            try {
                val result = GradebookRepository(client, tokens).studentView("class-A", "Parcial 1")
                assertEquals(Outcome.Success(null), result)
                assertEquals("/grading/students/class-A", requestedPath)
                assertEquals("Parcial 1", requestedPeriod)
                assertEquals("Bearer $token", authorization)
            } finally {
                client.close()
            }
        }
}
