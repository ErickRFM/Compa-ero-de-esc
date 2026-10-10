package org.companerodeescuela.api.security

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.routing.routing
import io.ktor.server.testing.*
import java.time.*
import kotlin.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.companerodeescuela.api.auth.*
import org.companerodeescuela.api.integrations.mock.MockIdentityProvider
import org.companerodeescuela.api.plugins.configurePlugins
import org.companerodeescuela.api.test.TestFixtures
import org.companerodeescuela.shared.contracts.*

class MfaRoutesTest {
    private val json=Json
    private val password="operator-test-password"
    private class TestClock:Clock() {
        var now=Instant.now()
        override fun instant()=now
        override fun getZone():ZoneId=ZoneOffset.UTC
        override fun withZone(zone:ZoneId):Clock=this
    }
    private inner class Fixture {
        val accounts=InMemoryPlatformAccountRepository();val sessions=InMemoryRefreshSessionRepository()
        val factors=InMemoryMfaRepository();val clock=TestClock()
        val settings=TestFixtures.settings().copy(jwtSecret="0123456789abcdef0123456789abcdef".toCharArray())
        val identity=MockIdentityProvider();val tokens=AuthTokenService(settings)
        val mfa=MfaService(accounts,factors,MfaSecretCipher(ByteArray(32){it.toByte()}),clock)
        val unavailable=MfaService(accounts,factors,null,clock)
        suspend fun session(id:String="operator",roles:Set<UserRole> = setOf(UserRole.SUPER_ADMIN),status:AccountStatus=AccountStatus.ACTIVE,verified:Boolean=true,source:SessionIdentitySource=SessionIdentitySource.NATIVE):String {
            val account=PlatformAccount(id,"QA Operator","$id@example.test",PasswordHasher().hash(password),roles,accountStatus=status,emailVerifiedAt=if(verified) clock.now.minusSeconds(10) else null)
            assertTrue(accounts.create(account));val user=account.toUserSummary();val sessionId="$id-session"
            sessions.create(RefreshSession(sessionId,"test-hash",user,clock.now,clock.now.plusSeconds(3600),identitySource=source))
            return tokens.issue(user,sessionId).value
        }
    }
    private fun scenario(missingKey:Boolean=false,block:suspend ApplicationTestBuilder.(Fixture)->Unit)=testApplication {
        val f=Fixture()
        application {
            configurePlugins(f.settings,f.sessions,PlatformSessionAuthority(f.accounts,f.identity))
            routing { mfaRoutes(f.settings,f.identity,f.sessions,f.accounts,if(missingKey) f.unavailable else f.mfa) }
        }
        block(f)
    }
    @Test fun `authenticated real MFA enrollment confirmation and bound step up work over Ktor`()=scenario { f ->
        val token=f.session()
        val status=get("/auth/mfa/status",token)
        assertEquals(HttpStatusCode.OK,status.status)
        assertEquals(MfaStatus(false,true),json.decodeFromString<ApiResponse<MfaStatus>>(status.bodyAsText()).data)
        val enrollment=enroll(token)
        assertEquals(32,enrollment.secret.length)
        val code=Totp.code(Totp.decodeSecret(enrollment.secret),f.clock.now.epochSecond)
        assertEquals(HttpStatusCode.OK,post("/auth/mfa/confirmation",json.encodeToString(MfaConfirmationRequest(enrollment.enrollmentId,code)),token).status)
        assertEquals(HttpStatusCode.Forbidden,post("/auth/mfa/step-up",json.encodeToString(MfaStepUpRequest(password,code,StepUpAction.APPROVE_STAFF,"target","operation","a".repeat(64))),token).status)
        f.clock.now=f.clock.now.plusSeconds(30)
        val next=Totp.code(Totp.decodeSecret(enrollment.secret),f.clock.now.epochSecond)
        val request=MfaStepUpRequest(password,next,StepUpAction.APPROVE_STAFF,"target","operation","a".repeat(64))
        val response=post("/auth/mfa/step-up",json.encodeToString(request),token)
        assertEquals(HttpStatusCode.OK,response.status)
        val proof=json.decodeFromString<ApiResponse<StepUpProof>>(response.bodyAsText()).data
        assertEquals(43,proof.proof.length)
        f.mfa.requireStepUp(assertNotNull(f.accounts.findById("operator")),proof.proof,StepUpAction.APPROVE_STAFF,"target","operation","a".repeat(64))
        val visible=get("/auth/mfa/status",token).bodyAsText()
        assertFalse(visible.contains(enrollment.secret));assertFalse(visible.contains(proof.proof));assertFalse(visible.contains("passwordHash"))
    }
    @Test fun `MFA endpoints reject unauthenticated forged and same ID institutional sessions`()=scenario { f ->
        assertEquals(HttpStatusCode.Unauthorized,client.get("/auth/mfa/status").status)
        assertEquals(HttpStatusCode.Unauthorized,get("/auth/mfa/status","forged").status)
        // Even a native account with the exact provider ID must not be selected by guessing a source.
        val external=f.session(org.companerodeescuela.api.integrations.mock.MockFixtures.STUDENT_ID,setOf(UserRole.STUDENT),source=SessionIdentitySource.INSTITUTIONAL)
        assertEquals(HttpStatusCode.Forbidden,get("/auth/mfa/status",external).status)
    }
    @Test fun `verified pending applicant can enroll but cannot authorize administration or bypass verification`()=scenario { f ->
        val pending=f.session("pending",emptySet(),AccountStatus.PENDING_APPROVAL)
        val enrollment=enroll(pending)
        assertEquals(HttpStatusCode.OK,post("/auth/mfa/confirmation",json.encodeToString(MfaConfirmationRequest(enrollment.enrollmentId,Totp.code(Totp.decodeSecret(enrollment.secret),f.clock.now.epochSecond))),pending).status)
        assertEquals(HttpStatusCode.Unauthorized,post("/auth/mfa/step-up",json.encodeToString(MfaStepUpRequest(password,"000000",StepUpAction.APPROVE_STAFF,"target","operation","a".repeat(64))),pending).status)
        val unverified=f.session("unverified",emptySet(),AccountStatus.PENDING_VERIFICATION,false)
        assertEquals(HttpStatusCode.Unauthorized,post("/auth/mfa/enrollment",json.encodeToString(MfaEnrollmentRequest(password)),unverified).status)
    }
    @Test fun `revocation and session rotation invalidate already issued native MFA access`()=scenario { f ->
        val token=f.session()
        assertEquals(HttpStatusCode.OK,get("/auth/mfa/status",token).status)
        assertNotNull(f.accounts.changeIdentity("operator",0,AccountIdentityChange("suspend","security",AccountStatus.SUSPENDED,setOf(UserRole.SUPER_ADMIN),null,f.clock.now)))
        assertEquals(HttpStatusCode.Unauthorized,get("/auth/mfa/status",token).status)
        val other=f.session("other")
        assertNotNull(f.sessions.rotate("other-session","test-hash","rotated",assertNotNull(f.accounts.findById("other")).toUserSummary(),f.clock.now,f.clock.now.plusSeconds(3600)))
        assertEquals(HttpStatusCode.Unauthorized,get("/auth/mfa/status",other).status)
    }
    @Test fun `bounded strict MFA JSON throttles credential attempts without enabling a factor`()=scenario { f ->
        val token=f.session()
        val oversized="""{"password":"${"x".repeat(5000)}"}"""
        assertEquals(HttpStatusCode.BadRequest,post("/auth/mfa/enrollment",oversized,token).status)
        assertEquals(HttpStatusCode.BadRequest,post("/auth/mfa/enrollment","{bad}",token).status)
        repeat(3) { assertEquals(HttpStatusCode.Forbidden,post("/auth/mfa/enrollment",json.encodeToString(MfaEnrollmentRequest("wrong-password")),token).status) }
        val response=post("/auth/mfa/enrollment",json.encodeToString(MfaEnrollmentRequest(password)),token)
        assertEquals(HttpStatusCode.TooManyRequests,response.status)
        assertNotNull(response.headers[HttpHeaders.RetryAfter]);assertNull(f.factors.findFactor("operator"))
    }
    @Test fun `missing deployment encryption key fails closed instead of manufacturing MFA`()=scenario(true) { f ->
        val token=f.session()
        assertEquals(MfaStatus(false,false),json.decodeFromString<ApiResponse<MfaStatus>>(get("/auth/mfa/status",token).bodyAsText()).data)
        assertEquals(HttpStatusCode.ServiceUnavailable,post("/auth/mfa/enrollment",json.encodeToString(MfaEnrollmentRequest(password)),token).status)
        assertNull(f.factors.findFactor("operator"))
    }
    private suspend fun ApplicationTestBuilder.enroll(token:String):MfaEnrollment {
        val response=post("/auth/mfa/enrollment",json.encodeToString(MfaEnrollmentRequest(password)),token)
        assertEquals(HttpStatusCode.OK,response.status)
        return json.decodeFromString<ApiResponse<MfaEnrollment>>(response.bodyAsText()).data
    }
    private suspend fun ApplicationTestBuilder.get(path:String,token:String)=client.get(path){header(HttpHeaders.Authorization,"Bearer $token")}
    private suspend fun ApplicationTestBuilder.post(path:String,body:String,token:String)=client.post(path){header(HttpHeaders.Authorization,"Bearer $token");contentType(ContentType.Application.Json);setBody(body)}
}
