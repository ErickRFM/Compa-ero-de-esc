package org.companerodeescuela.feature.home

import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import org.companerodeescuela.shared.contracts.ScheduleEntry

class ClassProgressTest {

    @Test
    fun `class progress is proportional inside the occurrence`() {
        val entry = entry("08:00", "09:00")
        assertEquals(0.5f, classProgress(entry, LocalTime.of(8, 30)), 0.001f)
    }

    @Test
    fun `class progress clamps before and after the occurrence`() {
        val entry = entry("08:00", "09:00")
        assertEquals(0f, classProgress(entry, LocalTime.of(7, 30)), 0.001f)
        assertEquals(1f, classProgress(entry, LocalTime.of(9, 30)), 0.001f)
    }

    private fun entry(start: String, end: String) = ScheduleEntry(
        courseId = "course-1",
        subjectCode = "PROG",
        subjectName = "Programación",
        groupName = "A",
        teacherName = "Docente",
        dayOfWeek = "MONDAY",
        startsAt = start,
        endsAt = end,
        classroomName = "Aula 4",
    )
}
