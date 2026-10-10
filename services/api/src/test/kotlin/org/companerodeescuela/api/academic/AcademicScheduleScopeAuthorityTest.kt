package org.companerodeescuela.api.academic

import java.time.Instant
import kotlinx.coroutines.*
import kotlin.test.*
import org.companerodeescuela.api.auth.*
import org.companerodeescuela.api.academic.groups.*
import org.companerodeescuela.api.classroom.*
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.test.*
import org.companerodeescuela.shared.contracts.*

class AcademicScheduleScopeAuthorityTest {
    private val now=Instant.parse("2026-10-10T07:00:00Z")
    private inner class Fixture {
        val clock=QaClock(now);val accounts=InMemoryPlatformAccountRepository();val groups=InMemoryAcademicGroupRepository()
        val repo=InMemoryAcademicScheduleOverrideRepository();val classRepo=InMemoryClassroomRepository()
        lateinit var operator:QaMfaOperator;lateinit var service:AcademicScheduleManagementService
        lateinit var classroom:ClassroomSummary
        suspend fun initialize() {
            operator=QaMfaOperator.create(accounts,UserSummary("admin","Actual Admin",roles=setOf(UserRole.ADMIN),institutionId="campus"),clock)
            val teacher=PlatformAccount("teacher","Actual Teacher","teacher@example.test","test-hash",setOf(UserRole.TEACHER),institutionId="campus",emailVerifiedAt=now)
            accounts.create(teacher);accounts.create(teacher.copy(id="outside",email="outside@example.test",institutionId="other"))
            groups.create(AcademicGroupRecord("6A","Actual Group",true,now,"campus","period",now,now.plusSeconds(3600)))
            groups.create(AcademicGroupRecord("OTHER","Other Group",true,now,"other","period",now,now.plusSeconds(3600)))
            val classes=ClassroomService(classRepo,groups,clock,accounts=accounts,mfa=operator.mfa)
            var assignment=CreateClassroomRequest("Actual Class",groupId="6A",groupName="ignored",teacherId="teacher",teacherDisplayName="ignored",operationId="class")
            assignment=assignment.copy(proof=operator.proof(StepUpAction.MANAGE_ACADEMICS,"6A","class",PrivilegedCommandDigests.classroomAssignment("6A",assignment)))
            classroom=classes.create(operator.actor.toUserSummary(),assignment)
            service=AcademicScheduleManagementService(repo,clock,accounts=accounts,groups=groups,mfa=operator.mfa,classrooms=classes)
        }
        fun request()=UpsertScheduleBlockRequest(ownerId="teacher",dayOfWeek="MONDAY",startTime="07:00",endTime="07:50",subjectId=classroom.id,subjectName="Untrusted",teacherId="teacher",teacherName="Untrusted",groupId="6A",groupName="Untrusted",operationId="schedule")
        suspend fun signed(request:UpsertScheduleBlockRequest=request())=request.copy(proof=operator.proof(StepUpAction.MANAGE_ACADEMICS,request.ownerId,request.operationId!!,PrivilegedCommandDigests.scheduleUpsert(request.ownerId,request)))
    }
    @Test fun `actual assigned class schedule needs command MFA and parallel creation is idempotent`()=runBlocking<Unit> {
        val f=Fixture();f.initialize();val actor=f.operator.actor.toUserSummary()
        assertFailsWith<ApiException.Forbidden> { f.service.upsertScoped(actor,f.request()) }
        val command=f.signed();val results=coroutineScope { (1..12).map { async { f.service.upsertScoped(actor,command) } }.awaitAll() }
        assertEquals(1,results.map { it.block.id }.toSet().size);assertEquals(1,f.repo.listForOwner("teacher").size)
        assertEquals("Actual Class",results.first().block.subjectName);assertEquals("Actual Teacher",results.first().block.teacherName)
        assertFailsWith<ApiException.Forbidden> { f.service.upsertScoped(actor,command.copy(groupId="OTHER")) }
    }
    @Test fun `admin cannot read mutate or cancel another institution schedule even using old owner parameters`()=runBlocking<Unit> {
        val f=Fixture();f.initialize();val actor=f.operator.actor.toUserSummary()
        assertFailsWith<ApiException.Forbidden> { f.service.listScoped(actor,"outside") }
        assertFailsWith<ApiException.Forbidden> { f.service.upsertScoped(actor,f.signed(f.request().copy(ownerId="outside",groupId="OTHER"))) }
        val original=f.service.upsertScoped(actor,f.signed())
        val other=UserSummary("other-admin","Other Admin",roles=setOf(UserRole.ADMIN),institutionId="other");f.accounts.create(ScopedAcademicFixtures.account(other))
        assertFailsWith<ApiException.Forbidden> { f.service.deleteScoped(other,original.block.id,AcademicOperationRequest("delete",original.revision,"")) }
        assertEquals(original,f.repo.findById(original.block.id))
    }
    @Test fun `schedule cancellation requires real scoped proof retains audit revision and cannot be replayed across payload`()=runBlocking<Unit> {
        val f=Fixture();f.initialize();val actor=f.operator.actor.toUserSummary();val current=f.service.upsertScoped(actor,f.signed())
        var command=AcademicOperationRequest("cancel",current.revision,"")
        assertFailsWith<ApiException.Forbidden> { f.service.deleteScoped(actor,current.block.id,command) }
        command=command.copy(proof=f.operator.proof(StepUpAction.MANAGE_ACADEMICS,current.block.id,command.operationId,PrivilegedCommandDigests.scheduleCancellation(current.block.id,command)))
        assertTrue(f.service.deleteScoped(actor,current.block.id,command));assertTrue(f.service.deleteScoped(actor,current.block.id,command))
        val cancelled=assertNotNull(f.repo.findById(current.block.id))
        assertEquals(BlockStatus.CANCELLED,cancelled.block.status);assertEquals(current.revision+1,cancelled.revision);assertEquals("cancel",cancelled.operationId)
        assertFailsWith<ApiException.Forbidden> { f.service.deleteScoped(actor,current.block.id,command.copy(expectedRevision=command.expectedRevision+1)) }
    }
}
