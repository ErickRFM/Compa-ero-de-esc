package org.companerodeescuela.api.tutoring

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.academic.groups.AcademicGroupMembershipRecord
import org.companerodeescuela.api.academic.groups.AcademicGroupRecord
import org.companerodeescuela.api.academic.groups.InMemoryAcademicGroupRepository
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.AddTutorCaseNoteRequest
import org.companerodeescuela.shared.contracts.CreateTutorCaseRequest
import org.companerodeescuela.shared.contracts.TutorNoteVisibility
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary

class TutorCaseServiceTest {
    private val tutor = UserSummary("tutor-1", "Tutor", roles = setOf(UserRole.TUTOR))
    private val outsider = UserSummary("tutor-2", "Other tutor", roles = setOf(UserRole.TUTOR))
    private val student = UserSummary("student-1", "Student", roles = setOf(UserRole.STUDENT))

    @Test
    fun tutorCreatesCaseAndStudentOnlySeesPublishedNotes() = runTest {
        val (service, _) = setup()
        val record = service.create(tutor, CreateTutorCaseRequest("9A", student.id, "Academic support"))
        service.addNote(tutor, record.id, AddTutorCaseNoteRequest("Private observation"))
        service.addNote(tutor, record.id, AddTutorCaseNoteRequest("Please attend tutorial", TutorNoteVisibility.STUDENT_VISIBLE))
        assertEquals(2, service.detail(tutor, record.id).notes.size)
        val published = service.myPublishedNotes(student)
        assertEquals(1, published.size)
        assertEquals("Please attend tutorial", published.single().body)
        assertFailsWith<ApiException.Forbidden> { service.detail(outsider, record.id) }
        assertFailsWith<ApiException.Forbidden> { service.list(student) }
    }

    @Test
    fun tutorCannotCreateCasesOutsideEnrollmentOrRequestRestrictedNotes() = runTest {
        val (service, _) = setup()
        assertFailsWith<ApiException.Forbidden> {
            service.create(tutor, CreateTutorCaseRequest("9A", "not-enrolled", "Academic support"))
        }
        val record = service.create(tutor, CreateTutorCaseRequest("9A", student.id, "Academic support"))
        assertFailsWith<ApiException.Forbidden> {
            service.addNote(tutor, record.id, AddTutorCaseNoteRequest("Sensitive comment", TutorNoteVisibility.SENSITIVE_RESTRICTED))
        }
    }

    private suspend fun setup(): Pair<TutorCaseService, InMemoryTutorCaseRepository> {
        val groups = InMemoryAcademicGroupRepository()
        groups.create(AcademicGroupRecord("9A", "9 A", true, Instant.EPOCH))
        groups.assign(AcademicGroupMembershipRecord("9A", student.id, Instant.EPOCH))
        val tutors = InMemoryTutorAssignmentRepository()
        tutors.create(TutorAssignmentRecord("t1", tutor.id, "9A", true, "admin", Instant.EPOCH))
        val assignmentService = TutorAssignmentService(tutors, groups)
        val repository = InMemoryTutorCaseRepository()
        return TutorCaseService(repository, assignmentService, groups) to repository
    }
}
