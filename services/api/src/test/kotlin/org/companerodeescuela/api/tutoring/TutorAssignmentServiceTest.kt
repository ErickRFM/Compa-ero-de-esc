package org.companerodeescuela.api.tutoring

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.academic.groups.AcademicGroupRecord
import org.companerodeescuela.api.academic.groups.AcademicGroupMembershipRecord
import org.companerodeescuela.api.academic.groups.InMemoryAcademicGroupRepository
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.auth.InMemoryPlatformAccountRepository
import org.companerodeescuela.api.auth.PlatformAccount
import org.companerodeescuela.shared.contracts.CreateTutorAssignmentRequest
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary

class TutorAssignmentServiceTest {
    @Test
    fun adminAssignsTutorAndTutorSeesOnlyAssignedScope() = runTest {
        val groups = InMemoryAcademicGroupRepository()
        groups.create(AcademicGroupRecord("6A", "6A", true, Instant.EPOCH))
        groups.create(AcademicGroupRecord("6B", "6B", true, Instant.EPOCH))
        val accounts = InMemoryPlatformAccountRepository()
        accounts.create(PlatformAccount(TUTOR.id, TUTOR.displayName, "tutor@example.edu", "test-only", TUTOR.roles))
        accounts.create(PlatformAccount("student-1", "Ana", "ana@example.edu", "test-only", setOf(UserRole.STUDENT)))
        groups.assign(AcademicGroupMembershipRecord("6A", "student-1", Instant.EPOCH))
        val service = TutorAssignmentService(
            repository = InMemoryTutorAssignmentRepository(),
            groupRepository = groups,
            clock = Clock.fixed(Instant.parse("2026-10-06T18:30:00Z"), ZoneOffset.UTC),
            accounts = accounts,
        )

        service.create(ADMIN, CreateTutorAssignmentRequest(TUTOR.id, "6A"))

        assertEquals(listOf("6A"), service.scopeFor(TUTOR).groups.map { it.id })
        assertEquals(1, service.listFor(TUTOR).size)
        val roster = service.studentsForGroup(TUTOR, "6A")
        assertEquals(listOf("Ana"), roster.map { it.displayName })
        assertFailsWith<ApiException.Forbidden> { service.studentsForGroup(TUTOR, "6B") }
        service.requireCanAccessGroup(TUTOR, "6A")
        assertFailsWith<ApiException.Forbidden> {
            service.requireCanAccessGroup(TUTOR, "6B")
        }
        val assignment = service.listFor(TUTOR).single()
        val revoked = service.revoke(ADMIN, assignment.id)
        assertEquals(false, revoked.active)
        assertEquals(ADMIN.id, revoked.revokedBy)
        assertEquals(emptyList(), service.scopeFor(TUTOR).groups)
        assertFailsWith<ApiException.Forbidden> {
            service.requireCanAccessGroup(TUTOR, "6A")
        }
        assertEquals(revoked, service.revoke(ADMIN, assignment.id))
    }

    @Test
    fun adminRejectsUnknownOrNonTutorIdentity() = runTest {
        val groups = InMemoryAcademicGroupRepository()
        groups.create(AcademicGroupRecord("6A", "6A", true, Instant.EPOCH))
        val accounts = InMemoryPlatformAccountRepository()
        accounts.create(PlatformAccount(TEACHER.id, TEACHER.displayName, "teacher@example.edu", "test-only", TEACHER.roles))
        val service = TutorAssignmentService(InMemoryTutorAssignmentRepository(), groups, accounts = accounts)
        assertFailsWith<ApiException.NotFound> {
            service.create(ADMIN, CreateTutorAssignmentRequest("unknown", "6A"))
        }
        assertFailsWith<ApiException.Validation> {
            service.create(ADMIN, CreateTutorAssignmentRequest(TEACHER.id, "6A"))
        }
    }

    @Test
    fun teacherWithoutTutorRoleCannotReadTutorScope() = runTest {
        val service = TutorAssignmentService(
            repository = InMemoryTutorAssignmentRepository(),
            groupRepository = InMemoryAcademicGroupRepository(),
        )

        assertFailsWith<ApiException.Forbidden> {
            service.scopeFor(TEACHER)
        }
    }

    @Test
    fun nonAdminCannotAssignTutor() = runTest {
        val groups = InMemoryAcademicGroupRepository()
        groups.create(AcademicGroupRecord("6A", "6A", true, Instant.EPOCH))
        val service = TutorAssignmentService(
            repository = InMemoryTutorAssignmentRepository(),
            groupRepository = groups,
        )

        assertFailsWith<ApiException.Forbidden> {
            service.create(TUTOR, CreateTutorAssignmentRequest(TUTOR.id, "6A"))
        }
    }

    private companion object {
        val ADMIN = UserSummary(
            id = "admin-1",
            displayName = "Control escolar",
            roles = setOf(UserRole.ADMIN),
        )
        val TUTOR = UserSummary(
            id = "teacher-tutor-1",
            displayName = "Mtra. Elena",
            roles = setOf(UserRole.TEACHER, UserRole.TUTOR),
        )
        val TEACHER = UserSummary(
            id = "teacher-1",
            displayName = "Mtro. Luis",
            roles = setOf(UserRole.TEACHER),
        )
    }
}
