package org.companerodeescuela.api.admin

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
import org.companerodeescuela.api.academic.groups.*
import org.companerodeescuela.api.institutions.InMemoryInstitutionRepository
import org.companerodeescuela.api.tutoring.*
import org.companerodeescuela.api.security.*
import org.companerodeescuela.api.plugins.configurePlugins
import org.companerodeescuela.api.integrations.mock.MockIdentityProvider
import org.companerodeescuela.api.test.TestFixtures
import org.companerodeescuela.shared.contracts.*

class AccountAdministrationRoutesTest {
    private val json=Json;private val password="operator-test-password"
    private class TestClock:Clock() {
        var now=Instant.now().minusSeconds(1)
        override fun instant()=now
        override fun getZone():ZoneId=ZoneOffset.UTC
        override fun withZone(zone:ZoneId):Clock=this
    }
    private inner class Fixture {
        val clock=TestClock();val settings=TestFixtures.settings().copy(jwtSecret="0123456789abcdef0123456789abcdef".toCharArray())
        val accounts=InMemoryPlatformAccountRepository();val sessions=InMemoryRefreshSessionRepository();val groups=InMemoryAcademicGroupRepository()
        val identity=MockIdentityProvider();val tokens=AuthTokenService(settings)
        val directory=InMemoryInstitutionRepository(listOf(InstitutionSummary("campus","QA Campus"),InstitutionSummary("other","Other QA campus")))
        val mfa=MfaService(accounts,InMemoryMfaRepository(),MfaSecretCipher(ByteArray(32){it.toByte()}),clock)
        val tutoring=TutorAssignmentService(InMemoryTutorAssignmentRepository(),groups,clock,accounts)
        val service=AccountAdministrationService(accounts,directory,groups,tutoring,mfa,clock)
        suspend fun session(id:String,roles:Set<UserRole>,institution:String?="campus",status:AccountStatus=AccountStatus.ACTIVE,profile:RegistrationAccountType?=null):String {
            val account=PlatformAccount(id,"QA User","$id@example.test",PasswordHasher().hash(password),roles,institutionId=if(status==AccountStatus.ACTIVE) institution else null,accountStatus=status,emailVerifiedAt=clock.now,registrationIntent=profile?.let { RegistrationIntent(it,institution,"reference","6A") },adminCapabilities=if(UserRole.ADMIN in roles) setOf(AdminCapability.APPROVE_STAFF,AdminCapability.MANAGE_USERS,AdminCapability.MANAGE_ACADEMICS) else emptySet())
            accounts.create(account);val user=account.toUserSummary();sessions.create(RefreshSession("$id-session","test-hash",user,Instant.now(),Instant.now().plusSeconds(3600),identitySource=SessionIdentitySource.NATIVE))
            return tokens.issue(user,"$id-session").value
        }
    }
    private fun scenario(block:suspend ApplicationTestBuilder.(Fixture)->Unit)=testApplication {
        val f=Fixture()
        application {
            configurePlugins(f.settings,f.sessions,PlatformSessionAuthority(f.accounts,f.identity))
            routing {
                authRoutes(f.settings,f.identity,sessions=f.sessions,accounts=f.accounts,institutions=f.directory)
                mfaRoutes(f.settings,f.identity,f.sessions,f.accounts,f.mfa)
                accountAdministrationRoutes(f.settings,f.identity,f.sessions,f.accounts,f.service)
            }
        }
        block(f)
    }
    @Test fun `Ktor real MFA approves verified teacher and old pending session cannot keep authority`()=scenario { f ->
        val admin=f.session("admin",setOf(UserRole.ADMIN));val pending=f.session("pending",emptySet(),status=AccountStatus.PENDING_APPROVAL,profile=RegistrationAccountType.TEACHER)
        assertEquals(HttpStatusCode.OK,get("/auth/staff-applications",pending).status)
        assertEquals(HttpStatusCode.Unauthorized,get("/admin/accounts",pending).status)
        var command=ApproveStaffRequest("approve",0,"registration-pending","campus",proof="")
        val proof=proof(f,admin,"pending",command)
        command=command.copy(proof=proof)
        val response=post("/admin/accounts/pending/staff-approval",json.encodeToString(command),admin)
        assertEquals(HttpStatusCode.OK,response.status)
        val result=json.decodeFromString<ApiResponse<AdminUserSummary>>(response.bodyAsText()).data
        assertEquals(setOf(UserRole.TEACHER),result.user.roles);assertEquals(1,result.user.authRevision)
        assertEquals(HttpStatusCode.Unauthorized,get("/auth/me",pending).status)
        val login=client.post("/auth/login"){contentType(ContentType.Application.Json);setBody(json.encodeToString(LoginRequest("pending@example.test",password)))}
        assertEquals(HttpStatusCode.OK,login.status)
        assertEquals(setOf(UserRole.TEACHER),json.decodeFromString<ApiResponse<LoginResponse>>(login.bodyAsText()).data.user.roles)
        assertFalse(response.bodyAsText().contains(proof))
    }
    @Test fun `teacher can request tutor but response never grants local groups or administrative permission`()=scenario { f ->
        val teacher=f.session("teacher",setOf(UserRole.TEACHER))
        val response=post("/auth/staff-applications",json.encodeToString(TutorApplicationRequest("request",0,"campus","reference","preferred-only")),teacher)
        assertEquals(HttpStatusCode.Created,response.status)
        assertEquals(StaffApplicationStatus.PENDING,json.decodeFromString<ApiResponse<StaffApplication>>(response.bodyAsText()).data.status)
        val me=get("/auth/me",teacher);assertEquals(HttpStatusCode.OK,me.status)
        assertEquals(setOf(UserRole.TEACHER),json.decodeFromString<ApiResponse<UserSummary>>(me.bodyAsText()).data.roles)
        assertEquals(HttpStatusCode.Forbidden,get("/admin/accounts",teacher).status)
        assertEquals(HttpStatusCode.Forbidden,post("/admin/accounts/teacher/staff-approval",json.encodeToString(ApproveStaffRequest("self",0,"tutor-request-request","campus",proof="")),teacher).status)
    }
    @Test fun `administration API denies cross institution modified proof oversized JSON and revoked administrator`()=scenario { f ->
        val admin=f.session("admin",setOf(UserRole.ADMIN));f.session("outside",emptySet(),"other",AccountStatus.PENDING_APPROVAL,RegistrationAccountType.TEACHER)
        val overview=get("/admin/accounts",admin)
        assertEquals(HttpStatusCode.OK,overview.status);assertFalse(overview.bodyAsText().contains("outside@example.test"))
        assertEquals(HttpStatusCode.Forbidden,post("/admin/accounts/outside/staff-approval",json.encodeToString(ApproveStaffRequest("approve",0,"registration-outside","other",proof="")),admin).status)
        f.session("pending",emptySet(),status=AccountStatus.PENDING_APPROVAL,profile=RegistrationAccountType.TEACHER)
        val command=ApproveStaffRequest("approve",0,"registration-pending","campus",proof="")
        val proof=proof(f,admin,"pending",command)
        assertEquals(HttpStatusCode.Forbidden,post("/admin/accounts/pending/staff-approval",json.encodeToString(command.copy(operationId="different",proof=proof)),admin).status)
        assertEquals(HttpStatusCode.BadRequest,post("/admin/accounts/pending/staff-approval","""{"proof":"${"x".repeat(5000)}"}""",admin).status)
        f.accounts.changeIdentity("admin",0,AccountIdentityChange("revoke","owner",AccountStatus.REVOKED,setOf(UserRole.ADMIN),"campus",f.clock.now))
        assertEquals(HttpStatusCode.Unauthorized,get("/admin/accounts",admin).status)
    }
    private suspend fun ApplicationTestBuilder.proof(f:Fixture,token:String,target:String,command:ApproveStaffRequest):String {
        val enrollmentResponse=post("/auth/mfa/enrollment",json.encodeToString(MfaEnrollmentRequest(password)),token)
        assertEquals(HttpStatusCode.OK,enrollmentResponse.status)
        val enrollment=json.decodeFromString<ApiResponse<MfaEnrollment>>(enrollmentResponse.bodyAsText()).data
        val secret=Totp.decodeSecret(enrollment.secret)
        assertEquals(HttpStatusCode.OK,post("/auth/mfa/confirmation",json.encodeToString(MfaConfirmationRequest(enrollment.enrollmentId,Totp.code(secret,f.clock.now.epochSecond))),token).status)
        f.clock.now=f.clock.now.plusSeconds(30)
        val proofResponse=post("/auth/mfa/step-up",json.encodeToString(MfaStepUpRequest(password,Totp.code(secret,f.clock.now.epochSecond),StepUpAction.APPROVE_STAFF,target,command.operationId,PrivilegedCommandDigests.staffApproval(target,command))),token)
        assertEquals(HttpStatusCode.OK,proofResponse.status)
        return json.decodeFromString<ApiResponse<StepUpProof>>(proofResponse.bodyAsText()).data.proof
    }
    private suspend fun ApplicationTestBuilder.get(path:String,token:String)=client.get(path){header(HttpHeaders.Authorization,"Bearer $token")}
    private suspend fun ApplicationTestBuilder.post(path:String,body:String,token:String)=client.post(path){header(HttpHeaders.Authorization,"Bearer $token");contentType(ContentType.Application.Json);setBody(body)}
}
