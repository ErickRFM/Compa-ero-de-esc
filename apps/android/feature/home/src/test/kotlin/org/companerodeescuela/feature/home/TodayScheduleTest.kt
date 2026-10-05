package org.companerodeescuela.feature.home

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import org.companerodeescuela.shared.contracts.AcademicLoadResponse
import org.companerodeescuela.shared.contracts.AcademicProfile
import org.companerodeescuela.shared.contracts.AcademicScheduleResponse
import org.companerodeescuela.shared.contracts.ScheduleEntry

class TodayScheduleTest {
    @Test
    fun `now and next are selected from the current weekday`() {
        val result = TodaySchedule.calculate(
            load = AcademicLoadResponse(
                student = AcademicProfile("student-1", "Ana"),
                schedule = AcademicScheduleResponse(
                    ownerId = "student-1",
                    entries = listOf(
                        entry("Programación", "08:00", "09:00"),
                        entry("Bases de Datos", "09:00", "10:00"),
                        entry("Redes", "11:00", "12:00"),
                    ),
                ),
            ),
            now = LocalDateTime.of(2026, 10, 5, 8, 20),
        )
        assertEquals("Programación", result.current?.subjectName)
        assertEquals("Bases de Datos", result.next?.subjectName)
        assertEquals(3, result.classes.size)
    }

    @Test
    fun `free day exposes the next weekly class`() {
        val result = TodaySchedule.calculate(
            load = AcademicLoadResponse(
                student = AcademicProfile("student-1", "Ana"),
                schedule = AcademicScheduleResponse(
                    ownerId = "student-1",
                    entries = listOf(
                        ScheduleEntry(
                            courseId = "mobile",
                            subjectCode = "PM",
                            subjectName = "Programación móvil",
                            groupName = "8-A",
                            teacherName = "Docente",
                            dayOfWeek = "MONDAY",
                            startsAt = "08:00",
                            endsAt = "09:00",
                            classroomName = "Lab A",
                        ),
                    ),
                ),
            ),
            now = LocalDateTime.of(2026, 10, 4, 12, 0),
        )

        assertEquals(0, result.classes.size)
        assertEquals("Programación móvil", result.nextScheduled?.subjectName)
        assertEquals(1, result.nextScheduledDaysAway)
    }

    @Test
    fun `empty institutional schedule is identified separately from a free day`() {
        val result = TodaySchedule.calculate(
            load = AcademicLoadResponse(
                student = AcademicProfile("student-1", "Ana"),
                schedule = AcademicScheduleResponse(
                    ownerId = "student-1",
                    entries = emptyList(),
                ),
            ),
            now = LocalDateTime.of(2026, 10, 5, 8, 20),
        )

        assertEquals(false, result.hasSchedule)
        assertEquals(0, result.classes.size)
    }

    private fun entry(name: String, start: String, end: String) = ScheduleEntry(
        courseId = name,
        subjectCode = name,
        subjectName = name,
        groupName = "8-A",
        teacherName = "Docente",
        dayOfWeek = "MONDAY",
        startsAt = start,
        endsAt = end,
        classroomName = "Aula 4",
    )
}
