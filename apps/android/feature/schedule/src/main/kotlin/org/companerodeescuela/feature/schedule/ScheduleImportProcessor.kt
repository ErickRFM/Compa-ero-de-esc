package org.companerodeescuela.feature.schedule

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class ScheduleImportProcessor(
    private val context: Context,
) {
    suspend fun extractText(uri: Uri): String {
        val mime = context.contentResolver.getType(uri).orEmpty()
        return if (mime == "application/pdf") {
            extractPdf(uri)
        } else {
            recognize(InputImage.fromFilePath(context, uri))
        }
    }

    private suspend fun extractPdf(uri: Uri): String {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("No se pudo abrir el PDF")
        descriptor.use { fd ->
            PdfRenderer(fd).use { renderer ->
                val pages = minOf(renderer.pageCount, MAX_PAGES)
                return buildList {
                    repeat(pages) { index ->
                        renderer.openPage(index).use { page ->
                            // A timetable fits seven narrow columns on one PDF page.
                            // Rendering at native 72 DPI leaves glyphs too small for ML Kit.
                            val scale = minOf(3f, MAX_RENDER_DIMENSION / maxOf(page.width, page.height).toFloat())
                            val bitmap = Bitmap.createBitmap(
                                (page.width * scale).toInt().coerceAtLeast(1),
                                (page.height * scale).toInt().coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888,
                            )
                            bitmap.eraseColor(Color.WHITE)
                            page.render(
                                bitmap,
                                null,
                                Matrix().apply { postScale(scale, scale) },
                                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                            )
                            try {
                                add(recognize(InputImage.fromBitmap(bitmap, 0)))
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                }.joinToString("\n")
            }
        }
    }

    private suspend fun recognize(image: InputImage): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            suspendCancellableCoroutine { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        if (continuation.isActive) {
                            continuation.resume(
                                TimetableOcrReconstructor.reconstruct(result) ?: result.text,
                            )
                        }
                    }
                    .addOnFailureListener { error ->
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
            }
        } finally {
            recognizer.close()
        }
    }

    private companion object {
        const val MAX_PAGES = 4
        const val MAX_RENDER_DIMENSION = 3072f
    }
}

/**
 * Layout-aware timetable reader. ML Kit's full-page reading order does not
 * preserve PDF table columns; positions are essential.
 *
 * This algorithm deliberately reconstructs missing header columns instead of
 * assigning Wednesday/Thursday cells to Friday when OCR misses their labels.
 */
internal object TimetableOcrReconstructor {
    private val dayNames = linkedMapOf(
        "Lunes" to listOf("lunes"),
        "Martes" to listOf("martes"),
        "Miércoles" to listOf("miércoles", "miercoles"),
        "Jueves" to listOf("jueves"),
        "Viernes" to listOf("viernes"),
        "Sábado" to listOf("sábado", "sabado"),
        "Domingo" to listOf("domingo"),
    )

    private val timeRange = Regex(
        """\b([01]?\d|2[0-3])[:.]([0-5]\d)\s*(?:-|–|—|a)\s*([01]?\d|2[0-3])[:.]([0-5]\d)\b""",
        RegexOption.IGNORE_CASE,
    )

    internal data class PositionedLine(
        val text: String,
        val centerX: Float,
        val centerY: Float,
        val left: Int,
    )

    fun reconstruct(result: Text): String? = reconstructLines(
        result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            val value = line.text.trim().takeIf(String::isNotBlank) ?: return@mapNotNull null
            PositionedLine(value, box.exactCenterX(), box.exactCenterY(), box.left)
        },
    )

    internal fun reconstructLines(lines: List<PositionedLine>): String? {
        val observed = dayNames.entries.mapIndexedNotNull { index, (name, aliases) ->
            lines.firstOrNull { line ->
                val normalized = line.text.lowercase()
                aliases.any { alias -> normalized == alias || normalized.startsWith("$alias ") }
            }?.let { Triple(index, name, it) }
        }.sortedBy { it.first }

        val anchors = lines.mapNotNull { line ->
            val match = timeRange.find(line.text) ?: return@mapNotNull null
            Triple(normalizeRange(match), line.centerY, line.centerX)
        }.distinctBy { it.first + "@" + it.second.toInt() }.sortedBy { it.second }

        if (observed.size < MIN_DAY_HEADERS || anchors.size < MIN_TIME_ROWS) return null
        if (!timetableHeadersShareRow(
                observed.map { it.third.centerX to it.third.centerY },
                rowBand(anchors) * 2,
            )
        ) return null

        // A missed header must not collapse an entire column. Recover its center
        // from the spacing between headers whose day-of-week is known.
        val spacing = observed.zipWithNext { a, b ->
            (b.third.centerX - a.third.centerX) / (b.first - a.first)
        }.filter { it > 0f }.sorted()
        val step = spacing.getOrNull(spacing.size / 2) ?: return null
        val origin = observed.map { it.third.centerX - it.first * step }.average().toFloat()
        val columns = dayNames.keys.mapIndexed { i, name -> name to (origin + i * step) }

        val timeRows = anchors.filter { it.third < columns.first().second }
            .ifEmpty { anchors }.sortedBy { it.second }
        if (timeRows.size < MIN_TIME_ROWS) return null

        val observedHeaders = observed.map { it.third }.toSet()
        val excluded = lines.filter { it in observedHeaders || timeRange.containsMatchIn(it.text) }.toSet()

        return buildString {
            columns.forEachIndexed { dayIndex, (day, _) ->
                if (dayIndex > 0) appendLine()
                appendLine(day)

                timeRows.forEachIndexed { rowIndex, row ->
                    val previousY = timeRows.getOrNull(rowIndex - 1)?.second
                    val nextY = timeRows.getOrNull(rowIndex + 1)?.second
                    val top = previousY?.let { (it + row.second) / 2f }
                        ?: row.second - rowBand(timeRows)
                    val bottom = nextY?.let { (it + row.second) / 2f }
                        ?: row.second + rowBand(timeRows)

                    val cell = lines.asSequence()
                        .filterNot(excluded::contains)
                        .filter { it.centerY >= top && it.centerY < bottom }
                        // Use the LEFT edge rather than the bounding-box center:
                        // long subject names extend beyond their narrow column.
                        .filter { nearestColumn(it.left.toFloat(), columns) == dayIndex }
                        .sortedWith(compareBy<PositionedLine>({ it.centerY }, { it.left }))
                        .toList()

                    // Timetable subjects sit above the printed time label's
                    // center; teacher names sit below it. Keep multi-line
                    // subjects together, and never turn an orphaned teacher
                    // line into its own class.
                    val subject = cell.filter { it.centerY < row.second }
                        .joinToString(" ") { it.text.trim() }
                        .replace(Regex("""\s+"""), " ").trim()
                    if (subject.isNotBlank()) {
                        append(row.first)
                        append(' ')
                        appendLine(subject)
                        val teacher = cell.filter { it.centerY >= row.second }
                            .joinToString(" ") { it.text.trim() }
                            .replace(Regex("""\s+"""), " ").trim()
                        if (teacher.isNotBlank()) appendLine(teacher)
                    }
                }
            }
        }.trim()
    }

    private fun nearestColumn(x: Float, columns: List<Pair<String, Float>>): Int =
        columns.indices.minByOrNull { index ->
            kotlin.math.abs(columns[index].second - x)
        } ?: 0

    private fun rowBand(rows: List<Triple<String, Float, Float>>): Float {
        val deltas = rows.zipWithNext { a, b -> kotlin.math.abs(b.second - a.second) }
            .filter { it > 0f }
        return (deltas.sorted().getOrNull(deltas.size / 2) ?: DEFAULT_ROW_BAND) / 2f
    }

    private fun normalizeRange(match: MatchResult): String {
        fun time(hour: String, minute: String): String =
            hour.toInt().toString().padStart(2, '0') + ":" + minute
        return time(match.groupValues[1], match.groupValues[2]) + "-" +
            time(match.groupValues[3], match.groupValues[4])
    }

    private const val MIN_DAY_HEADERS = 4
    private const val MIN_TIME_ROWS = 3
    private const val DEFAULT_ROW_BAND = 60f
}
