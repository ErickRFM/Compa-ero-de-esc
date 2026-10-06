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
 * Demo fixtures are explicitly scoped to the demo Ana/Elena identities. Clean
 * QA identities resolve successfully without inheriting demo enrollments or
 * timetable entries.
 */
class MockAcademicProvider : AcademicProvider, MockIntegrationProvider {
    override val id: String = "mock-academic"
    override val displayName: String = "Mock academic system (development only)"

    override suspend fun getStudent(externalId: String): ExternalStudent? =
        MockFixtures.STUDENTS[externalId]

    override suspend fun getAcademicLoad(externalId: String): ExternalAcademicLoad {
        val student = MockFixtures.STUDENTS[externalId] ?: throw MockFixtures.notFound(externalId)
        return when (externalId) {
            MockFixtures.STUDENT_ID -> ExternalAcademicLoad(
                student = student,
                enrollments = MockFixtures.ENROLLMENTS,
                schedule = MockFixtures.SCHEDULE,
            )
            MockFixtures.QA_STUDENT_ID -> ExternalAcademicLoad(
                student = student,
                enrollments = emptyList(),
                schedule = emptyList(),
            )
            else -> ExternalAcademicLoad(
                student = student,
                enrollments = emptyList(),
                schedule = emptyList(),
            )
        }
    }

    override suspend fun listCourses(): List<ExternalCourse> =
        MockFixtures.ENROLLMENTS.map { it.course }

    override suspend fun getSchedule(externalId: String, weekOf: LocalDate?): List<ExternalScheduleSlot> =
        when (externalId) {
            MockFixtures.STUDENT_ID,
            MockFixtures.TEACHER_ID,
            -> MockFixtures.SCHEDULE

            MockFixtures.QA_STUDENT_ID,
            MockFixtures.QA_TEACHER_ID,
            MockFixtures.QA_SUPERVISOR_ID,
            MockFixtures.QA_ADMIN_ID,
            -> emptyList()

            else -> emptyList()
        }

    override suspend fun listTeachers(): List<ExternalTeacher> =
        listOf(MockFixtures.TEACHER, MockFixtures.QA_TEACHER)
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
    const val TEACHER_ID = "T-0001"

    const val QA_STUDENT_ID = "QA-STUDENT"
    const val QA_TEACHER_ID = "QA-TEACHER"
    const val QA_SUPERVISOR_ID = "QA-SUPERVISOR"
    const val QA_ADMIN_ID = "QA-ADMIN"

    val TEACHER = ExternalTeacher(
        externalId = TEACHER_ID,
        fullName = "Mtra. Elena Ríos Salgado",
        institutionalEmail = "elena.rios@escuela.edu",
        departmentCode = "MAT",
    )

    val QA_TEACHER = ExternalTeacher(
        externalId = QA_TEACHER_ID,
        fullName = "QA Docente",
        institutionalEmail = "qa.docente@example.invalid",
        departmentCode = "QA",
    )

    val STUDENTS: Map<String, ExternalStudent> = mapOf(
        STUDENT_ID to ExternalStudent(
            externalId = STUDENT_ID,
            fullName = "Ana López Hernández",
            institutionalEmail = "ana.lopez@escuela.edu",
            programCode = "ING-SIS",
            enrollmentYear = 3,
        ),
        QA_STUDENT_ID to ExternalStudent(
            externalId = QA_STUDENT_ID,
            fullName = "QA Alumno",
            institutionalEmail = "qa.alumno@example.invalid",
            programCode = "QA",
            enrollmentYear = null,
        ),
        QA_TEACHER_ID to ExternalStudent(
            externalId = QA_TEACHER_ID,
            fullName = "QA Docente",
            institutionalEmail = "qa.docente@example.invalid",
            programCode = "QA",
            enrollmentYear = null,
        ),
        QA_SUPERVISOR_ID to ExternalStudent(
            externalId = QA_SUPERVISOR_ID,
            fullName = "QA Supervisor",
            institutionalEmail = "qa.supervisor@example.invalid",
            programCode = "QA",
            enrollmentYear = null,
        ),
        QA_ADMIN_ID to ExternalStudent(
            externalId = QA_ADMIN_ID,
            fullName = "QA Admin",
            institutionalEmail = "qa.admin@example.invalid",
            programCode = "QA",
            enrollmentYear = null,
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
