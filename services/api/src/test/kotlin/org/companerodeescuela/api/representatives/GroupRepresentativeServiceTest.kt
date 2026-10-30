package org.companerodeescuela.api.representatives

import org.companerodeescuela.api.test.ScopedAcademicFixtures
import org.companerodeescuela.api.tutoring.TutorAssignmentRecord
import org.companerodeescuela.api.academic.groups.AcademicGroupMembershipRecord
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.companerodeescuela.api.academic.groups.AcademicGroupRecord
import org.companerodeescuela.api.academic.groups.AcademicGroupRepository
import org.companerodeescuela.api.academic.groups.InMemoryAcademicGroupRepository
import org.companerodeescuela.api.auth.InMemoryPlatformAccountRepository
import org.companerodeescuela.api.auth.PlatformAccount
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.tutoring.InMemoryTutorAssignmentRepository
import org.companerodeescuela.api.tutoring.TutorAssignmentService
import org.companerodeescuela.shared.contracts.AppointRepresentativeRequest
import org.companerodeescuela.shared.contracts.CreateTutorAssignmentRequest
import org.companerodeescuela.shared.contracts.RepresentativePosition
import org.companerodeescuela.shared.contracts.RepresentativeStatus
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GroupRepresentativeServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-09T12:00:00Z"), ZoneOffset.UTC)
    private lateinit var groupRepository: AcademicGroupRepository
    private lateinit var tutoringService: TutorAssignmentService
    private lateinit var repository: GroupRepresentativeRepository
    private lateinit var service: GroupRepresentativeService

    private val adminActor = UserSummary("admin-1", "Admin", roles = setOf(UserRole.ADMIN))
    private val tutorRoles = setOf(UserRole.TUTOR, UserRole.TEACHER)

    @BeforeEach
    fun setUp() = runBlocking<Unit> {
        groupRepository = InMemoryAcademicGroupRepository()
        groupRepository.create(ScopedAcademicFixtures.group("G-101", "101-A"))

        val tutor1=UserSummary("tutor-1","Tutor 1",roles=tutorRoles,institutionId=ScopedAcademicFixtures.INSTITUTION)
        val tutor2=UserSummary("tutor-2","Tutor 2",roles=tutorRoles,institutionId=ScopedAcademicFixtures.INSTITUTION)
        val accounts=ScopedAcademicFixtures.accounts(tutor1,tutor2)
        for(id in listOf("student-1","student-2")) {
            val student=UserSummary(id,id,roles=setOf(UserRole.STUDENT),institutionId=ScopedAcademicFixtures.INSTITUTION)
            accounts.create(ScopedAcademicFixtures.account(student))
            groupRepository.assign(AcademicGroupMembershipRecord("G-101",id,Instant.EPOCH))
        }
        val assignments=InMemoryTutorAssignmentRepository()
        assignments.create(ScopedAcademicFixtures.assignment("qa-assignment",tutor1.id,"G-101"))
        tutoringService=TutorAssignmentService(assignments,groupRepository,clock,accounts)
        repository = InMemoryGroupRepresentativeRepository()
        service = GroupRepresentativeService(repository, tutoringService, clock)
    }

    @Test
    fun `tutor can appoint chief and student can accept`() = runBlocking<Unit> {
        val appointment = service.appointRepresentative(
            actor = UserSummary("tutor-1","Tutor 1",roles=tutorRoles,institutionId=ScopedAcademicFixtures.INSTITUTION),
            groupId = "G-101",
            groupName = "101-A",
            request = AppointRepresentativeRequest("student-1", RepresentativePosition.CHIEF),
        )

        assertEquals("G-101", appointment.academicGroupId)
        assertEquals("student-1", appointment.studentUserId)
        assertEquals(RepresentativePosition.CHIEF, appointment.position)
        assertEquals(RepresentativeStatus.PENDING, appointment.status)

        val accepted = service.acceptAppointment("student-1", appointment.id)
        assertEquals(RepresentativeStatus.ACTIVE, accepted.status)
        assertNotNull(accepted.acceptedAtEpochSeconds)

        val activeList = service.activeAssignmentsForStudent("student-1")
        assertEquals(1, activeList.size)
        assertEquals("G-101", activeList.first().academicGroupId)
    }

    @Test
    fun `unauthorized tutor cannot appoint representative`() = runBlocking<Unit> {
        assertThrows<ApiException.Forbidden> {
            service.appointRepresentative(
                actor = UserSummary("tutor-2","Tutor 2",roles=tutorRoles,institutionId=ScopedAcademicFixtures.INSTITUTION),
                groupId = "G-101",
                groupName = "101-A",
                request = AppointRepresentativeRequest("student-1", RepresentativePosition.CHIEF),
            )
        }
    }

    @Test
    fun `cannot appoint duplicate active position in same group`() = runBlocking<Unit> {
        val first = service.appointRepresentative(
            actor = UserSummary("tutor-1","Tutor 1",roles=tutorRoles,institutionId=ScopedAcademicFixtures.INSTITUTION),
            groupId = "G-101",
            groupName = "101-A",
            request = AppointRepresentativeRequest("student-1", RepresentativePosition.CHIEF),
        )
        service.acceptAppointment("student-1", first.id)

        assertThrows<ApiException.Conflict> {
            service.appointRepresentative(
                actor = UserSummary("tutor-1","Tutor 1",roles=tutorRoles,institutionId=ScopedAcademicFixtures.INSTITUTION),
                groupId = "G-101",
                groupName = "101-A",
                request = AppointRepresentativeRequest("student-2", RepresentativePosition.CHIEF),
            )
        }
    }

    @Test
    fun `revoking appointment removes active status`() = runBlocking<Unit> {
        val appointment = service.appointRepresentative(
            actor = UserSummary("tutor-1","Tutor 1",roles=tutorRoles,institutionId=ScopedAcademicFixtures.INSTITUTION),
            groupId = "G-101",
            groupName = "101-A",
            request = AppointRepresentativeRequest("student-1", RepresentativePosition.CHIEF),
        )
        service.acceptAppointment("student-1", appointment.id)

        val revoked = service.revokeAppointment(
            actor = UserSummary("tutor-1","Tutor 1",roles=tutorRoles,institutionId=ScopedAcademicFixtures.INSTITUTION),
            assignmentId = appointment.id,
        )

        assertEquals(RepresentativeStatus.REVOKED, revoked.status)
        assertEquals("tutor-1", revoked.revokedBy)

        val active = service.activeAssignmentsForStudent("student-1")
        assertEquals(0, active.size)
    }

    @Test
    fun `activeAndPendingAssignmentsForStudent excludes revoked and declined but preserves history`() = runBlocking<Unit> {
        val appointment = service.appointRepresentative(
            actor = UserSummary("tutor-1", "Tutor 1", roles = tutorRoles, institutionId = ScopedAcademicFixtures.INSTITUTION),
            groupId = "G-101",
            groupName = "101-A",
            request = AppointRepresentativeRequest("student-1", RepresentativePosition.CHIEF),
        )
        // While pending:
        val pendingActive = service.activeAndPendingAssignmentsForStudent("student-1")
        assertEquals(1, pendingActive.size)
        assertEquals(RepresentativeStatus.PENDING, pendingActive.first().status)

        // Decline it:
        service.declineAppointment("student-1", appointment.id)

        // activeAndPending must now be empty:
        val afterDecline = service.activeAndPendingAssignmentsForStudent("student-1")
        assertEquals(0, afterDecline.size)

        // But full history still records the assignment:
        val fullHistory = service.getStudentAssignments("student-1")
        assertEquals(1, fullHistory.size)
        assertEquals(RepresentativeStatus.DECLINED, fullHistory.first().status)
    }
}
