package org.companerodeescuela.feature.schedule

import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max
import org.companerodeescuela.core.academic.PersonalScheduleDraft

/**
 * Reads timetable CONTENT instead of guessing words from a low-resolution bitmap.
 *
 * Coordinates are top-origin PDF points. The PDF's text order is not assumed to
 * equal reading order. Glyphs are first assembled into font/position-aware lines;
 * lines are then associated with the correct day column and hour row.
 */
internal data class DigitalPdfGlyph(
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val fontSize: Float,
)

internal data class DigitalPdfLine(
    val text: String,
    val left: Float,
    val right: Float,
    val y: Float,
    val fontSize: Float,
)

internal object DigitalPdfTimetableParser {
    private val days = listOf(
        "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo",
    )
    private val dayIds = listOf(
        "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY",
    )
    private val timeRange = Regex(
        """\b(\d{1,2})\s*[:.]\s*(\d{2})\s*[-–—]\s*(\d{1,2})\s*[:.]\s*(\d{2})\b""",
    )

    fun parseGlyphs(glyphs: List<DigitalPdfGlyph>): List<PersonalScheduleDraft>? =
        parseLines(assembleLines(glyphs))

    internal fun assembleLines(glyphs: List<DigitalPdfGlyph>): List<DigitalPdfLine> {
        val rows = mutableListOf<MutableList<DigitalPdfGlyph>>()
        glyphs.asSequence()
            .filter { it.text.isNotEmpty() && it.fontSize > 0f }
            .sortedWith(compareBy<DigitalPdfGlyph> { it.y }.thenBy { it.fontSize }.thenBy { it.x })
            .forEach { glyph ->
                val row = rows.lastOrNull { existing ->
                    abs(existing.first().y - glyph.y) <= 1.5f &&
                        abs(existing.first().fontSize - glyph.fontSize) <= 0.16f
                }
                if (row == null) rows.add(mutableListOf(glyph)) else row.add(glyph)
            }

        return rows.flatMap { row ->
            val groups = mutableListOf<MutableList<DigitalPdfGlyph>>()
            row.sortedBy { it.x }.forEach { glyph ->
                val last = groups.lastOrNull()?.lastOrNull()
                if (last == null || glyph.x - (last.x + last.width) > max(8f, glyph.fontSize * 1.2f)) {
                    groups.add(mutableListOf(glyph))
                } else {
                    groups.last().add(glyph)
                }
            }
            groups.mapNotNull { group ->
                val text = buildString {
                    group.forEachIndexed { index, glyph ->
                        val before = group.getOrNull(index - 1)
                        if (before != null && glyph.x - (before.x + before.width) > max(1.5f, glyph.fontSize * 0.21f)
                            && !endsWith(" ") && !glyph.text.startsWith(" ")
                        ) append(' ')
                        append(glyph.text)
                    }
                }.replace(Regex("""\s+"""), " ").trim()
                if (text.isBlank()) null else DigitalPdfLine(
                    text = text,
                    left = group.minOf { it.x },
                    right = group.maxOf { it.x + it.width },
                    y = group.map { it.y }.average().toFloat(),
                    fontSize = group.map { it.fontSize }.average().toFloat(),
                )
            }
        }
    }

