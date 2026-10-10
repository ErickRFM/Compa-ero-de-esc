package org.companerodeescuela.api.tutoring

import java.time.*
import kotlin.test.*
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.academic.groups.*
import org.companerodeescuela.api.auth.*
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.*

class TutorAssignmentScopeTest {
    private val now=Instant.parse("2026-10-10T07:00:00Z")
    private fun tutor()=PlatformAccount("tutor","QA Tutor","tutor@example.test","test-hash",setOf(UserRole.TEACHER,UserRole.TUTOR),institutionId="campus",emailVerifiedAt=now,roleGenerations=mapOf(UserRole.TEACHER to 1,UserRole.TUTOR to 2))
    private fun group()=AcademicGroupRecord("6A","6A",true,now,"campus","period",now.minusSeconds(10),now.plusSeconds(600))
    private fun assignment()=TutorAssignmentRecord("assignment","tutor","6A",true,"admin",now,institutionId="campus",periodId="period",startsAt=now.minusSeconds(10),endsAt=now.plusSeconds(600),tutorRoleGeneration=2)
    private suspend fun fixture(account:PlatformAccount=tutor(),group:AcademicGroupRecord=group(),assignment:TutorAssignmentRecord=assignment()):Pair<TutorAssignmentService,InMemoryPlatformAccountRepository> {
        val accounts=InMemoryPlatformAccountRepository();accounts.create(account)
        val groups=InMemoryAcademicGroupRepository();groups.create(group)
        val assignments=InMemoryTutorAssignmentRepository();assignments.create(assignment)
        return TutorAssignmentService(assignments,groups,Clock.fixed(now,ZoneOffset.UTC),accounts) to accounts
    }
    @Test fun `valid teacher tutor retains only currently authorized group`()=runTest {
        val (service,_)=fixture();service.requireTutorAssignmentForGroup(tutor().toUserSummary(),"6A")
        assertEquals(listOf("6A"),service.scopeFor(tutor().toUserSummary()).groups.map { it.id })
        assertFailsWith<ApiException.Forbidden> { service.requireTutorAssignmentForGroup(tutor().toUserSummary(),"6B") }
    }
    @Test fun `assignment institution period future expired and legacy metadata cannot confer access`()=runTest {
        for(record in listOf(assignment().copy(institutionId="other"),assignment().copy(periodId="old"),assignment().copy(startsAt=now.plusSeconds(1)),assignment().copy(endsAt=now),assignment().copy(tutorRoleGeneration=1),assignment().copy(institutionId=null,periodId=null,startsAt=null,endsAt=null,tutorRoleGeneration=-1))) {
            val (service,_)=fixture(assignment=record)
            assertFailsWith<ApiException.Forbidden> { service.requireTutorAssignmentForGroup(tutor().toUserSummary(),"6A") }
            assertTrue(service.scopeFor(tutor().toUserSummary()).groups.isEmpty())
        }
        for(group in listOf(group().copy(institutionId=null),group().copy(periodId=null),group().copy(active=false),group().copy(endsAt=now))) {
            val (service,_)=fixture(group=group)
            assertFailsWith<ApiException.Forbidden> { service.requireTutorAssignmentForGroup(tutor().toUserSummary(),"6A") }
        }
    }
    @Test fun `live revocation role change and tutor regrant never reactivate old assignment`()=runTest {
        val original=tutor();val (service,repo)=fixture()
        val removed=assertNotNull(repo.changeIdentity("tutor",0,AccountIdentityChange("remove","owner",AccountStatus.ACTIVE,setOf(UserRole.TEACHER),"campus",now)))
        assertFailsWith<ApiException.Unauthorized> { service.requireTutorAssignmentForGroup(original.toUserSummary(),"6A") }
        val restored=assertNotNull(repo.changeIdentity("tutor",removed.authRevision,AccountIdentityChange("regrant","owner",AccountStatus.ACTIVE,original.roles,"campus",now)))
        assertFailsWith<ApiException.Forbidden> { service.requireTutorAssignmentForGroup(restored.toUserSummary(),"6A") }
        val suspended=assertNotNull(repo.changeIdentity("tutor",restored.authRevision,AccountIdentityChange("suspend","owner",AccountStatus.SUSPENDED,original.roles,"campus",now)))
        assertFailsWith<ApiException.Unauthorized> { service.requireTutorAssignmentForGroup(suspended.toUserSummary(),"6A") }
    }
    @Test fun `administrative role cannot use the previous global group bypass or private tutor notes`()=runTest {
        val admin=tutor().copy(id="admin",email="admin@example.test",roles=setOf(UserRole.ADMIN),adminCapabilities=setOf(AdminCapability.MANAGE_ACADEMICS))
        val (service,_)=fixture(account=admin,group=group().copy(institutionId="other"))
        assertFailsWith<ApiException.Forbidden> { service.requireCanAccessGroup(admin.toUserSummary(),"6A") }
        assertFailsWith<ApiException.Forbidden> { service.requireTutorAssignmentForGroup(admin.toUserSummary(),"6A") }
    }
}
