package org.companerodeescuela.api.events

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
import org.companerodeescuela.api.academic.groups.*
import org.companerodeescuela.api.classroom.*
import org.companerodeescuela.api.institutions.*
import org.companerodeescuela.api.integrations.mock.*
import org.companerodeescuela.api.plugins.configurePlugins
import org.companerodeescuela.api.test.*
import org.companerodeescuela.shared.contracts.*

class AcademicEventNativeRoutesTest {
    private class Fixture {
        val clock=QaClock();val accounts=InMemoryPlatformAccountRepository();val sessions=InMemoryRefreshSessionRepository();val groups=InMemoryAcademicGroupRepository();val classes=InMemoryClassroomRepository();val repository=InMemoryAcademicEventRepository()
        val teacher=UserSummary(MockFixtures.TEACHER_ID,"Teacher",roles=setOf(UserRole.TEACHER),institutionId="campus")
        val admin=UserSummary("operator","Operator",roles=setOf(UserRole.ADMIN),institutionId="campus")
        val guest=UserSummary("guest","Guest",roles=setOf(UserRole.WORKSHOP_PARTICIPANT))
        val settings=TestFixtures.settings().copy(jwtSecret="0123456789abcdef0123456789abcdef".toCharArray());val tokens=AuthTokenService(settings);val identity=MockIdentityProvider()
        lateinit var qa:QaMfaOperator;lateinit var service:AcademicEventService
        suspend fun initialize() {
            qa=QaMfaOperator.create(accounts,admin,clock);accounts.create(ScopedAcademicFixtures.account(teacher));accounts.create(ScopedAcademicFixtures.account(guest))
            groups.create(ScopedAcademicFixtures.group("6A").copy(institutionId="campus"))
            classes.createClassroom(NativeClassroom("class","Class",null,null,"6A","6A",teacher.id,"Teacher",ClassroomStatus.ACTIVE,clock.now,0,0))
            classes.addMembershipIfAbsent(NativeClassroomMembership("class",teacher.id,ClassroomMemberRole.TEACHER,clock.now))
            service=AcademicEventService(repository,accounts,groups,ClassroomService(classes,groups,clock,accounts=accounts),InMemoryInstitutionRepository(listOf(InstitutionSummary("campus","Campus"))),qa.mfa,clock)
        }
        fun event(target:AcademicEventTarget=AcademicEventTarget(AcademicEventScope.COURSE,"class"))=AcademicEvent("",AcademicEventType.ANNOUNCEMENT,AcademicEventSource.ADMIN,"client-forged",target=target,title="Notice",createdAtEpochSeconds=1,groupScopeRevision=0)
        suspend fun token(actor:UserSummary,source:SessionIdentitySource=SessionIdentitySource.NATIVE):String {
            val id=actor.id+source.name;sessions.create(RefreshSession(id,"hash",actor,Instant.now(),Instant.now().plusSeconds(3600),identitySource=source));return tokens.issue(actor,id).value
        }
    }
    private fun scenario(block:suspend ApplicationTestBuilder.(Fixture)->Unit)=testApplication {
        val f=Fixture();runBlocking { f.initialize() }
        application { configurePlugins(f.settings,f.sessions,PlatformSessionAuthority(f.accounts,f.identity));routing { academicEventRoutes(f.settings,f.service) } }
        block(f)
    }
    @Test fun `HTTP event teacher publishing uses native assignment and never client authorship`()=scenario { f ->
        val token=f.token(f.teacher);val request=AcademicEventWriteRequest("publish",0,f.event())
        val response=post("/events",Json.encodeToString(request),token)
        assertEquals(HttpStatusCode.OK,response.status)
        val event=Json.decodeFromString<ApiResponse<AcademicEvent>>(response.bodyAsText()).data
        assertEquals(f.teacher.id,event.sourceId);assertEquals(AcademicEventSource.TEACHER,event.source);assertEquals("campus",event.institutionId)
        // Existing participant route allowlist rejects academic routes before service authorization.
        assertEquals(HttpStatusCode.Unauthorized,client.get("/events"){bearerAuth(f.token(f.guest))}.status)
        assertEquals(HttpStatusCode.Forbidden,client.get("/events/${event.id}"){bearerAuth(f.token(f.teacher,SessionIdentitySource.INSTITUTIONAL))}.status)
        f.repository.save(event.copy(id="foreign",institutionId="other"))
        assertEquals(listOf(event.id),Json.decodeFromString<ApiResponse<AcademicEventFeedResponse>>(client.get("/events"){bearerAuth(token)}.bodyAsText()).data.events.map { it.id })
        assertEquals(HttpStatusCode.Forbidden,client.get("/events/foreign"){bearerAuth(token)}.status)
    }
    @Test fun `HTTP administrative event write and cancellation require exact native MFA and retain audit`()=scenario { f ->
        val token=f.token(f.admin);val request=AcademicEventWriteRequest("publish",0,f.event(AcademicEventTarget(AcademicEventScope.INSTITUTION,"campus")))
        assertEquals(HttpStatusCode.Forbidden,post("/events",Json.encodeToString(request),token).status)
        val proof=f.qa.proof(StepUpAction.MANAGE_ACADEMICS,"campus",request.operationId,PrivilegedCommandDigests.eventWrite("campus",request))
        val response=post("/events",Json.encodeToString(request.copy(proof=proof)),token)
        assertEquals(HttpStatusCode.OK,response.status)
        val event=Json.decodeFromString<ApiResponse<AcademicEvent>>(response.bodyAsText()).data
        val cancel=AcademicOperationRequest("cancel",event.revision,"")
        suspend fun cancel(body:String)=client.delete("/events/${event.id}"){bearerAuth(token);contentType(ContentType.Application.Json);setBody(body)}
        assertEquals(HttpStatusCode.Forbidden,cancel(Json.encodeToString(cancel)).status)
        val cancellationProof=f.qa.proof(StepUpAction.MANAGE_ACADEMICS,event.id,cancel.operationId,PrivilegedCommandDigests.eventCancellation(event.id,cancel))
        assertEquals(HttpStatusCode.OK,cancel(Json.encodeToString(cancel.copy(proof=cancellationProof))).status)
        val stored=assertNotNull(f.repository.findById(event.id));assertTrue(stored.cancelled);assertEquals(2,stored.audit.size)
    }
}
private suspend fun ApplicationTestBuilder.post(path:String,body:String,token:String)=client.post(path){bearerAuth(token);contentType(ContentType.Application.Json);setBody(body)}
