package org.companerodeescuela.feature.schedule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.companerodeescuela.shared.contracts.ScheduleSource

class ScheduleOcrParserTest {
    @Test
    fun `parses Spanish day time subject and optional context`() {
        val result = ScheduleOcrParser.parse(
            """
            Lunes
            08:00 - 10:00 Bases de datos
            Lab 2
            Mtra. Elena
            Martes
            10:00–12:00 Programación móvil
            Aula 4
            Ing. Ros
            """.trimIndent(),
        )

        assertEquals(2, result.size)
        assertEquals("MONDAY", result[0].dayOfWeek)
        assertEquals("08:00", result[0].startsAt)
        assertEquals("10:00", result[0].endsAt)
        assertEquals("Bases de datos", result[0].subjectName)
        assertEquals(ScheduleSource.OCR_IMPORT, result[0].source)
        assertEquals("TUESDAY", result[1].dayOfWeek)
    }

    @Test
    fun `does not fabricate entries when a time or day is missing`() {
        val result = ScheduleOcrParser.parse(
            """
            Programación móvil
            Aula 4
            Docente
            """.trimIndent(),
        )

        assertTrue(result.isEmpty())
    }
}
