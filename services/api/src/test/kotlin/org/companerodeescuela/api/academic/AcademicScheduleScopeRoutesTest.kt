package org.companerodeescuela.api.academic

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.routing.routing
import io.ktor.server.testing.*
import java.time.Instant
import kotlin.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.companerodeescuela.api.auth.*
import org.companerodeescuela.api.academic.groups.*
import org.companerodeescuela.api.classroom.*
import org.companerodeescuela.api.integrations.mock.*
import org.companerodeescuela.api.plugins.configurePlugins
import org.companerodeescuela.api.test.*
import org.companerodeescuela.shared.contracts.*

class AcademicScheduleScopeRoutesTest {
    @Test fun `HTTP schedule is bound to actual native actor MFA group class and revisions`()=testApplication {
        val clock=QaClock(Instant.now());val accounts=InMemoryPlatformAccountRepository();val sessions=InMemoryRefreshSessionRepository()
        val groups=InMemoryAcademicGroupRepository();val repository=InMemoryAcademicScheduleOverrideRepository()
        val operator=QaMfaOperator.create(accounts,UserSummary("operator","Operator",roles=setOf(UserRole.ADMIN),institutionId="campus"),clock)
        val teacher=PlatformAccount("teacher","Actual Teacher","teacher@example.test","test-hash",setOf(UserRole.TEACHER),institutionId="campus",emailVerifiedAt=clock.now)
        accounts.create(teacher)
        groups.create(AcademicGroupRecord("6A","Actual Group",true,clock.now,"campus","period",clock.now.minusSeconds(60),clock.now.plusSeconds(3600)))
        val classes=ClassroomService(InMemoryClassroomRepository(),groups,clock,accounts=accounts,mfa=operator.mfa)
        var assignment=CreateClassroomRequest("Actual Class",groupId="6A",groupName="ignored",teacherId="teacher",teacherDisplayName="ignored",operationId="class")
        assignment=assignment.copy(proof=operator.proof(StepUpAction.MANAGE_ACADEMICS,"6A","class",PrivilegedCommandDigests.classroomAssignment("6A",assignment)))
        val classroom=classes.create(operator.actor.toUserSummary(),assignment)
        val service=AcademicScheduleManagementService(repository,clock,accounts=accounts,groups=groups,mfa=operator.mfa,classrooms=classes)
        val settings=TestFixtures.settings().copy(jwtSecret="0123456789abcdef0123456789abcdef".toCharArray());val tokens=AuthTokenService(settings);val identity=MockIdentityProvider()
        val user=operator.actor.toUserSummary()
        sessions.create(RefreshSession("session","test-hash",user,Instant.now(),Instant.now().plusSeconds(3600),identitySource=SessionIdentitySource.NATIVE))
        val token=tokens.issue(user,"session").value
        application { configurePlugins(settings,sessions,PlatformSessionAuthority(accounts,identity));routing { academicRoutes(settings,MockAcademicProvider(),service,repository,groups) } }
        var request=UpsertScheduleBlockRequest(ownerId="teacher",dayOfWeek="MONDAY",startTime="07:00",endTime="07:50",subjectId=classroom.id,subjectName="Untrusted",groupId="6A",operationId="schedule")
        suspend fun post(command:UpsertScheduleBlockRequest)=client.post("/academic/manual-schedule") { header(HttpHeaders.Authorization,"Bearer $token");contentType(ContentType.Application.Json);setBody(Json.encodeToString(command)) }
        assertEquals(HttpStatusCode.Forbidden,post(request).status)
        request=request.copy(proof=operator.proof(StepUpAction.MANAGE_ACADEMICS,"teacher","schedule",PrivilegedCommandDigests.scheduleUpsert("teacher",request)))
        val created=post(request);assertEquals(HttpStatusCode.OK,created.status)
        val managed=Json.decodeFromString<ApiResponse<ManagedScheduleBlock>>(created.bodyAsText()).data
        assertEquals("Actual Class",managed.block.subjectName);assertEquals(1,managed.revision)
        assertEquals(HttpStatusCode.Forbidden,post(request.copy(endTime="08:00")).status)
        assertEquals(HttpStatusCode.OK,client.get("/academic/manual-schedule/teacher") { header(HttpHeaders.Authorization,"Bearer $token") }.status)
        assertEquals(HttpStatusCode.Forbidden,client.delete("/academic/manual-schedule/${managed.block.id}") { header(HttpHeaders.Authorization,"Bearer $token");contentType(ContentType.Application.Json);setBody(Json.encodeToString(AcademicOperationRequest("cancel",managed.revision,""))) }.status)
        assertEquals(managed,repository.findById(managed.block.id))
    }
}
