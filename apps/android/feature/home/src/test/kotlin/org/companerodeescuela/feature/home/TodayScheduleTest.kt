package org.companerodeescuela.feature.home

import org.companerodeescuela.shared.contracts.AcademicLoadResponse
import org.companerodeescuela.shared.contracts.AcademicProfile
import org.companerodeescuela.shared.contracts.AcademicScheduleResponse
import org.companerodeescuela.shared.contracts.ScheduleEntry
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class TodayScheduleTest {
    @Test
    fun `personal Thursday timetable appears on home even without institutional assignment`() {
        val personal =
            listOf(
                entry("Sistemas Embebidos", "13:00", "14:00").copy(
                dayOfWeek = "THURSDAY",
                subjectCode = "PERSONAL",
            ),
                entry("Inglés IX", "14:00", "16:00").copy(
                dayOfWeek = "THURSDAY",
                subjectCode = "PERSONAL",
            ),
            )
        val result =
            TodaySchedule.calculateEntries(
                allEntries = personal,
                now = LocalDateTime.of(2026, 10, 29, 13, 34),
            )

        assertEquals(true, result.hasSchedule)
        assertEquals(2, result.classes.size)
        assertEquals("Sistemas Embebidos", result.current?.subjectName)
        assertEquals("Inglés IX", result.next?.subjectName)
    }

    @Test
    fun `now and next are selected from the current weekday`() {
        val result =
            TodaySchedule.calculate(
                load =
                    AcademicLoadResponse(
                        student = AcademicProfile("student-1", "Ana"),
                        schedule =
                            AcademicScheduleResponse(
                                ownerId = "student-1",
                                entries =
                                    listOf(
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
        val result =
            TodaySchedule.calculate(
                load =
                    AcademicLoadResponse(
                        student = AcademicProfile("student-1", "Ana"),
                        schedule =
                            AcademicScheduleResponse(
                                ownerId = "student-1",
                                entries =
                                    listOf(
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
        val result =
            TodaySchedule.calculate(
                load =
                    AcademicLoadResponse(
                        student = AcademicProfile("student-1", "Ana"),
                        schedule =
                            AcademicScheduleResponse(
                                ownerId = "student-1",
                                entries = emptyList(),
                            ),
                    ),
                now = LocalDateTime.of(2026, 10, 5, 8, 20),
            )

        assertEquals(false, result.hasSchedule)
        assertEquals(0, result.classes.size)
    }

    @Test
    fun `personal placeholder code does not collapse different classes`() {
        val subjects =
            listOf(
                entry("Expresión Oral y Escrita II", "14:00", "16:00").copy(subjectCode = "PERSONAL"),
                entry("Negocios", "16:00", "17:00").copy(subjectCode = "PERSONAL"),
                entry("Inglés IX", "17:00", "19:00").copy(subjectCode = "PERSONAL"),
            )
        assertEquals(3, subjects.distinctBy(::scheduleSubjectKey).size)
        assertEquals(
            scheduleSubjectKey(subjects.first()),
            scheduleSubjectKey(subjects.first().copy(subjectName = "EXPRESIÓN ORAL Y ESCRITA II")),
        )
    }

    @Test
    fun `after last lesson next class stat shows next scheduled day`() {
        val load =
            AcademicLoadResponse(
                student = AcademicProfile("student-1", "Ana"),
                schedule =
                    AcademicScheduleResponse(
                        ownerId = "student-1",
                        entries =
                            listOf(
                                entry("Negocios", "16:00", "17:00").copy(subjectCode = "PERSONAL"),
                                entry("Redes", "09:00", "10:00").copy(dayOfWeek = "TUESDAY"),
                            ),
                    ),
            )
        val result = TodaySchedule.calculate(load, LocalDateTime.of(2026, 10, 5, 23, 6))
        assertEquals(1, result.classes.size)
        assertEquals(null, result.next)
        assertEquals("Mañana", nextClassStatLabel(result))
    }

    @Test
    fun `daily class rows distinguish current, next and completed without lying about status`() {
        val current = entry("Expresión", "14:00", "16:00")
        val next = entry("Negocios", "16:00", "17:00")
        val finished = entry("Inglés", "12:00", "13:00")
        assertEquals("Ahora", classStatus(current, current, next, LocalTime.of(14, 30)).label)
        assertEquals("Siguiente", classStatus(next, current, next, LocalTime.of(14, 30)).label)
        assertEquals("Finalizada", classStatus(finished, current, next, LocalTime.of(14, 30)).label)
        assertEquals("Finalizada", classStatus(current, null, next, LocalTime.of(16, 0)).label)
        assertEquals(null, classStatus(next, null, null, LocalTime.of(15, 0)).label)
    }

    @Test
    fun `cache timestamp is true local time or a neutral label if unknown`() {
        val zone = ZoneId.of("America/Mexico_City")
        val timestamp = Instant.parse("2026-10-05T14:45:00Z").epochSecond
        assertEquals("Agenda guardada · 08:45", cacheStatusLabel(timestamp, zone, LocalDate.of(2026, 10, 5)))
        assertEquals("Agenda guardada · 5 oct · 08:45", cacheStatusLabel(timestamp, zone, LocalDate.of(2026, 10, 6)))
        assertEquals("Agenda guardada", cacheStatusLabel(null, zone, LocalDate.of(2026, 10, 5)))
    }

    @Test
    fun `next lesson advances without another network load across daily boundary`() {
        val load =
            AcademicLoadResponse(
                student = AcademicProfile("student-1", "Ana"),
                schedule =
                    AcademicScheduleResponse(
                        ownerId = "student-1",
                        entries =
                            listOf(
                                entry("Programación", "08:00", "09:00"),
                                entry("Redes", "09:00", "10:00").copy(dayOfWeek = "TUESDAY"),
                            ),
                    ),
            )
        val monday = TodaySchedule.calculate(load, LocalDateTime.of(2026, 10, 5, 23, 59))
        val tuesday = TodaySchedule.calculate(load, LocalDateTime.of(2026, 10, 6, 9, 15))
        assertEquals("Mañana", nextClassStatLabel(monday))
        assertEquals("Redes", tuesday.current?.subjectName)
        assertEquals(1, tuesday.classes.size)
    }

    private fun entry(
        name: String,
        start: String,
        end: String,
    ): ScheduleEntry =
        ScheduleEntry(
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
