package org.companerodeescuela.core.network

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import java.util.Base64
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import org.companerodeescuela.core.common.result.*
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.*

class MfaRepositoryTest {
    private class Store:SessionTokenStore {
        var value:String?=listOf("e30",Base64.getUrlEncoder().withoutPadding().encodeToString("""{"sub":"operator","session_id":"session","exp":4102444800}""".toByteArray()),"test").joinToString(".")
        override suspend fun readAccessToken()=value
        override suspend fun writeAccessToken(token:String) { value=token }
        override suspend fun clear() { value=null }
    }
    @Test fun `MFA transport authenticates all native endpoints with typed credential commands and does not store secrets`()=runTest {
        val paths=mutableListOf<String>();val store=Store()
        val client=createApiClient(ApiEnvironment("https://example.test/","test"),MockEngine { request ->
            val path=request.url.encodedPath;paths+=path
            assertTrue(request.headers[HttpHeaders.Authorization]!!.startsWith("Bearer "))
            val body=when(path) {
                "/auth/mfa/status" -> """{"enabled":false,"available":true}"""
                "/auth/mfa/enrollment" -> { assertTrue((request.body as TextContent).text.contains("password"));"""{"enrollmentId":"enrollment","secret":"TESTONLY","provisioningUri":"otpauth://totp/QA","expiresAtEpochSeconds":4102444800}""" }
                "/auth/mfa/confirmation" -> { assertTrue((request.body as TextContent).text.contains("enrollment"));"""{"enabled":true,"available":true}""" }
                "/auth/mfa/step-up" -> { assertTrue((request.body as TextContent).text.contains("APPROVE_STAFF"));"""{"proof":"test-proof","expiresAtEpochSeconds":4102444800}""" }
                else -> error("Unexpected endpoint")
            }
            respond("""{"data":$body}""",HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
        })
        val repo=MfaRepository(client,store);val original=store.value
        assertFalse(assertIs<Outcome.Success<MfaStatus>>(repo.status()).value.enabled)
        assertEquals("TESTONLY",assertIs<Outcome.Success<MfaEnrollment>>(repo.enroll(MfaEnrollmentRequest("test-password"))).value.secret)
        assertTrue(assertIs<Outcome.Success<MfaStatus>>(repo.confirm(MfaConfirmationRequest("enrollment","123456"))).value.enabled)
        assertEquals("test-proof",assertIs<Outcome.Success<StepUpProof>>(repo.stepUp(MfaStepUpRequest("test-password","123456",StepUpAction.APPROVE_STAFF,"target","operation","digest"))).value.proof)
        assertEquals(original,store.value)
        assertEquals(listOf("/auth/mfa/status","/auth/mfa/enrollment","/auth/mfa/confirmation","/auth/mfa/step-up"),paths)
        client.close()
    }
    @Test fun `unavailable MFA or wrong permissions never produce an enrollment or fabricated confirmation`()=runTest {
        for(status in listOf(HttpStatusCode.ServiceUnavailable,HttpStatusCode.Forbidden)) {
            val client=createApiClient(ApiEnvironment("https://example.test/","test"),MockEngine { respond("{}",status,headersOf(HttpHeaders.ContentType,"application/json")) })
            val failure=assertIs<Outcome.Failure>(MfaRepository(client,Store()).enroll(MfaEnrollmentRequest("test")))
            assertEquals(status.value,assertIs<AppError.Http>(failure.error).status);client.close()
        }
    }
    @Test fun `revoked MFA session uses existing teardown instead of retaining privileged UI ownership`()=runTest {
        val store=Store();val client=createApiClient(ApiEnvironment("https://example.test/","test"),MockEngine { respond("{}",HttpStatusCode.Unauthorized,headersOf(HttpHeaders.ContentType,"application/json")) })
        val result=assertIs<Outcome.Failure>(MfaRepository(client,store).status())
        assertEquals(401,assertIs<AppError.Http>(result.error).status);assertNull(store.value);client.close()
    }

    @Test fun `old MFA unauthorized response must not clear a replacement account without refresh credentials`()=runTest {
        val store=Store();val before=store.value
        val next=listOf("e30",Base64.getUrlEncoder().withoutPadding().encodeToString("""{"sub":"replacement","session_id":"new-session","exp":4102444800}""".toByteArray()),"test").joinToString(".")
        val client=createApiClient(ApiEnvironment("https://example.test/","test"),MockEngine {
            store.value=next
            respond("{}",HttpStatusCode.Unauthorized,headersOf(HttpHeaders.ContentType,"application/json"))
        })
        val result=assertIs<Outcome.Failure>(MfaRepository(client,store).status())
        assertEquals(401,assertIs<AppError.Http>(result.error).status);assertEquals(next,store.value);assertNotEquals(before,store.value);client.close()
    }
}
