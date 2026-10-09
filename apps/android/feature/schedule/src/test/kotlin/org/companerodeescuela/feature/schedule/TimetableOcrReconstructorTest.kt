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
}
