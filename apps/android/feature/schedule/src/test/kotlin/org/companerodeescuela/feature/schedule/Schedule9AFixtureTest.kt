package org.companerodeescuela.feature.schedule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class Schedule9AFixtureTest {
    @Test
    fun `imports complete 9A week from Monday through Saturday`() {
        val raw = requireNotNull(javaClass.getResource("/fixtures/9A-ocr.txt")).readText()
        val result = ScheduleOcrParser.parse(raw)

        assertEquals(25, result.size)
        assertEquals(
            setOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"),
            result.map { it.dayOfWeek }.toSet(),
        )

        assertBlock(
            result = result,
            day = "MONDAY",
            start = "14:00",
            end = "16:00",
            subject = "Inglés IX",
            teacher = "Laura Saldaña",
        )
        assertBlock(
            result = result,
            day = "MONDAY",
            start = "17:00",
            end = "19:00",
            subject = "Inteligencia de Negocios",
            teacher = "Ma. Guadalupe Tecuapacho",
        )
        assertBlock(
            result = result,
            day = "WEDNESDAY",
            start = "17:00",
            end = "20:00",
            subject = "Seguridad Informática",
            teacher = "Osvaldo Moreno",
        )
        assertBlock(
            result = result,
            day = "THURSDAY",
            start = "18:00",
            end = "20:00",
            subject = "Programación Móvil",
            teacher = "Saúl Olaf Loaiza Meléndez",
        )
        assertBlock(
            result = result,
            day = "FRIDAY",
            start = "16:00",
            end = "18:00",
            subject = "Programación Móvil",
            teacher = "Saúl Olaf Loaiza Meléndez",
        )
        assertBlock(
            result = result,
            day = "SATURDAY",
            start = "07:00",
            end = "09:00",
            subject = "Programación Móvil",
            teacher = "Saúl Olaf Loaiza Meléndez",
        )
    }

    private fun assertBlock(
        result: List<org.companerodeescuela.core.academic.PersonalScheduleDraft>,
        day: String,
        start: String,
        end: String,
        subject: String,
        teacher: String,
    ) {
        val block = result.firstOrNull {
            it.dayOfWeek == day &&
                it.startsAt == start &&
                it.endsAt == end &&
                it.subjectName == subject
        }
        assertNotNull(block, "Missing $day $start-$end $subject")
        assertEquals(teacher, block.teacherName)
    }
}
