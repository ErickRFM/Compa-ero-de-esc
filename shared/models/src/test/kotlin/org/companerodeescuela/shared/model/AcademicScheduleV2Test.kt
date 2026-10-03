package org.companerodeescuela.shared.model

import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class AcademicScheduleV2Test {

    @Test
    fun `term rejects an inverted date range`() {
        assertFailsWith<IllegalArgumentException> {
            AcademicTerm(
                id = AcademicId("2026-1"),
                code = "2026-1",
                startsOn = LocalDate.parse("2026-08-01"),
                endsOn = LocalDate.parse("2026-01-01"),
            )
        }
    }

    @Test
    fun `class occurrence requires a positive local time interval`() {
        val group = fixtureGroup()
        assertFailsWith<IllegalArgumentException> {
            ClassOccurrence(
                id = AcademicId("occ-1"),
                patternId = AcademicId("pat-1"),
                group = group,
                date = LocalDate.parse("2026-10-05"),
                startsAt = LocalTime.of(9, 0),
                endsAt = LocalTime.of(8, 0),
                classroom = null,
                teacher = group.course.teacher,
            )
        }
    }

    @Test
    fun `scheduled occurrence can be represented without an override`() {
        val group = fixtureGroup()
        val occurrence = ClassOccurrence(
            id = AcademicId("occ-1"),
            patternId = AcademicId("pat-1"),
            group = group,
            date = LocalDate.parse("2026-10-05"),
            startsAt = LocalTime.of(7, 0),
            endsAt = LocalTime.of(8, 30),
            classroom = null,
            teacher = group.course.teacher,
        )

        assertNotNull(occurrence.patternId)
    }

    private fun fixtureGroup(): Group {
        val teacher = Teacher(
            person = Person(
                id = PersonId("teacher-1"),
                displayName = "Teacher",
            ),
        )
        val subject = Subject(
            id = AcademicId("MAT-101"),
            code = "MAT-101",
            name = "Álgebra",
        )
        val course = Course(
            id = AcademicId("C-1"),
            subject = subject,
            teacher = teacher,
            term = "2026-1",
        )
        return Group(
            id = AcademicId("C-1:101-A"),
            course = course,
            name = "101-A",
        )
    }
}
