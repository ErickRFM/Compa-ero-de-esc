package org.companerodeescuela.feature.schedule

import kotlin.test.Test
import kotlin.test.assertEquals
import org.companerodeescuela.shared.contracts.ScheduleEntry

class WeeklyScheduleTest {

    @Test
    fun `weekly schedule is ordered by weekday then time`() {
        val result = WeeklySchedule.order(
            listOf(
                entry("TUESDAY", "09:00"),
                entry("MONDAY", "11:00"),
                entry("MONDAY", "08:00"),
            ),
        )

        assertEquals(listOf("08:00", "11:00", "09:00"), result.map { it.startsAt })
    }

    private fun entry(day: String, start: String) = ScheduleEntry(
        courseId = day + start,
        subjectCode = "X",
        subjectName = "Materia",
        groupName = "A",
        teacherName = "Docente",
        dayOfWeek = day,
        startsAt = start,
        endsAt = "12:00",
    )
}
