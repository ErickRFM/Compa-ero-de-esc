package org.companerodeescuela.feature.schedule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DigitalPdfTimetableParserTest {
    private val dayNames = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
    private val headers = listOf(
        Triple(112.8f, 163.4f, 0), Triple(208.4f, 265.2f, 1),
        Triple(295.8f, 375.4f, 2), Triple(404.8f, 463.6f, 3),
        Triple(501.4f, 564.3f, 4), Triple(599.6f, 663.7f, 5),
        Triple(693.1f, 767.5f, 6),
    )
    private val dayStarts = listOf(88.9f, 187.6f, 286.2f, 384.8f, 483.5f, 582.1f, 680.8f)
    private val dayEnds = listOf(187.6f, 286.2f, 384.8f, 483.5f, 582.1f, 680.8f, 779.7f)

    @Test
    fun digital9AFixtureHasAllDaysAndCorrectMergedBlocks() {
        val fixture = requireNotNull(javaClass.getResource("/fixtures/9A-ocr.txt")).readText()
        var day = -1
        val lines = mutableListOf<DigitalPdfLine>()
        headers.forEach { (left, right, index) ->
            lines += DigitalPdfLine(dayNames[index], left, right, 90f, 18f)
        }
        for (hour in 7..19) {
            val y = 131f + (hour - 7) * 36f
            val endHour = hour + 1
            lines += DigitalPdfLine("$hour:00-$endHour:00", 16f, 78f, y, 11f)
        }
        val rawLines = fixture.lines().filter(String::isNotBlank)
        rawLines.forEachIndexed { index, value ->
            val foundDay = dayNames.indexOf(value)
            if (foundDay >= 0) {
                day = foundDay
                return@forEachIndexed
            }
            if (value.matches(Regex("""\d{1,2}:\d{2}-\d{1,2}:\d{2} .*"""))) {
                require(day in 0..6)
                val hour = value.substringBefore(':').toInt()
                val subject = value.substringAfter(' ')
                val cellLeft = dayStarts[day]
                val size = if (day == 4 && hour == 14) 8.07f else 9.04f
                lines += DigitalPdfLine(subject, cellLeft + 5f, cellLeft + 83f,
                    131f + (hour - 7) * 36f - 9f, size)
            } else {
                val previous = rawLines.take(index + 1).lastOrNull {
                    it.matches(Regex("""\d{1,2}:\d{2}-\d{1,2}:\d{2} .*"""))
                } ?: error("Teacher without subject")
                val hour = previous.substringBefore(':').toInt()
                lines += DigitalPdfLine(value, dayEnds[day] - 65f, dayEnds[day] - 5f,
                    131f + (hour - 7) * 36f + 10f, 8.72f)
            }
        }
        val output = assertNotNull(DigitalPdfTimetableParser.parseLines(lines.shuffled()))
        assertEquals(25, output.size)
        assertEquals(
            listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"),
            output.map { it.dayOfWeek }.distinct(),
        )
        assertBlock(output, "SATURDAY", "07:00", "09:00", "Programación Móvil", "Saúl Olaf Loaiza Meléndez")
        assertBlock(output, "MONDAY", "14:00", "16:00", "Inglés IX", "Laura Saldaña")
        assertBlock(output, "WEDNESDAY", "17:00", "20:00", "Seguridad Informática", "Osvaldo Moreno")
        assertBlock(output, "FRIDAY", "14:00", "15:00", "Desarrollo de Negocios para TI", "Máxima Sánchez")
        assertFalse(output.any { "IX Inglés" in it.subjectName || "Móvil Programación" in it.subjectName })
    }

    @Test
    fun shuffledGlyphsStillReconstructCorrectWordOrder() {
        var x = 93f
        val letters = "Inglés IX".map { c ->
            val glyph = DigitalPdfGlyph(c.toString(), x, 375f, if (c == ' ') 2.5f else 4f, 9.04f)
            x += if (c == ' ') 2.5f else 4f
            glyph
        }
        val result = DigitalPdfTimetableParser.assembleLines(letters.shuffled())
        assertEquals(listOf("Inglés IX"), result.map { it.text })
    }

    @Test
    fun unrelatedTextDoesNotBecomeATimetable() {
        assertEquals(null, DigitalPdfTimetableParser.parseLines(listOf(
            DigitalPdfLine("Horario", 50f, 110f, 10f, 17f),
            DigitalPdfLine("Programación móvil", 40f, 145f, 20f, 11f),
        )))
    }

    private fun assertBlock(
        entries: List<org.companerodeescuela.core.academic.PersonalScheduleDraft>,
        day: String, start: String, end: String, subject: String, teacher: String,
    ) {
        assertTrue(entries.any {
            it.dayOfWeek == day && it.startsAt == start && it.endsAt == end &&
                it.subjectName == subject && it.teacherName == teacher
        }, "Missing $day $start-$end $subject / $teacher")
    }
}
