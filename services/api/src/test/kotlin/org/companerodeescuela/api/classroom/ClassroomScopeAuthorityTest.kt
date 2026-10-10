package org.companerodeescuela.api.classroom

import java.time.Instant
import kotlinx.coroutines.*
import kotlin.test.*
import org.companerodeescuela.api.auth.*
import org.companerodeescuela.api.academic.groups.*
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.test.*
import org.companerodeescuela.shared.contracts.*

class ClassroomScopeAuthorityTest {
    private val now=Instant.parse("2026-10-10T07:00:00Z")
    private inner class Fixture {
        val clock=QaClock(now);val accounts=InMemoryPlatformAccountRepository();val groups=InMemoryAcademicGroupRepository();val repository=InMemoryClassroomRepository()
        lateinit var operator:QaMfaOperator;lateinit var service:ClassroomService
        val teacher=PlatformAccount("teacher","Actual Teacher","teacher@example.test","test-hash",setOf(UserRole.TEACHER),institutionId="campus",emailVerifiedAt=now)
        val student=PlatformAccount("student","Actual Student","student@example.test","test-hash",setOf(UserRole.STUDENT),institutionId="campus",emailVerifiedAt=now)
        suspend fun initialize() {
            operator=QaMfaOperator.create(accounts,UserSummary("operator","QA Operator",roles=setOf(UserRole.ADMIN),institutionId="campus"),clock)
            accounts.create(teacher);accounts.create(student);accounts.create(teacher.copy(id="outside",email="outside@example.test",institutionId="other"))
            groups.create(AcademicGroupRecord("6A","Actual Group",true,now,"campus","period",now,now.plusSeconds(3600)))
            groups.create(AcademicGroupRecord("OTHER","Other Group",true,now,"other","period",now,now.plusSeconds(3600)))
            service=ClassroomService(repository,groups,clock,accounts=accounts,mfa=operator.mfa)
        }
        fun request()=CreateClassroomRequest("QA Class",groupId="6A",groupName="Untrusted Group",teacherId="teacher",teacherDisplayName="Untrusted Teacher",operationId="assign")
        suspend fun signed(request:CreateClassroomRequest=request())=request.copy(proof=operator.proof(StepUpAction.MANAGE_ACADEMICS,request.groupId,request.operationId!!,PrivilegedCommandDigests.classroomAssignment(request.groupId,request)))
    }
    @Test fun `actual verified teacher and group are assigned once with scoped MFA rather than client names`()=runBlocking<Unit> {
        val f=Fixture();f.initialize();val actor=f.operator.actor.toUserSummary()
        assertFailsWith<ApiException.Forbidden> { f.service.create(actor,f.request()) }
        val command=f.signed();val results=coroutineScope { (1..12).map { async { f.service.create(actor,command) } }.awaitAll() }
        assertEquals(1,results.map { it.id }.toSet().size);assertEquals(1,f.repository.listClassrooms().size)
        val classroom=results.first();assertEquals("Actual Teacher",classroom.teacherDisplayName);assertEquals("Actual Group",classroom.groupName)
        assertTrue(f.service.requireCanPublish(f.teacher.toUserSummary(),classroom.id).canManage)
        assertFailsWith<ApiException.Forbidden> { f.service.create(actor,command.copy(teacherId="outside")) }
    }
    @Test fun `admin cannot read publish enroll invite or revoke classrooms belonging to another institution`()=runBlocking<Unit> {
        val f=Fixture();f.initialize();val actor=f.operator.actor.toUserSummary()
        val foreign=NativeClassroom("foreign","Other",null,null,"OTHER","Other","outside","Other",ClassroomStatus.ACTIVE,now)
        f.repository.createClassroom(foreign)
        assertTrue(f.service.listFor(actor).isEmpty())
        assertFailsWith<ApiException.Forbidden> { f.service.requireCanRead(actor,"foreign") }
        assertFailsWith<ApiException.Forbidden> { f.service.requireCanPublish(actor,"foreign") }
        assertFailsWith<ApiException.Forbidden> { f.service.createInvite(actor,"foreign",CreateClassInviteRequest()) }
        assertFailsWith<ApiException.Forbidden> { f.service.revokeInvite(actor,"foreign","missing") }
        assertFailsWith<ApiException.Forbidden> { f.service.create(actor,f.signed(f.request().copy(groupId="OTHER"))) }
    }
    @Test fun `pending unverified revoked or wrong institution teacher never gets a classroom assignment`()=runBlocking<Unit> {
        for(target in listOf("outside","missing","pending","unverified")) {
            val f=Fixture();f.initialize()
            f.accounts.create(f.teacher.copy(id="pending",email="pending@example.test",roles=emptySet(),accountStatus=AccountStatus.PENDING_APPROVAL))
            f.accounts.create(f.teacher.copy(id="unverified",email="unverified@example.test",emailVerifiedAt=null))
            assertFailsWith<ApiException.Validation> { f.service.create(f.operator.actor.toUserSummary(),f.signed(f.request().copy(teacherId=target))) }
            assertTrue(f.repository.listClassrooms().isEmpty())
        }
    }
    @Test fun `classroom invitation cannot enroll an account from another institution`()=runBlocking<Unit> {
        val f=Fixture();f.initialize();val actor=f.operator.actor.toUserSummary()
        val classroom=f.service.create(actor,f.signed());val invite=f.service.createInvite(actor,classroom.id,CreateClassInviteRequest(maxUses=1))
        val outsider=f.student.copy(id="outsider",email="outsider@example.test",institutionId="other");f.accounts.create(outsider)
        assertFailsWith<ApiException.Forbidden> { f.service.join(outsider.toUserSummary(),invite.code) }
        assertEquals(0,f.repository.findInviteByHash(orgHash(invite.code))?.uses)
        assertEquals(classroom.id,f.service.join(f.student.toUserSummary(),invite.code).classroom.id)
    }
    private fun orgHash(code:String)=java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(code.toByteArray()))
}
