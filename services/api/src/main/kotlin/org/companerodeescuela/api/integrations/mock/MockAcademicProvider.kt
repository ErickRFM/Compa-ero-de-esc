package org.companerodeescuela.api.integrations.mock

import java.time.LocalDate
import org.companerodeescuela.api.integrations.IntegrationException
import org.companerodeescuela.api.integrations.MockIntegrationProvider
import org.companerodeescuela.api.integrations.academic.AcademicProvider
import org.companerodeescuela.api.integrations.academic.dto.ExternalAcademicLoad
import org.companerodeescuela.api.integrations.academic.dto.ExternalCourse
import org.companerodeescuela.api.integrations.academic.dto.ExternalEnrollment
import org.companerodeescuela.api.integrations.academic.dto.ExternalScheduleSlot
import org.companerodeescuela.api.integrations.academic.dto.ExternalStudent
import org.companerodeescuela.api.integrations.academic.dto.ExternalSubject
import org.companerodeescuela.api.integrations.academic.dto.ExternalTeacher

/**
 * Development-only [AcademicProvider] backed by in-memory fixtures.
 *
 * This class must never be reachable in a non-local environment. The rule is
 * enforced by [org.companerodeescuela.api.integrations.ProviderRegistry],
 * which refuses to build a production registry containing a mock provider.
 */
class MockAcademicProvider : AcademicProvider, MockIntegrationProvider {
    override val id: String = "mock-academic"
    override val displayName: String = "Mock academic system (development only)"

    override suspend fun getStudent(externalId: String): ExternalStudent? =
        MockFixtures.STUDENTS[externalId]

    override suspend fun getAcademicLoad(externalId: String): ExternalAcademicLoad {
        if (MockFixtures.STUDENTS[externalId] == null) throw MockFixtures.notFound(externalId)
        return ExternalAcademicLoad(
            student = MockFixtures.STUDENTS.getValue(externalId),
            enrollments = MockFixtures.ENROLLMENTS,
            schedule = MockFixtures.SCHEDULE,
        )
    }

    override suspend fun listCourses(): List<ExternalCourse> =
        MockFixtures.ENROLLMENTS.map { it.course }

    override suspend fun getSchedule(externalId: String, weekOf: LocalDate?): List<ExternalScheduleSlot> =
        MockFixtures.SCHEDULE

    override suspend fun listTeachers(): List<ExternalTeacher> = listOf(MockFixtures.TEACHER)
}

/**
 * Fixture data for the development providers.
 *
 * Weekday names use the Spanish vocabulary on purpose: it exercises the
 * normalisation path a real institution will need instead of only the English
 * one.
 */
internal object MockFixtures {
    const val STUDENT_ID = "2020-10455"

    val TEACHER = ExternalTeacher(
        externalId = "T-0001",
        fullName = "Mtra. Elena Ríos Salgado",
        institutionalEmail = "elena.rios@escuela.edu",
        departmentCode = "MAT",
    )

    val STUDENTS: Map<String, ExternalStudent> = mapOf(
        STUDENT_ID to ExternalStudent(
            externalId = STUDENT_ID,
            fullName = "Ana López Hernández",
            institutionalEmail = "ana.lopez@escuela.edu",
            programCode = "ING-SIS",
            enrollmentYear = 3,
        ),
    )

    private val ALGEBRA = ExternalSubject("MAT-101", "Álgebra Lineal", credits = 8)
    private val HISTORIA = ExternalSubject("HIS-204", "Historia Contemporánea", credits = 6)

    val ENROLLMENTS: List<ExternalEnrollment> = listOf(
        ExternalEnrollment(
            course = ExternalCourse("C-9001", ALGEBRA, TEACHER, "2026-1", "101-A"),
            enrolledAtIso = "2026-01-12T09:00:00Z",
        ),
        ExternalEnrollment(
            course = ExternalCourse("C-9002", HISTORIA, TEACHER, "2026-1", "204-B"),
            enrolledAtIso = "2026-01-12T09:05:00Z",
        ),
    )

    val SCHEDULE: List<ExternalScheduleSlot> = listOf(
        ExternalScheduleSlot(
            courseId = "C-9001",
            groupName = "101-A",
            weekdayName = "Lunes",
            startTime = "07:00",
            endTime = "08:30",
            buildingCode = "B-1",
            classroomCode = "A-204",
            campusName = "Campus Central",
        ),
        ExternalScheduleSlot(
            courseId = "C-9001",
            groupName = "101-A",
            weekdayName = "Jueves",
            startTime = "09:00",
            endTime = "10:30",
            buildingCode = "B-1",
            classroomCode = "A-204",
            campusName = "Campus Central",
        ),
        ExternalScheduleSlot(
            courseId = "C-9002",
            groupName = "204-B",
            weekdayName = "Martes",
            startTime = "11:00",
            endTime = "12:30",
            buildingCode = "B-3",
            classroomCode = "L-11",
            campusName = "Campus Central",
        ),
    )

    fun notFound(externalId: String): IntegrationException = IntegrationException(
        providerId = "mock-academic",
        category = IntegrationException.Category.NOT_FOUND,
        message = "No record for '$externalId'",
    )
}
