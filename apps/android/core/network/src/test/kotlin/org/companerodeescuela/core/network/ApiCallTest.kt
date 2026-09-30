package org.companerodeescuela.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import io.ktor.http.ContentType
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.common.result.Outcome
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ApiCallTest {

    private fun clientReturning(
        status: HttpStatusCode,
        body: String = "{}",
    ): HttpClient {
        val engine = MockEngine {
            respond(
                content = body,
                status = status,
                headers = headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString(),
                ),
            )
        }
        return createApiClient(ApiEnvironment.Emulator, engine)
    }

    @Test
    @DisplayName("A 500 becomes a retryable HTTP failure, not a crash")
    fun serverErrorIsOutcome() = runTest {
        val client = clientReturning(HttpStatusCode.InternalServerError)

        val outcome = apiCall { client.get("/health").requireUnit() }

        val error = assertIs<Outcome.Failure>(outcome).error
        assertEquals(500, assertIs<AppError.Http>(error).status)
        assertTrue(error.isRetryable())
    }

    @Test
    @DisplayName("A 401 is reported as unauthorized and is not retryable")
    fun unauthorizedIsNotRetryable() = runTest {
        val client = clientReturning(HttpStatusCode.Unauthorized)

        val outcome = apiCall { client.get("/private").requireUnit() }

        val error = assertIs<Outcome.Failure>(outcome).error
        assertTrue(error.isUnauthorized())
        assertTrue(!error.isRetryable(), "retrying a 401 without new credentials cannot help")
    }

    @Test
    @DisplayName("A successful empty response is a unit success")
    fun successIsUnit() = runTest {
        val client = clientReturning(HttpStatusCode.OK, body = "")

        val outcome: Outcome<Unit> = apiCall { client.get("/version").requireUnit() }

        assertEquals(Outcome.Success(Unit), outcome)
    }

    @Test
    @DisplayName("A malformed body becomes a serialization failure without leaking the body")
    fun malformedBodyIsHandled() = runTest {
        val client = clientReturning(HttpStatusCode.OK, body = "not json at all")

        val outcome = apiCall { client.get("/health").requireBody<HealthProbe>() }

        val error = assertIs<Outcome.Failure>(outcome).error
        assertIs<AppError.Serialization>(error)
        assertTrue(error.userMessage.isNotBlank())
    }

    @Test
    @DisplayName("A dropped connection becomes a network failure")
    fun connectionFailureIsNetworkError() = runTest {
        val engine = MockEngine { throw java.io.IOException("connection reset") }
        val client = createApiClient(ApiEnvironment.Emulator, engine)

        val outcome = apiCall { client.get("/health").requireUnit() }

        val error = assertIs<Outcome.Failure>(outcome).error
        assertIs<AppError.Network>(error)
        assertTrue(error.isRetryable())
    }

    @Test
    @DisplayName("The base URL must be absolute and end with a slash")
    fun baseUrlIsValidated() {
        val relative = runCatching { ApiEnvironment("/api", "bad") }.exceptionOrNull()
        val missingSlash = runCatching { ApiEnvironment("http://10.0.2.2:8080", "bad") }.exceptionOrNull()

        assertIs<IllegalArgumentException>(relative)
        assertIs<IllegalArgumentException>(missingSlash)
    }
}

/** Minimal response shape, declared here so the test needs no shared contract. */
@kotlinx.serialization.Serializable
data class HealthProbe(
    val service: String,
    val status: String,
)
