package org.companerodeescuela.core.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.common.result.Outcome

class InvitationApiErrorsTest {
    @Test fun typedErrorKeepsCodeAndCorrelationButNotServerMessage() = runTest {
        val client = createApiClient(ApiEnvironment.Emulator, MockEngine {
            respond(
                """{"code":"mfa_code_invalid","message":"secret INTERNAL diagnostic","requestId":"req-123"}""",
                HttpStatusCode.Forbidden,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        })
        try {
            val failure = assertIs<Outcome.Failure>(apiCall {
                client.get("auth/mfa/step-up").requireBody<Probe>()
            })
            val error = assertIs<AppError.Http>(failure.error)
            assertEquals("MFA_CODE_INVALID", error.apiCode)
            assertEquals("req-123", error.requestId)
            assertFalse(error.userMessage.contains("INTERNAL"))
        } finally {
            client.close()
        }
    }

    @Test fun unknownOrMalformedErrorPreservesStatusWithoutDisplayingBody() = runTest {
        val client = createApiClient(ApiEnvironment.Emulator, MockEngine {
            respond("""{"code":"future_code","message":"secret"}""",
                HttpStatusCode.BadRequest,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        })
        try {
            val error = assertIs<AppError.Http>(
                assertIs<Outcome.Failure>(apiCall { client.get("admin/invitations/prepare").requireBody<Probe>() }).error
            )
            assertEquals(400, error.status)
            assertEquals(null, error.apiCode)
            assertFalse(error.userMessage.contains("secret"))
        } finally {
            client.close()
        }
    }

    @kotlinx.serialization.Serializable
    private data class Probe(val value: String = "")
}
