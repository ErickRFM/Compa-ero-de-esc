package org.companerodeescuela.api.presence

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.routing.routing
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.*
import kotlin.test.*
import org.companerodeescuela.api.auth.*
import org.companerodeescuela.api.institutions.*
import org.companerodeescuela.api.integrations.mock.*
import org.companerodeescuela.api.plugins.configurePlugins
import org.companerodeescuela.api.test.*
import org.companerodeescuela.shared.contracts.*

class SchoolPresenceNativeRoutesTest {
    private class Fixture {
        val clock=QaClock();val accounts=InMemoryPlatformAccountRepository();val sessions=InMemoryRefreshSessionRepository()
        val admin=UserSummary("operator","Operator",roles=setOf(UserRole.ADMIN),institutionId="campus")
        val student=UserSummary(MockFixtures.STUDENT_ID,"Student",roles=setOf(UserRole.STUDENT),institutionId="campus")
        val foreign=UserSummary("outside","Outside",roles=setOf(UserRole.STUDENT),institutionId="other")
        val settings=TestFixtures.settings().copy(jwtSecret="0123456789abcdef0123456789abcdef".toCharArray())
        val tokens=AuthTokenService(settings);val identity=MockIdentityProvider();val repository=InMemorySchoolEntryQrRepository();val days=InMemorySchoolPresenceRepository()
        lateinit var qa:QaMfaOperator;lateinit var qr:SchoolEntryQrService;lateinit var presence:SchoolPresenceService
        suspend fun initialize() {
            qa=QaMfaOperator.create(accounts,admin,clock);accounts.create(ScopedAcademicFixtures.account(student));accounts.create(ScopedAcademicFixtures.account(foreign))
            qr=SchoolEntryQrService(repository,clock,accounts,qa.mfa,institutions=InMemoryInstitutionRepository(listOf(InstitutionSummary("campus","Campus"))))
            presence=SchoolPresenceService(days,SchoolPresencePolicy("",setOf("School"),emptySet()),SchoolEntryQrVerifier(qr),clock=clock,accounts=accounts)
        }
        fun request()=CreateSchoolEntryQrRequest("Entrance",validFromEpochSeconds=clock.now.epochSecond-60,expiresAtEpochSeconds=clock.now.epochSecond+7200,institutionId="campus",operationId="create")
        suspend fun token(actor:UserSummary,source:SessionIdentitySource=SessionIdentitySource.NATIVE):String {
            val id=actor.id+source.name
            sessions.create(RefreshSession(id,"test-hash",actor,Instant.now(),Instant.now().plusSeconds(3600),identitySource=source))
            return tokens.issue(actor,id).value
        }
    }
    private fun scenario(block:suspend ApplicationTestBuilder.(Fixture)->Unit)=testApplication {
        val f=Fixture();runBlocking { f.initialize() }
        application { configurePlugins(f.settings,f.sessions,PlatformSessionAuthority(f.accounts,f.identity));routing { schoolPresenceRoutes(f.settings,f.presence,f.qr) } }
        block(f)
    }
    @Test fun `actual HTTP native MFA QR issuance school day and audited revocation are connected`()=scenario { f ->
        val admin=f.token(f.admin);val student=f.token(f.student);val foreign=f.token(f.foreign)
        val request=f.request()
        assertEquals(HttpStatusCode.Forbidden,post("/presence/admin/qrs",Json.encodeToString(request),admin).status)
        val signed=request.copy(proof=f.qa.proof(StepUpAction.MANAGE_ACADEMICS,"campus",request.operationId!!,PrivilegedCommandDigests.schoolQrCreation("campus",request)))
        val response=post("/presence/admin/qrs",Json.encodeToString(signed),admin)
        assertEquals(HttpStatusCode.OK,response.status)
        val qr=Json.decodeFromString<ApiResponse<SchoolEntryQrResponse>>(response.bodyAsText()).data
        assertEquals("campus",qr.institutionId);assertNotNull(qr.token);assertEquals(1,qr.audit.size)
        val start=StartSchoolPresenceRequest("enter",qr.token!!,SchoolNetworkEvidence(ssid="School"),f.clock.now.epochSecond)
        assertEquals(HttpStatusCode.Forbidden,post("/presence/school-day/start",Json.encodeToString(start),foreign).status)
        val day=post("/presence/school-day/start",Json.encodeToString(start),student)
        assertEquals(HttpStatusCode.OK,day.status)
        val actual=Json.decodeFromString<ApiResponse<SchoolPresenceResponse>>(day.bodyAsText()).data
        assertEquals("campus",actual.institutionId);assertTrue(actual.qrVerified);assertFalse(actual.networkVerified)
        val listed=client.get("/presence/admin/qrs"){bearerAuth(admin)}
        val current=Json.decodeFromString<ApiResponse<List<SchoolEntryQrResponse>>>(listed.bodyAsText()).data.single()
        assertNull(current.token);assertEquals(1,current.usageCount)
        val revoke=AcademicOperationRequest("revoke",current.revision,"")
        assertEquals(HttpStatusCode.Forbidden,post("/presence/admin/qrs/${qr.id}/revoke",Json.encodeToString(revoke),admin).status)
        val proof=f.qa.proof(StepUpAction.MANAGE_ACADEMICS,qr.id,revoke.operationId,PrivilegedCommandDigests.schoolQrRevocation(qr.id,revoke))
        val revoked=post("/presence/admin/qrs/${qr.id}/revoke",Json.encodeToString(revoke.copy(proof=proof)),admin)
        assertEquals(HttpStatusCode.OK,revoked.status)
        assertEquals(listOf("create","revoke"),Json.decodeFromString<ApiResponse<SchoolEntryQrResponse>>(revoked.bodyAsText()).data.audit.map { it.operationId })
    }
    @Test fun `HTTP school scope rejects unauthenticated same ID institutional and unauthorized readers`()=scenario { f ->
        assertEquals(HttpStatusCode.Unauthorized,client.get("/presence/admin/qrs").status)
        val student=f.token(f.student);val external=f.token(f.student,SessionIdentitySource.INSTITUTIONAL)
        assertEquals(HttpStatusCode.Forbidden,client.get("/presence/admin/qrs"){bearerAuth(student)}.status)
        assertEquals(HttpStatusCode.Forbidden,client.get("/presence/school-day"){bearerAuth(external)}.status)
        assertEquals(HttpStatusCode.OK,client.get("/presence/school-day"){bearerAuth(student)}.status)
        val admin=f.token(f.admin)
        assertEquals(HttpStatusCode.BadRequest,post("/presence/admin/qrs","{bad}",admin).status)
        assertEquals(HttpStatusCode.BadRequest,post("/presence/admin/qrs","{\"name\":\"${"x".repeat(5000)}\"}",admin).status)
        assertTrue(f.repository.list().isEmpty())
    }
}
private suspend fun ApplicationTestBuilder.post(path:String,body:String,token:String)=client.post(path) { bearerAuth(token);contentType(ContentType.Application.Json);setBody(body) }
