package org.companerodeescuela.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.common.result.Outcome
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.Arguments.arguments

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

    // Every status the API can return is pinned here on purpose. The set was
    // chosen from the error contract in shared/contracts plus the statuses a
    // real deployment can produce, so a change to requireBody or requireUnit
    // that silently reclassifies one of them fails a test instead of reaching a
    // screen as a wrong message.
    @ParameterizedTest(name = "{0} is an HTTP failure with the right retry policy")
    @DisplayName("Every non-success status is mapped to the correct AppError")
    @MethodSource("nonSuccessStatuses")
    fun everyNonSuccessStatusIsMapped(
        status: HttpStatusCode,
        expectedStatus: Int,
        expectedRetryable: Boolean,
    ) = runTest {
        val client = clientReturning(status)

        val outcome = apiCall { client.get("/thing").requireUnit() }

        val error = assertIs<Outcome.Failure>(outcome).error
        val http = assertIs<AppError.Http>(error)
        assertEquals(expectedStatus, http.status, "status must be preserved for $status")
        assertEquals(expectedRetryable, http.isRetryable(), "retry policy for $status")
        assertTrue(http.userMessage.isNotBlank(), "every status needs a presentable message")
    }

    /** A 200 with a body must survive deserialisation, not just the status. */
    @Test
    @DisplayName("A 200 with a valid JSON body is deserialised into the contract type")
    fun successBodyIsDeserialised() = runTest {
        val client = clientReturning(
            HttpStatusCode.OK,
            body = """{"service":"companero-api","status":"up"}""",
        )

        val outcome = apiCall { client.get("/health").requireBody<HealthProbe>() }

        val probe = assertIs<Outcome.Success<HealthProbe>>(outcome).value
        assertEquals("companero-api", probe.service)
        assertEquals("up", probe.status)
    }

    /**
     * 204 is the status a logout or a PATCH is expected to return. It has no
     * body, so requireBody must not be the answer for it, and requireUnit must
     * treat it as success rather than as a failure.
     */
    @Test
    @DisplayName("A 204 with no body is a unit success, not a serialization failure")
    fun noContentIsUnitSuccess() = runTest {
        val client = clientReturning(HttpStatusCode.NoContent, body = "")

        val outcome: Outcome<Unit> = apiCall { client.get("/logout").requireUnit() }

        assertEquals(Outcome.Success(Unit), outcome)
    }

    /** A 200 whose body is valid JSON but the wrong shape is still a contract break. */
    @Test
    @DisplayName("A well-formed JSON body of the wrong shape is a serialization failure")
    fun wrongShapeIsSerializationFailure() = runTest {
        val client = clientReturning(
            HttpStatusCode.OK,
            body = """{"unexpected":"shape"}""",
        )

        val outcome = apiCall { client.get("/health").requireBody<HealthProbe>() }

        assertIs<AppError.Serialization>(assertIs<Outcome.Failure>(outcome).error)
    }

    /**
     * An endpoint that returns no payload must not drag the error body along
     * with it.
     *
     * This used to be named "is truncated" and asserted nothing about
     * truncation. `requireUnit` never reads the body at all, so there is nothing
     * to truncate: the stronger guarantee is that the body is never captured.
     * The real truncation lives in [requireBody] and is covered separately by
     * errorBodyIsTruncatedForLogs.
     */
    @Test
    @DisplayName("A unit endpoint never captures the error body at all")
    fun unitEndpointDoesNotCaptureErrorBody() = runTest {
        val noisy = "x".repeat(MAX_ERROR_BODY * 3)
        val client = clientReturning(HttpStatusCode.BadRequest, body = noisy)

        val outcome = apiCall { client.get("/thing").requireUnit() }

        val http = assertIs<AppError.Http>(assertIs<Outcome.Failure>(outcome).error)
        assertTrue(
            !http.userMessage.contains("x"),
            "the raw error body must never appear in a user-facing message",
        )
        // requireUnit keeps only the status description, never the body.
        val detail: String? = http.technicalDetail
        assertTrue(detail == null || !detail.contains("xxx"), "error body must not reach the log either")
    }

    /**
     * [requireBody] does read the body, for the log. It must cap it, because an
     * upstream service can return a megabyte of HTML in an error response and an
     * unbounded string in a log line is a liability, not diagnostics.
     */
    @Test
    @DisplayName("An error body kept for logs is capped, and the user sees only the status")
    fun errorBodyIsTruncatedForLogs() = runTest {
        val noisy = "x".repeat(MAX_ERROR_BODY * 3)
        val client = clientReturning(HttpStatusCode.BadRequest, body = noisy)

        val outcome = apiCall { client.get("/thing").requireBody<ApiProbe>() }

        val http = assertIs<AppError.Http>(assertIs<Outcome.Failure>(outcome).error)
        assertEquals(400, http.status)
        val detail: String? = http.technicalDetail
        assertEquals(MAX_ERROR_BODY, detail?.length, "the captured body must be capped at MAX_ERROR_BODY")
        assertTrue(
            !http.userMessage.contains("x"),
            "the truncated body is for the log, never for the user",
        )
    }

    @kotlinx.serialization.Serializable
    private data class ApiProbe(val unused: String = "")

    /** A timeout is a transport failure and must be retryable, unlike a 4xx. */
    @Test
    @DisplayName("A request timeout becomes a retryable network failure")
    fun timeoutIsRetryableNetworkFailure() = runTest {
        val engine = MockEngine { throw java.net.SocketTimeoutException("read timed out") }
        val client = createApiClient(ApiEnvironment.Emulator, engine)

        val outcome = apiCall { client.get("/health").requireUnit() }

        val error = assertIs<Outcome.Failure>(outcome).error
        assertIs<AppError.Network>(error)
        assertTrue(error.isRetryable(), "a timeout is worth retrying")
    }

    @Test
    @DisplayName("The base URL must be absolute and end with a slash")
    fun baseUrlIsValidated() {
        val relative = runCatching { ApiEnvironment("/api", "bad") }.exceptionOrNull()
        val missingSlash = runCatching { ApiEnvironment("http://10.0.2.2:8080", "bad") }.exceptionOrNull()

        assertIs<IllegalArgumentException>(relative)
        assertIs<IllegalArgumentException>(missingSlash)
    }

    companion object {
        /**
         * Statuses the API and its proxies can return, with the retry policy
         * each one implies.
         *
         * 429 is absent on purpose: no endpoint rate-limits yet, and adding a
         * row for a behaviour that does not exist would document a fiction.
         * It belongs here the day rate limiting is implemented.
         */
        @JvmStatic
        fun nonSuccessStatuses(): List<Arguments> = listOf(
            arguments(HttpStatusCode.BadRequest, 400, false),
            arguments(HttpStatusCode.Unauthorized, 401, false),
            arguments(HttpStatusCode.Forbidden, 403, false),
            arguments(HttpStatusCode.NotFound, 404, false),
            arguments(HttpStatusCode.Conflict, 409, false),
            arguments(HttpStatusCode(422, "Unprocessable Content"), 422, false),
            arguments(HttpStatusCode.InternalServerError, 500, true),
            arguments(HttpStatusCode.BadGateway, 502, true),
            arguments(HttpStatusCode.ServiceUnavailable, 503, true),
        )
    }
}

/** Minimal response shape, declared here so the test needs no shared contract. */
@kotlinx.serialization.Serializable
data class HealthProbe(
    val service: String,
    val status: String,
)
