package org.companerodeescuela.api.integrations.academic.mapper

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.companerodeescuela.api.integrations.IntegrationException
import org.companerodeescuela.api.integrations.academic.dto.ExternalCourse
import org.companerodeescuela.api.integrations.academic.dto.ExternalEnrollment
import org.companerodeescuela.api.integrations.academic.dto.ExternalScheduleSlot
import org.companerodeescuela.api.integrations.academic.dto.ExternalStudent
import org.companerodeescuela.api.integrations.academic.dto.ExternalSubject
import org.companerodeescuela.api.integrations.academic.dto.ExternalTeacher
import org.companerodeescuela.shared.model.PersonId
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class AcademicMappersTest {

    private val teacher = ExternalTeacher(
        externalId = "T-0001",
        fullName = "Mtra. Elena Ríos Salgado",
        institutionalEmail = "Elena.Rios@Escuela.EDU",
        departmentCode = "MAT",
    )

    private val subject = ExternalSubject("MAT-101", "Álgebra Lineal", credits = 8)

    private val course = ExternalCourse(
        externalId = "C-9001",
        subject = subject,
        teacher = teacher,
        term = "2026-1",
        groupName = "101-A",
    )

    private fun slot(
        weekday: String = "Lunes",
        start: String = "07:00",
        end: String = "08:30",
        courseId: String = "C-9001",
        groupName: String = "101-A",
    ) = ExternalScheduleSlot(
        courseId = courseId,
        groupName = groupName,
        weekdayName = weekday,
        startTime = start,
        endTime = end,
        buildingCode = "B-1",
        classroomCode = "A-204",
        campusName = "Campus Central",
    )

    @Test
    @DisplayName("Student mapping trims the name and lowercases the email")
    fun mapsStudent() {
        val student = AcademicMappers.toStudent(
            ExternalStudent(
                externalId = "2020-10455",
                fullName = "  Ana López Hernández  ",
                institutionalEmail = "  ANA.Lopez@Escuela.EDU ",
            ),
        )

        assertEquals(PersonId("2020-10455"), student.person.id)
        assertEquals("Ana López Hernández", student.person.displayName)
        assertEquals("ana.lopez@escuela.edu", student.person.institutionalEmail)
    }

    @Test
    @DisplayName("Blank upstream values become null instead of empty strings")
    fun normalisesBlankOptionals() {
        val student = AcademicMappers.toStudent(
            ExternalStudent(externalId = "1", fullName = "Ana", institutionalEmail = "   "),
        )
        val mappedTeacher = AcademicMappers.toTeacher(
            ExternalTeacher(externalId = "T-1", fullName = "Ana", departmentCode = ""),
        )

        assertNull(student.person.institutionalEmail)
        assertNull(mappedTeacher.departmentCode)
    }

    @Test
    @DisplayName("Subject uses the upstream code as both id and code")
    fun mapsSubject() {
        val mapped = AcademicMappers.toSubject(subject)

        assertEquals("MAT-101", mapped.code)
        assertEquals("Álgebra Lineal", mapped.name)
        assertEquals(8, mapped.credits)
    }

    @Test
    @DisplayName("Group id is namespaced by course and group name")
    fun mapsGroup() {
        val group = AcademicMappers.toGroup(course)

        assertEquals("C-9001:101-A", group.id.value)
        assertEquals("101-A", group.name)
        assertEquals("2026-1", group.course.term)
        assertEquals("Mtra. Elena Ríos Salgado", group.course.teacher.person.displayName)
    }

    @Test
    @DisplayName("Enrollment parses the upstream timestamp")
    fun mapsEnrollment() {
        val enrollment = AcademicMappers.toEnrollment(
            studentId = PersonId("2020-10455"),
            externalEnrollment = ExternalEnrollment(course, "2026-01-12T09:00:00Z"),
        )

        assertEquals(PersonId("2020-10455"), enrollment.studentId)
        assertEquals(Instant.parse("2026-01-12T09:00:00Z"), enrollment.enrolledAt)
    }

    @Test
    @DisplayName("Schedule normalises Spanish and English weekday names")
    fun mapsWeekdays() {
        val schedule = AcademicMappers.toSchedule(
            ownerId = PersonId("2020-10455"),
            slots = listOf(
                slot(weekday = "Lunes"),
                slot(weekday = "martes"),
                slot(weekday = "Wednesday"),
            ),
            coursesById = mapOf("C-9001" to course),
        )

        assertEquals(
            listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY),
            schedule.slots.map { it.dayOfWeek },
        )
    }

    @Test
    @DisplayName("Schedule resolves real course data when the course is known")
    fun scheduleResolvesCourse() {
        val schedule = AcademicMappers.toSchedule(
            ownerId = PersonId("2020-10455"),
            slots = listOf(slot()),
            coursesById = mapOf("C-9001" to course),
        )

        val mapped = schedule.slots.single()
        assertEquals("Álgebra Lineal", mapped.group.course.subject.name)
        assertEquals("Mtra. Elena Ríos Salgado", mapped.group.course.teacher.person.displayName)
    }

    @Test
    @DisplayName("An unknown course keeps the slot but marks the subject as unresolved")
    fun scheduleWithUnknownCourseIsStillVisible() {
        val schedule = AcademicMappers.toSchedule(
            ownerId = PersonId("2020-10455"),
            slots = listOf(slot(courseId = "C-UNKNOWN")),
        )

        val mapped = schedule.slots.single()
        assertEquals(AcademicMappers.UNKNOWN_COURSE, mapped.group.course.subject.name)
    }

    @Test
    @DisplayName("Classroom is built from campus, building and room codes")
    fun mapsClassroom() {
        val schedule = AcademicMappers.toSchedule(
            ownerId = PersonId("2020-10455"),
            slots = listOf(slot()),
            coursesById = mapOf("C-9001" to course),
        )

        val classroom = requireNotNull(schedule.slots.single().classroom)
        assertEquals("A-204", classroom.name)
        assertEquals("B-1", classroom.building.name)
        assertEquals("Campus Central", classroom.building.campus.name)
    }

    @Test
    @DisplayName("A slot without a classroom maps to null instead of failing")
    fun slotWithoutClassroom() {
        val schedule = AcademicMappers.toSchedule(
            ownerId = PersonId("2020-10455"),
            slots = listOf(
                ExternalScheduleSlot("C-9001", "101-A", "Lunes", "07:00", "08:30"),
            ),
        )

        assertNull(schedule.slots.single().classroom)
    }

    @Test
    @DisplayName("An unrecognised weekday is reported as a malformed response")
    fun rejectsUnknownWeekday() {
        val error = assertFailsWith<IntegrationException> {
            AcademicMappers.toSchedule(
                ownerId = PersonId("1"),
                slots = listOf(slot(weekday = "Someday")),
            )
        }

        assertEquals(IntegrationException.Category.MALFORMED_RESPONSE, error.category)
        assertTrue(error.message!!.contains("Someday"))
    }

    @Test
    @DisplayName("A slot that ends before it starts is rejected")
    fun rejectsInvertedSlot() {
        val error = assertFailsWith<IntegrationException> {
            AcademicMappers.toSchedule(
                ownerId = PersonId("1"),
                slots = listOf(slot(start = "09:00", end = "08:00")),
            )
        }

        assertEquals(IntegrationException.Category.MALFORMED_RESPONSE, error.category)
    }

    @Test
    @DisplayName("Unparsable times are rejected rather than defaulted")
    fun rejectsUnparsableTime() {
        val error = assertFailsWith<IntegrationException> {
            AcademicMappers.toSchedule(
                ownerId = PersonId("1"),
                slots = listOf(slot(start = "7am")),
            )
        }

        assertEquals(IntegrationException.Category.MALFORMED_RESPONSE, error.category)
    }

    @Test
    @DisplayName("Schedule groups slots by day and orders them by start time")
    fun scheduleOrdersSlotsPerDay() {
        val schedule = AcademicMappers.toSchedule(
            ownerId = PersonId("1"),
            slots = listOf(
                slot(weekday = "Lunes", start = "09:00", end = "10:30"),
                slot(weekday = "Lunes", start = "07:00", end = "08:30"),
                slot(weekday = "Martes", start = "11:00", end = "12:30"),
            ),
        )

        val monday = schedule.slotsFor(DayOfWeek.MONDAY)
        assertEquals(2, monday.size)
        assertEquals(LocalTime.of(7, 0), monday.first().startsAt)
        assertEquals(1, schedule.slotsFor(DayOfWeek.TUESDAY).size)
        assertTrue(schedule.slotsFor(DayOfWeek.SUNDAY).isEmpty())
    }
}
