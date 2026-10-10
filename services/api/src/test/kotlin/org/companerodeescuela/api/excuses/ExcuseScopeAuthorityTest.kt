package org.companerodeescuela.api.excuses

import java.time.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import org.companerodeescuela.api.auth.*
import org.companerodeescuela.api.academic.groups.*
import org.companerodeescuela.api.tutoring.*
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.test.ScopedAcademicFixtures
import org.companerodeescuela.shared.contracts.*

class ExcuseScopeAuthorityTest {
    @Test fun `institutional administrator cannot list or review another institution excuses`()=runTest {
        val actor=UserSummary("admin","Admin",roles=setOf(UserRole.ADMIN),institutionId="campus")
        val accounts=ScopedAcademicFixtures.accounts(actor)
        val groups=InMemoryAcademicGroupRepository();groups.create(ScopedAcademicFixtures.group("6A").copy(institutionId="campus"));groups.create(ScopedAcademicFixtures.group("6B").copy(institutionId="other"))
        val repo=InMemoryExcuseRepository()
        for(group in listOf("6A","6B")) repo.create(ExcuseRecord(group,"student",group,"2026-10-10",
            "Private reason",emptyList(),ExcuseStatus.PENDING,Instant.EPOCH,
            institutionId=if(group=="6A") "campus" else "other",
            periodId=ScopedAcademicFixtures.PERIOD,groupScopeRevision=0))
        val tutoring=TutorAssignmentService(InMemoryTutorAssignmentRepository(),groups,accounts=accounts)
        val service=ExcuseService(repo,tutoring,groupRepository=groups)
        assertEquals(listOf("6A"),service.listFor(actor).map { it.academicGroupId })
        assertFailsWith<ApiException.Forbidden> { service.review(actor,"6B",ReviewExcuseRequest(ExcuseStatus.APPROVED)) }
        assertEquals(ExcuseStatus.PENDING,repo.find("6B")?.status)
        assertEquals(ExcuseStatus.APPROVED,service.review(actor,"6A",ReviewExcuseRequest(ExcuseStatus.APPROVED)).status)
    }
    @Test fun `stale membership cannot let a student submit excuses across institution`()=runTest {
        val actor=UserSummary("student","Student",roles=setOf(UserRole.STUDENT),institutionId="campus")
        val accounts=ScopedAcademicFixtures.accounts(actor)
        val groups=InMemoryAcademicGroupRepository();groups.create(ScopedAcademicFixtures.group("6B").copy(institutionId="other"));groups.assign(AcademicGroupMembershipRecord("6B",actor.id,Instant.EPOCH))
        val repo=InMemoryExcuseRepository();val tutoring=TutorAssignmentService(InMemoryTutorAssignmentRepository(),groups,accounts=accounts)
        val service=ExcuseService(repo,tutoring,groupRepository=groups)
        assertFailsWith<ApiException.Forbidden> { service.submit(actor,SubmitExcuseRequest("6B","2026-10-10","Wrong institution")) }
        assertTrue(repo.listAll().isEmpty())
    }
    @Test fun `historical excuses without explicit scope are never returned or reviewable`()=runTest {
        val actor=UserSummary("admin","Admin",roles=setOf(UserRole.ADMIN),institutionId="campus")
        val accounts=ScopedAcademicFixtures.accounts(actor)
        val groups=InMemoryAcademicGroupRepository()
        groups.create(ScopedAcademicFixtures.group("6A").copy(institutionId="campus"))
        val repo=InMemoryExcuseRepository()
        repo.create(ExcuseRecord("legacy","student","6A","2026-10-10","Old private reason",emptyList(),
            ExcuseStatus.PENDING,Instant.EPOCH))
        val service=ExcuseService(repo,TutorAssignmentService(InMemoryTutorAssignmentRepository(),groups,accounts=accounts),
            groupRepository=groups)
        assertTrue(service.listFor(actor).isEmpty())
        assertFailsWith<ApiException.Forbidden> {
            service.review(actor,"legacy",ReviewExcuseRequest(ExcuseStatus.APPROVED))
        }
        assertEquals(ExcuseStatus.PENDING,repo.find("legacy")?.status)
    }

}
