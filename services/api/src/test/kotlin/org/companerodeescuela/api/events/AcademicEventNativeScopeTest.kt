package org.companerodeescuela.api.events

import java.time.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import org.companerodeescuela.api.auth.*
import org.companerodeescuela.api.academic.groups.*
import org.companerodeescuela.api.classroom.*
import org.companerodeescuela.api.institutions.*
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.test.*
import org.companerodeescuela.shared.contracts.*

class AcademicEventNativeScopeTest {
    private inner class Fixture {
        val clock=QaClock();val accounts=InMemoryPlatformAccountRepository();val groups=InMemoryAcademicGroupRepository();val classes=InMemoryClassroomRepository();val repository=InMemoryAcademicEventRepository()
        val admin=UserSummary("admin","Admin",roles=setOf(UserRole.ADMIN),institutionId="campus")
        val teacher=UserSummary("teacher","Teacher",roles=setOf(UserRole.TEACHER),institutionId="campus")
        val student=UserSummary("student","Student",roles=setOf(UserRole.STUDENT),institutionId="campus")
        val guest=UserSummary("guest","Guest",roles=setOf(UserRole.WORKSHOP_PARTICIPANT))
        lateinit var qa:QaMfaOperator;lateinit var service:AcademicEventService
        suspend fun initialize() {
            qa=QaMfaOperator.create(accounts,admin,clock)
            for(actor in listOf(teacher,student,guest)) accounts.create(ScopedAcademicFixtures.account(actor))
            groups.create(ScopedAcademicFixtures.group("6A").copy(institutionId="campus"));groups.create(ScopedAcademicFixtures.group("6B").copy(institutionId="other"))
            groups.assign(AcademicGroupMembershipRecord("6A",student.id,clock.now,groupScopeRevision=0))
            classes.createClassroom(NativeClassroom("own-class","Own class",null,null,"6A","6A",teacher.id,"Teacher",ClassroomStatus.ACTIVE,clock.now,0,0))
            classes.createClassroom(NativeClassroom("foreign-class","Foreign",null,null,"6B","6B","outside","Outside",ClassroomStatus.ACTIVE,clock.now,0,0))
            classes.addMembershipIfAbsent(NativeClassroomMembership("own-class",teacher.id,ClassroomMemberRole.TEACHER,clock.now))
            service=AcademicEventService(repository,accounts,groups,ClassroomService(classes,groups,clock,accounts=accounts),InMemoryInstitutionRepository(listOf(InstitutionSummary("campus","Campus"),InstitutionSummary("other","Other"))),qa.mfa,clock)
        }
        fun event(scope:AcademicEventScope=AcademicEventScope.COURSE,target:String="own-class")=AcademicEvent("",AcademicEventType.ANNOUNCEMENT,AcademicEventSource.ADMIN,"forged-author",target=AcademicEventTarget(scope,target),title="Class notice",createdAtEpochSeconds=1,groupScopeRevision=0)
        fun command(op:String="create",event:AcademicEvent=event())=AcademicEventWriteRequest(op,0,event)
        suspend fun signed(command:AcademicEventWriteRequest,id:String?=null)=command.copy(proof=qa.proof(StepUpAction.MANAGE_ACADEMICS,id ?: command.event.target.id,command.operationId,PrivilegedCommandDigests.eventWrite(id ?: command.event.target.id,command)))
    }
    @Test fun `teacher event writer derives real author and checks current assigned classroom`()=runTest {
        val f=Fixture();f.initialize()
        assertFailsWith<ApiException.Forbidden> { f.service.writeScoped(f.teacher,null,f.command(event=f.event(target="foreign-class"))) }
        assertFailsWith<ApiException.Forbidden> { f.service.writeScoped(f.teacher,null,f.command(event=f.event(AcademicEventScope.INSTITUTION,"campus"))) }
        val created=f.service.writeScoped(f.teacher,null,f.command())
        assertEquals(f.teacher.id,created.sourceId);assertEquals(AcademicEventSource.TEACHER,created.source);assertEquals("campus",created.institutionId)
        assertEquals("Own class",created.subjectName);assertEquals(1,created.audit.size)
    }
    @Test fun `admin needs actual capability own tenant and command bound MFA`()=runTest {
        val f=Fixture();f.initialize();val command=f.command(event=f.event(AcademicEventScope.INSTITUTION,"campus"))
        assertFailsWith<ApiException.Forbidden> { f.service.writeScoped(f.admin,null,command) }
        assertFailsWith<ApiException.Forbidden> { f.service.writeScoped(f.admin,null,f.command(event=f.event(AcademicEventScope.INSTITUTION,"other"))) }
        val signed=f.signed(command)
        assertFailsWith<ApiException.Forbidden> { f.service.writeScoped(f.admin,null,signed.copy(event=signed.event.copy(title="tampered"))) }
        val created=f.service.writeScoped(f.admin,null,signed)
        assertEquals("campus",created.institutionId);assertEquals(f.admin.id,created.sourceId)
    }
    @Test fun `event feed denies guests and filters foreign unmigrated and nonmember student scope`()=runTest {
        val f=Fixture();f.initialize();val created=f.service.writeScoped(f.teacher,null,f.command())
        f.repository.save(created.copy(id="foreign",institutionId="other",target=AcademicEventTarget(AcademicEventScope.COURSE,"foreign-class")))
        f.repository.save(created.copy(id="legacy",institutionId=null))
        assertFailsWith<ApiException.Forbidden> { f.service.listScoped(f.guest) }
        assertEquals(listOf(created.id),f.service.listScoped(f.student).events.map { it.id })
        assertFailsWith<ApiException.Forbidden> { f.service.getScoped(f.student,"foreign") }
    }
    @Test fun `event operations use deterministic retry and strict revision without replacing foreign ID`()=runTest {
        val f=Fixture();f.initialize();val command=f.command();val first=f.service.writeScoped(f.teacher,null,command)
        assertEquals(first,f.service.writeScoped(f.teacher,null,command));assertEquals(1,f.repository.listEvents().size)
        assertFailsWith<ApiException.Conflict> { f.service.writeScoped(f.teacher,null,command.copy(event=command.event.copy(title="different"))) }
        assertFailsWith<ApiException.Validation> { f.service.writeScoped(f.teacher,null,f.command("collision",f.event().copy(id="foreign-ID"))) }
        val update=AcademicEventWriteRequest("update",first.revision,f.event().copy(title="Updated"))
        val changed=f.service.writeScoped(f.teacher,first.id,update)
        assertEquals(first.revision+1,changed.revision);assertEquals(2,changed.audit.size)
        assertFailsWith<ApiException.Conflict> { f.service.writeScoped(f.teacher,first.id,update.copy(operationId="stale")) }
    }
    @Test fun `cancellation retains event audit and cannot be applied outside writer scope`()=runTest {
        val f=Fixture();f.initialize();val first=f.service.writeScoped(f.teacher,null,f.command())
        assertFailsWith<ApiException.Forbidden> { f.service.cancelScoped(f.student,first.id,AcademicOperationRequest("cancel",first.revision,"")) }
        assertTrue(f.service.cancelScoped(f.teacher,first.id,AcademicOperationRequest("cancel",first.revision,"")))
        val stored=assertNotNull(f.repository.findById(first.id));assertTrue(stored.cancelled);assertEquals(2,stored.audit.size)
        assertTrue(f.service.listScoped(f.student).events.isEmpty())
        assertTrue(f.service.cancelScoped(f.teacher,first.id,AcademicOperationRequest("cancel",first.revision,"")))
    }
}
