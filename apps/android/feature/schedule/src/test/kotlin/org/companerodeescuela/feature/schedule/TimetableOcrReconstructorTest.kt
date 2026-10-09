package org.companerodeescuela.feature.schedule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimetableOcrReconstructorTest {
    private fun line(text: String, x: Int, y: Int, centerX: Float = x.toFloat()) =
        TimetableOcrReconstructor.PositionedLine(text, centerX, y.toFloat(), x)

    @Test
    fun `recovers Wednesday and Thursday when OCR drops both headings`() {
        val pageLines = listOf(
            line("Lunes", 80, 20, 100f),
            line("Martes", 180, 20, 200f),
            line("Viernes", 480, 20, 500f),
            line("Sábado", 580, 20, 600f),
            line("13:00-14:00", 10, 100, 45f),
            line("14:00-15:00", 10, 200, 45f),
            line("15:00-16:00", 10, 300, 45f),
            // Subject names can cover several lines and extend rightwards.
            line("Desarrollo de", 258, 77, 330f),
            line("Negocios para TI", 258, 88, 342f),
            line("Máxima Sánchez", 279, 115, 337f),
            line("Sistemas", 358, 177, 407f),
            line("Embebidos", 358, 188, 412f),
            line("Ivette Hernández", 380, 215, 429f),
            // An isolated teacher must not create a fake Friday class.
            line("Osvaldo Moreno", 480, 120, 530f),
        )

        val reconstructed = TimetableOcrReconstructor.reconstructLines(pageLines)
        requireNotNull(reconstructed)
        val entries = ScheduleOcrParser.parse(reconstructed)

        assertTrue(entries.any {
            it.dayOfWeek == "WEDNESDAY" &&
                it.startsAt == "13:00" &&
                it.subjectName == "Desarrollo de Negocios para TI" &&
                it.teacherName == "Máxima Sánchez"
        })
        assertTrue(entries.any {
            it.dayOfWeek == "THURSDAY" &&
                it.startsAt == "14:00" &&
                it.subjectName == "Sistemas Embebidos" &&
                it.teacherName == "Ivette Hernández"
        })
        assertFalse(entries.any { it.subjectName == "Osvaldo Moreno" })
        assertEquals(2, entries.size)
    }

    @Test
    fun `9A PDF layout does not shift Saturday to Friday or merge Monday and Tuesday`() {
        // Coordinates follow the original 9A PDF's seven columns (in points).
        // This catches the previous left-edge-to-header-center day shift.
        val columns = listOf(
            90f to 190f, 190f to 289f, 289f to 388f,
            388f to 487f, 487f to 585f, 585f to 684f, 684f to 783f,
        )
        val positioned = listOf(
            line("Lunes", 113, 86, 138f),
            line("Martes", 208, 86, 236f),
            line("Miércoles", 296, 86, 335f),
            line("Jueves", 405, 86, 433f),
            line("Viernes", 501, 86, 533f),
            line("Sábado", 600, 86, 632f),
            line("Domingo", 693, 86, 730f),
            line("07:00-08:00", 16, 131, 49f),
            line("08:00-09:00", 16, 167, 49f),
            line("13:00-14:00", 16, 347, 49f),
            line("14:00-15:00", 16, 383, 49f),
            line("15:00-16:00", 16, 419, 49f),
            line("Programación Móvil", 587, 115, 627f),
            line("Saúl Olaf Loaiza Meléndez", 588, 137, 647f),
            line("Programación Móvil", 587, 151, 627f),
            line("Saúl Olaf Loaiza Meléndez", 588, 173, 647f),
            line("Sistemas Embebidos", 94, 335, 137f),
            line("Ivette Hernández", 115, 353, 155f),
            line("Inglés IX", 94, 371, 112f),
            line("Laura Saldaña", 120, 389, 155f),
            line("Expresión Oral y Escrita II", 192, 371, 239f),
            line("Diana Laura Montes", 206, 389, 256f),
            line("Desarrollo de", 291, 334, 319f),
            line("Negocios para TI", 291, 340, 339f),
            line("Máxima Sánchez", 304, 353, 348f),
            line("Sistemas Embebidos", 390, 371, 433f),
            line("Ivette Hernández", 408, 389, 449f),
        )
        val text = TimetableOcrReconstructor.reconstructLines(positioned, columns)
        requireNotNull(text)
        val entries = ScheduleOcrParser.parse(text)

        assertTrue(entries.any {
            it.dayOfWeek == "SATURDAY" && it.startsAt == "07:00" &&
                it.endsAt == "09:00" && it.subjectName == "Programación Móvil"
        })
        assertFalse(entries.any {
            it.dayOfWeek == "FRIDAY" && it.startsAt == "07:00"
        })
        assertTrue(entries.any {
            it.dayOfWeek == "MONDAY" && it.startsAt == "14:00" &&
                it.subjectName == "Inglés IX"
        })
        assertTrue(entries.any {
            it.dayOfWeek == "TUESDAY" && it.startsAt == "14:00" &&
                it.subjectName == "Expresión Oral y Escrita II"
        })
        assertTrue(entries.any {
            it.dayOfWeek == "WEDNESDAY" && it.startsAt == "13:00" &&
                it.subjectName == "Desarrollo de Negocios para TI"
        })
        assertFalse(entries.any {
            it.subjectName.contains("Inglés IX Expresión")
        })
    }
}
