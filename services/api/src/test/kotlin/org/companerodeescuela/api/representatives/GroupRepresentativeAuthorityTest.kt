package org.companerodeescuela.api.representatives

import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import org.companerodeescuela.api.auth.*
import org.companerodeescuela.api.academic.groups.*
import org.companerodeescuela.api.tutoring.*
import org.companerodeescuela.api.test.ScopedAcademicFixtures
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.*

class GroupRepresentativeAuthorityTest {
    private suspend fun fixture(actors:List<UserSummary>):Triple<GroupRepresentativeService,InMemoryGroupRepresentativeRepository,InMemoryAcademicGroupRepository> {
        val accounts=ScopedAcademicFixtures.accounts(*actors.toTypedArray());val groups=InMemoryAcademicGroupRepository()
        groups.create(ScopedAcademicFixtures.group("6A"));groups.create(ScopedAcademicFixtures.group("OTHER").copy(institutionId="other"))
        val assignments=InMemoryTutorAssignmentRepository()
        for(actor in actors.filter { UserRole.TUTOR in it.roles }) assignments.create(ScopedAcademicFixtures.assignment("assignment-"+actor.id,actor.id,"6A"))
        val repo=InMemoryGroupRepresentativeRepository()
        return Triple(GroupRepresentativeService(repo,TutorAssignmentService(assignments,groups,accounts=accounts)),repo,groups)
    }
    private fun actor(id:String,role:UserRole)=UserSummary(id,id,roles=setOf(role),institutionId=ScopedAcademicFixtures.INSTITUTION)
    @Test fun `representative overview requires actual member tutor assignment or scoped administration`()=runTest {
        val outsiders=listOf(actor("outsider",UserRole.STUDENT),actor("teacher",UserRole.TEACHER),actor("visitor",UserRole.WORKSHOP_PARTICIPANT))
        val (service,_,_)=fixture(outsiders)
        for(actor in outsiders) assertFailsWith<ApiException.Forbidden> { service.getGroupOverview(actor,"6A","6A") }
    }
    @Test fun `student member sees public appointments and only their own pending invitation`()=runTest {
        val student=actor("student",UserRole.STUDENT);val tutor=actor("tutor",UserRole.TUTOR)
        val (service,repo,groups)=fixture(listOf(student,tutor,actor("other-student",UserRole.STUDENT)))
        groups.assign(AcademicGroupMembershipRecord("6A",student.id,Instant.EPOCH))
        groups.assign(AcademicGroupMembershipRecord("6A","other-student",Instant.EPOCH))
        val invitation=repo.save(GroupRepresentativeAssignment("invitation",institutionId=ScopedAcademicFixtures.INSTITUTION,academicTermId=ScopedAcademicFixtures.PERIOD,expiresAtEpochSeconds=ScopedAcademicFixtures.END.epochSecond,groupScopeRevision=0,academicGroupId="6A",academicGroupName="6A",studentUserId="other-student",position=RepresentativePosition.CHIEF,appointedBy=tutor.id,appointedAtEpochSeconds=Instant.now().epochSecond))
        assertTrue(service.getGroupOverview(student,"6A","6A").pendingInvitations.isEmpty())
        assertEquals(listOf(invitation.id),service.getGroupOverview(tutor,"6A","6A").pendingInvitations.map { it.id })
    }
    @Test fun `institutional admin cannot appoint or list another institution using legacy administrative shortcut`()=runTest {
        val admin=actor("admin",UserRole.ADMIN)
        val (service,_,_)=fixture(listOf(admin))
        assertFailsWith<ApiException.Forbidden> { service.getGroupOverview(admin,"OTHER","Other") }
        assertFailsWith<ApiException.Forbidden> { service.appointRepresentative(admin,"OTHER","Other",AppointRepresentativeRequest("student",RepresentativePosition.CHIEF)) }
    }
}