    internal fun parseLines(lines: List<DigitalPdfLine>): List<PersonalScheduleDraft>? {
        val headers = lines.mapNotNull { line ->
            val index = days.indexOfFirst { normalize(it) == normalize(line.text) }
            if (index == -1) null else index to line
        }.groupBy({ it.first }, { it.second })
            .mapValues { (_, candidates) -> candidates.minBy { it.y } }
        if (headers.size < 5) return null

        val centers = headers.mapValues { (_, value) -> (value.left + value.right) / 2f }
        val consecutiveDistances = centers.keys.sorted().zipWithNext().mapNotNull { (a, b) ->
            val step = (centers.getValue(b) - centers.getValue(a)) / (b - a)
            step.takeIf { it > 15f }
        }.sorted()
        val step = consecutiveDistances.getOrNull(consecutiveDistances.size / 2) ?: return null
        val origins = centers.map { (index, x) -> x - index * step }.sorted()
        val origin = origins[origins.size / 2]
        val boundaries = (0..7).map { origin + (it - 0.5f) * step }

        // Reject wildly irregular "headers" from non-timetable documents.
        if (centers.any { (index, x) -> abs(x - (origin + index * step)) > step * 0.18f }) return null

        data class TimeRow(val y: Float, val from: String, val to: String)
        val rows = lines.mapNotNull { line ->
            if (line.right >= boundaries.first()) return@mapNotNull null
            val range = timeRange.find(line.text) ?: return@mapNotNull null
            val (h1, m1, h2, m2) = range.destructured
            val first = h1.toInt() * 60 + m1.toInt()
            val last = h2.toInt() * 60 + m2.toInt()
            if (h1.toInt() !in 0..23 || h2.toInt() !in 0..23 ||
                m1.toInt() !in 0..59 || m2.toInt() !in 0..59 ||
                first >= last
            ) return@mapNotNull null
            TimeRow(line.y, formatClock(h1, m1), formatClock(h2, m2))
        }.distinctBy { it.from to it.to }.sortedBy { it.y }
        if (rows.size < 5) return null

        var teacherPairs = 0
        var occupiedCells = 0
        val formatted = buildString {
            dayIds.forEachIndexed { dayIndex, day ->
                appendLine(day)
                rows.forEachIndexed { i, time ->
                    val top = if (i > 0) (rows[i - 1].y + time.y) / 2f
                        else time.y - (rows[1].y - time.y) / 2f
                    val bottom = if (i + 1 < rows.size) (rows[i + 1].y + time.y) / 2f
                        else time.y + (time.y - rows[i - 1].y) / 2f
                    val left = boundaries[dayIndex]
                    val right = boundaries[dayIndex + 1]
                    val cell = lines.filter {
                        it.y >= top && it.y < bottom &&
                            it.left >= left + 1f && it.left < right - 1f
                    }.sortedWith(compareBy<DigitalPdfLine> { it.y }.thenBy { it.left })
                    if (cell.isEmpty()) return@forEachIndexed

                    // In actual school-generated PDFs teachers are right aligned
                    // whereas subjects start at the cell's left padding. Their
                    // baselines CAN overlap, and small subjects can use a smaller
                    // font than their teachers. Do not sort all words together or
                    // decide based only on y/font size (the previous bug).
                    val teacher = cell.filter { line ->
                        val offset = line.left - left
                        val rightGap = right - line.right
                        offset > max(10f, (right - left) * 0.11f) &&
                            rightGap < max(9f, (right - left) * 0.10f)
                    }
                    val teacherSet = teacher.toSet()
                    val subject = cell.filterNot(teacherSet::contains)
                    if (subject.isEmpty()) return@forEachIndexed
                    occupiedCells++
                    if (teacher.isNotEmpty()) teacherPairs++
                    append(time.from)
                    append('-')
                    append(time.to)
                    append(' ')
                    appendLine(subject.joinToString(" ") { it.text })
                    if (teacher.isNotEmpty()) appendLine(teacher.joinToString(" ") { it.text })
                }
            }
        }
        // Incomplete or uncertain native extraction must fall back to OCR +
        // manual import review, never overwrite a complete existing timetable.
        if (occupiedCells < 5 || teacherPairs * 10 < occupiedCells * 6) return null
        val entries = ScheduleOcrParser.parse(formatted)
        return entries.takeIf { it.size >= 5 && it.map { draft -> draft.dayOfWeek }.distinct().size >= 3 }
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")

    private fun formatClock(hour: String, minute: String): String =
        hour.toInt().toString().padStart(2, '0') + ":" + minute
}
