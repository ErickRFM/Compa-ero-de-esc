package org.companerodeescuela.api.excuses

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
import org.companerodeescuela.api.tutoring.InMemoryTutorAssignmentRepository
import org.companerodeescuela.api.tutoring.TutorAssignmentRecord
import org.companerodeescuela.api.tutoring.TutorAssignmentService
import org.companerodeescuela.shared.contracts.ExcuseStatus
import org.companerodeescuela.shared.contracts.ReviewExcuseRequest
import org.companerodeescuela.shared.contracts.SubmitExcuseRequest
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary

class ExcuseServiceTest {
    @Test
    fun studentSubmitsAndAssignedTutorApproves() = runTest {
        val setup = setup()
        val submitted = setup.service.submit(
            STUDENT,
            SubmitExcuseRequest(
                academicGroupId = "6A",
                attendanceDateIso = "2026-10-06",
                reason = "Consulta médica con comprobante",
            ),
        )

        assertEquals(ExcuseStatus.PENDING, submitted.status)
        assertEquals(1, setup.service.listFor(TUTOR).size)

        val reviewed = setup.service.review(
            TUTOR,
            submitted.id,
            ReviewExcuseRequest(ExcuseStatus.APPROVED, "Justificación válida"),
        )

        assertEquals(ExcuseStatus.APPROVED, reviewed.status)
        assertEquals(TUTOR.id, reviewed.reviewedBy)
    }

    @Test
    fun tutorCannotReviewExcuseFromAnotherGroup() = runTest {
        val setup = setup()
        val submitted = setup.service.submit(
            OTHER_STUDENT,
            SubmitExcuseRequest(
                academicGroupId = "6B",
                attendanceDateIso = "2026-10-06",
                reason = "Motivo suficientemente largo",
            ),
        )

        assertFailsWith<ApiException.Forbidden> {
            setup.service.review(
                TUTOR,
                submitted.id,
                ReviewExcuseRequest(ExcuseStatus.REJECTED, "Fuera de alcance"),
            )
        }
    }

    @Test
    fun submitRejectsWrongGroupMalformedDateAndUnverifiedFiles() = runTest {
        val service = setup().service
        assertFailsWith<ApiException.Forbidden> {
            service.submit(STUDENT, SubmitExcuseRequest("6B", "2026-10-06", "Wrong group"))
        }
        assertFailsWith<ApiException.Validation> {
            service.submit(STUDENT, SubmitExcuseRequest("6A", "2026-02-31", "Bad calendar date"))
        }
        assertFailsWith<ApiException.Validation> {
            service.submit(STUDENT, SubmitExcuseRequest("6A", "2026-10-06", "Valid reason", listOf("foreign-file")))
        }
    }

    @Test
    fun staleReviewIsRejectedByOptimisticLock() = runTest {
        val repository = InMemoryExcuseRepository()
        val initial = ExcuseRecord("e1", STUDENT.id, "6A", "2026-10-06", "Test reason", emptyList(), ExcuseStatus.PENDING, Instant.EPOCH)
        repository.create(initial)
        val first = initial.copy(status = ExcuseStatus.APPROVED, version = 1)
        val competing = initial.copy(status = ExcuseStatus.REJECTED, version = 1)
        assertEquals(first, repository.compareAndUpdate(initial, first))
        assertEquals(null, repository.compareAndUpdate(initial, competing))
    }

    @Test
    fun teacherWithoutTutorRoleCannotListExcuses() = runTest {
        val setup = setup()
        assertFailsWith<ApiException.Forbidden> {
            setup.service.listFor(TEACHER)
        }
    }

    private suspend fun setup(): Setup {
        val groups = InMemoryAcademicGroupRepository().also {
            it.create(AcademicGroupRecord("6A", "6A", true, Instant.EPOCH))
            it.create(AcademicGroupRecord("6B", "6B", true, Instant.EPOCH))
        }
        groups.assign(AcademicGroupMembershipRecord("6A", STUDENT.id, Instant.EPOCH))
        groups.assign(AcademicGroupMembershipRecord("6B", OTHER_STUDENT.id, Instant.EPOCH))
        val assignments = InMemoryTutorAssignmentRepository().also {
            it.create(
                TutorAssignmentRecord(
                    id = "ta-1",
                    tutorUserId = TUTOR.id,
                    academicGroupId = "6A",
                    active = true,
                    assignedBy = ADMIN.id,
                    assignedAt = Instant.EPOCH,
                ),
            )
        }
        val tutoring = TutorAssignmentService(assignments, groups)
        val service = ExcuseService(
            repository = InMemoryExcuseRepository(),
            tutoring = tutoring,
            clock = Clock.fixed(Instant.parse("2026-10-06T19:00:00Z"), ZoneOffset.UTC),
            groupRepository = groups,
        )
        return Setup(service)
    }

    private data class Setup(val service: ExcuseService)

    private companion object {
        val STUDENT = UserSummary("student-1", "Ana", roles = setOf(UserRole.STUDENT))
        val OTHER_STUDENT = UserSummary("student-2", "Luis", roles = setOf(UserRole.STUDENT))
        val TUTOR = UserSummary(
            "teacher-tutor-1",
            "Mtra. Elena",
            roles = setOf(UserRole.TEACHER, UserRole.TUTOR),
        )
        val TEACHER = UserSummary("teacher-2", "Mtro. Luis", roles = setOf(UserRole.TEACHER))
        val ADMIN = UserSummary("admin-1", "Control escolar", roles = setOf(UserRole.ADMIN))
    }
}
