package org.companerodeescuela.api.representatives

import java.time.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import org.companerodeescuela.api.auth.*
import org.companerodeescuela.api.academic.groups.*
import org.companerodeescuela.api.tutoring.*
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.test.ScopedAcademicFixtures
import org.companerodeescuela.shared.contracts.*

class GroupRepresentativeExpiryScopeTest {
    private val now=Instant.parse("2026-10-10T07:00:00Z")
    private inner class Fixture {
        val tutor=UserSummary("tutor","Actual Tutor",roles=setOf(UserRole.TUTOR),institutionId="qa-campus")
        val student=UserSummary("student","Actual Student",roles=setOf(UserRole.STUDENT),institutionId="qa-campus")
        val groups=InMemoryAcademicGroupRepository();val repo=InMemoryGroupRepresentativeRepository()
        lateinit var service:GroupRepresentativeService
        suspend fun initialize() {
            val accounts=ScopedAcademicFixtures.accounts(tutor,student)
            groups.create(ScopedAcademicFixtures.group("6A","Actual Group"));groups.assign(AcademicGroupMembershipRecord("6A",student.id,now))
            val assignments=InMemoryTutorAssignmentRepository();assignments.create(ScopedAcademicFixtures.assignment("assignment",tutor.id,"6A"))
            service=GroupRepresentativeService(repo,TutorAssignmentService(assignments,groups,Clock.fixed(now,ZoneOffset.UTC),accounts),Clock.fixed(now,ZoneOffset.UTC))
        }
        suspend fun appoint()=service.appointRepresentative(tutor,"6A","Untrusted Label",AppointRepresentativeRequest(student.id,RepresentativePosition.CHIEF))
    }
    @Test fun `appointment stores actual institution period student name and bounded expiry`()=runTest {
        val f=Fixture();f.initialize();val appointment=f.appoint()
        assertEquals("qa-campus",appointment.institutionId);assertEquals("qa-period",appointment.academicTermId)
        assertEquals("Actual Student",appointment.studentDisplayName);assertEquals("Actual Group",appointment.academicGroupName)
        assertEquals(ScopedAcademicFixtures.END.epochSecond,appointment.expiresAtEpochSeconds)
    }
    @Test fun `period changes and restored labels cannot reactivate accepted representative authority`()=runTest {
        val f=Fixture();f.initialize();val appointment=f.appoint();f.service.acceptAppointment(f.student.id,appointment.id)
        assertEquals(1,f.service.activeAssignmentsForStudent(f.student.id).size)
        val change=AcademicGroupScopeChange("next","owner","qa-campus","next",Instant.EPOCH,ScopedAcademicFixtures.END,now,"a".repeat(64))
        assertNotNull(f.groups.changeScope("6A",0,change))
        assertNotNull(f.groups.changeScope("6A",1,change.copy(operationId="restore",periodId="qa-period",payloadDigest="b".repeat(64))))
        assertTrue(f.service.activeAssignmentsForStudent(f.student.id).isEmpty())
    }
    @Test fun `unknown legacy or expired appointment cannot grant representatives channel access`()=runTest {
        val f=Fixture();f.initialize();val legacy=GroupRepresentativeAssignment("legacy",academicGroupId="6A",academicGroupName="6A",studentUserId=f.student.id,position=RepresentativePosition.CHIEF,status=RepresentativeStatus.ACTIVE,appointedBy=f.tutor.id,appointedAtEpochSeconds=now.epochSecond)
        f.repo.save(legacy)
        assertTrue(f.service.activeAssignmentsForStudent(f.student.id).isEmpty())
        assertFailsWith<ApiException.Validation> { f.service.appointRepresentative(f.tutor,"6A","6A",AppointRepresentativeRequest(f.student.id,RepresentativePosition.DEPUTY,"invented-period")) }
    }
}
