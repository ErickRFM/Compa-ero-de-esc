package org.companerodeescuela.mobile

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class MobileApiClientTest {
    private val headers = headersOf(HttpHeaders.ContentType, "application/json")
    @Test fun healthUsesPrefixAndExistingJson() = runTest {
        val engine = MockEngine { request ->
            assertEquals("https://school.example/api/health", request.url.toString())
            respond("""{"service":"companero","status":"up","version":"1","environment":"test","timestamp":"2026-10-08T20:00:00Z","dependencies":[]}""", headers=headers)
        }
        val client = MobileApiClient(ApiConfiguration("https://school.example/api"), engine)
        try {
            val result = assertIs<ApiResult.Success<HealthPayload>>(client.health())
            assertEquals("up", result.value.status)
            assertEquals("2026-10-08T20:00:00Z", result.value.timestamp)
        } finally { client.close() }
    }
    @Test fun loginUsesRealEnvelopeAndJsonRequest() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/api/auth/login", request.url.encodedPath)
            assertEquals(HttpMethod.Post, request.method)
            assertEquals(ContentType.Application.Json, request.body.contentType)
            respond("""{"data":{"accessToken":"test-access","expiresAtEpochSeconds":1900000000,"sessionId":"s1","refreshToken":"test-refresh","user":{"id":"u1","displayName":"Alumno","roles":["student"]}}}""", headers=headers)
        }
        val client = MobileApiClient(ApiConfiguration("https://school.example/api/"), engine)
        try {
            val response = assertIs<ApiResult.Success<org.companerodeescuela.shared.contracts.LoginResponse>>(client.login("student", "test-only"))
            assertEquals("Alumno", response.value.user.displayName)
        } finally { client.close() }
    }
    @Test fun httpStatusesAreNotTransportFailures() = runTest {
        listOf(401,403,429,503).forEach { status ->
            val client = MobileApiClient(ApiConfiguration("https://school.example/"),
                MockEngine { respond("""{"code":"dependency_unavailable","message":"technical-only"}""",
                    HttpStatusCode.fromValue(status), headers) })
            try {
                val result = assertIs<ApiResult.Failure>(client.health())
                assertEquals(status, result.statusCode)
                assertEquals(FailureKind.HTTP, result.kind)
                assertFalse(result.message.contains("technical-only"))
            } finally { client.close() }
        }
    }
    @Test fun malformedResponseIsControlled() = runTest {
        val client = MobileApiClient(ApiConfiguration("https://school.example/"),
            MockEngine { respond("not json", headers=headers) })
        try { assertEquals(FailureKind.FORMAT, assertIs<ApiResult.Failure>(client.health()).kind) }
        finally { client.close() }
    }
    @Test fun readinessUnavailableRetains503() = runTest {
        val client = MobileApiClient(ApiConfiguration("https://school.example/"),
            MockEngine { respond("""{"status":"degraded","timestamp":"2026-10-08T20:00:00Z","checks":[]}""",
                HttpStatusCode.ServiceUnavailable, headers) })
        try { assertEquals(503, assertIs<ApiResult.Failure>(client.readiness()).statusCode) }
        finally { client.close() }
    }
    @Test fun cancellationIsPropagated() = runTest {
        val client = MobileApiClient(ApiConfiguration("https://school.example/"),
            MockEngine { throw CancellationException("cancelled") })
        try { assertFailsWith<CancellationException> { client.health() } }
        finally { client.close() }
    }
    @Test fun transportFailureIsControlled() = runTest {
        val client = MobileApiClient(ApiConfiguration("https://school.example/"),
            MockEngine { throw IllegalStateException("test transport unavailable") })
        try { assertEquals(FailureKind.NETWORK, assertIs<ApiResult.Failure>(client.health()).kind) }
        finally { client.close() }
    }

    @Test fun closingClientClosesItsEngine() = runTest {
        val engine = MockEngine { respond("{}", headers=headers) }
        val client = MobileApiClient(ApiConfiguration("https://school.example/"), engine)
        client.close()
        assertFalse(engine.coroutineContext[kotlinx.coroutines.Job]!!.isActive, "Client must release its owned engine")
    }

    @Test fun timeoutIsControlledWithoutLeakingRequestDetails() = runTest {
        val client = MobileApiClient(ApiConfiguration("https://school.example/"),
            MockEngine { request -> throw io.ktor.client.plugins.HttpRequestTimeoutException(request) })
        try {
            val result = assertIs<ApiResult.Failure>(client.health())
            assertEquals(FailureKind.NETWORK, result.kind)
            assertFalse(result.message.contains("school.example"))
        } finally { client.close() }
    }
    @Test fun unknownUserRoleReturnsFormatFailure() = runTest {
        val client = MobileApiClient(ApiConfiguration("https://school.example/"),
            MockEngine { respond("""{"data":{"accessToken":"test-access","expiresAtEpochSeconds":1900000000,"sessionId":"s1","refreshToken":"test-refresh","user":{"id":"u1","displayName":"Alumno","roles":["unknown_role"]}}}""", headers=headers) })
        try { assertEquals(FailureKind.FORMAT, assertIs<ApiResult.Failure>(client.login("student", "test-only")).kind) }
        finally { client.close() }
    }
}
