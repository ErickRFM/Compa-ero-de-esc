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
    fun `separates concatenated 9A subject and teacher text`() {
        val result = ScheduleOcrParser.parse(
            """
            Lunes
            17:00-18:00 Inteligencia de NegociosMa. Guadalupe Tecuapacho
            18:00-19:00 Inteligencia de NegociosMa. Guadalupe Tecuapacho
            """.trimIndent(),
        )

        assertEquals(1, result.size)
        assertEquals("Inteligencia de Negocios", result.single().subjectName)
        assertEquals("Ma. Guadalupe Tecuapacho", result.single().teacherName)
        assertEquals("17:00", result.single().startsAt)
        assertEquals("19:00", result.single().endsAt)
    }

    @Test
    fun `merges contiguous 9A blocks only when academic identity matches`() {
        val result = ScheduleOcrParser.parse(
            """
            Lunes
            14:00-15:00 Inglés IX
            Laura Saldaña
            15:00-16:00 Inglés IX
            Laura Saldaña
            16:00-17:00 Sistemas Embebidos
            Ivette Hernández
            """.trimIndent(),
        )

        assertEquals(2, result.size)
        assertEquals("Inglés IX", result[0].subjectName)
        assertEquals("Laura Saldaña", result[0].teacherName)
        assertEquals("14:00", result[0].startsAt)
        assertEquals("16:00", result[0].endsAt)
        assertEquals("Sistemas Embebidos", result[1].subjectName)
    }

    @Test
    fun `keeps Saturday morning from 9A`() {
        val result = ScheduleOcrParser.parse(
            """
            Sábado
            07:00-08:00 Programación Móvil
            Saúl Olaf Loaiza Meléndez
            08:00-09:00 Programación Móvil
            Saúl Olaf Loaiza Meléndez
            """.trimIndent(),
        )

        assertEquals(1, result.size)
        assertEquals("SATURDAY", result.single().dayOfWeek)
        assertEquals("07:00", result.single().startsAt)
        assertEquals("09:00", result.single().endsAt)
        assertEquals("Saúl Olaf Loaiza Meléndez", result.single().teacherName)
    }


    @Test
    fun `normalizes repeated minor OCR variants using the dominant label`() {
        val result = ScheduleOcrParser.parse(
            """
            Lunes
            14:00-15:00 Programación Móvil
            Saúl Olaf Loaiza Meléndez
            15:00-16:00 Programacion Movil
            Saul Olaf Loaiza Melendez
            16:00-17:00 Programación Móvill
            Saúl Olaf Loaiza Meléndez
            """.trimIndent(),
        )

        assertEquals(1, result.size)
        assertEquals("Programación Móvil", result.single().subjectName)
        assertEquals("Saúl Olaf Loaiza Meléndez", result.single().teacherName)
        assertEquals("14:00", result.single().startsAt)
        assertEquals("17:00", result.single().endsAt)
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
