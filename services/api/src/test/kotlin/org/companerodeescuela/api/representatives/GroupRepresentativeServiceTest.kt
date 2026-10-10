package org.companerodeescuela.api.representatives

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
    fun setUp() = runBlocking {
        groupRepository = InMemoryAcademicGroupRepository()
        groupRepository.create(AcademicGroupRecord("G-101", "101-A", active = true, createdAt = Instant.now()))

        val accounts = InMemoryPlatformAccountRepository()
        accounts.create(PlatformAccount("tutor-1", "Tutor 1", "tutor1@escuela.edu", "", roles = setOf(UserRole.TUTOR)))
        accounts.create(PlatformAccount("tutor-2", "Tutor 2", "tutor2@escuela.edu", "", roles = setOf(UserRole.TUTOR)))

        tutoringService = TutorAssignmentService(
            repository = InMemoryTutorAssignmentRepository(),
            groupRepository = groupRepository,
            clock = clock,
            accounts = accounts,
        )
        tutoringService.create(
            actor = adminActor,
            request = CreateTutorAssignmentRequest("tutor-1", "G-101"),
        )
        repository = InMemoryGroupRepresentativeRepository()
        service = GroupRepresentativeService(repository, tutoringService, clock)
    }

    @Test
    fun `tutor can appoint chief and student can accept`() = runBlocking {
        val appointment = service.appointRepresentative(
            actorUserId = "tutor-1",
            actorRoles = tutorRoles,
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
    fun `unauthorized tutor cannot appoint representative`() = runBlocking {
        assertThrows<ApiException.Forbidden> {
            service.appointRepresentative(
                actorUserId = "tutor-2",
                actorRoles = tutorRoles,
                groupId = "G-101",
                groupName = "101-A",
                request = AppointRepresentativeRequest("student-1", RepresentativePosition.CHIEF),
            )
        }
    }

    @Test
    fun `cannot appoint duplicate active position in same group`() = runBlocking {
        val first = service.appointRepresentative(
            actorUserId = "tutor-1",
            actorRoles = tutorRoles,
            groupId = "G-101",
            groupName = "101-A",
            request = AppointRepresentativeRequest("student-1", RepresentativePosition.CHIEF),
        )
        service.acceptAppointment("student-1", first.id)

        assertThrows<ApiException.Conflict> {
            service.appointRepresentative(
                actorUserId = "tutor-1",
                actorRoles = tutorRoles,
                groupId = "G-101",
                groupName = "101-A",
                request = AppointRepresentativeRequest("student-2", RepresentativePosition.CHIEF),
            )
        }
    }

    @Test
    fun `revoking appointment removes active status`() = runBlocking {
        val appointment = service.appointRepresentative(
            actorUserId = "tutor-1",
            actorRoles = tutorRoles,
            groupId = "G-101",
            groupName = "101-A",
            request = AppointRepresentativeRequest("student-1", RepresentativePosition.CHIEF),
        )
        service.acceptAppointment("student-1", appointment.id)

        val revoked = service.revokeAppointment(
            actorUserId = "tutor-1",
            actorRoles = tutorRoles,
            assignmentId = appointment.id,
        )

        assertEquals(RepresentativeStatus.REVOKED, revoked.status)
        assertEquals("tutor-1", revoked.revokedBy)

        val active = service.activeAssignmentsForStudent("student-1")
        assertEquals(0, active.size)
    }
}
