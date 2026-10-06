package org.companerodeescuela.feature.schedule

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
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
                            val bitmap = Bitmap.createBitmap(
                                page.width.coerceAtLeast(1),
                                page.height.coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888,
                            )
                            bitmap.eraseColor(Color.WHITE)
                            page.render(
                                bitmap,
                                null,
                                null,
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
    }
}

private object TimetableOcrReconstructor {
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

    private data class PositionedLine(
        val text: String,
        val centerX: Float,
        val centerY: Float,
        val left: Int,
    )

    fun reconstruct(result: Text): String? {
        val lines = result.textBlocks
            .flatMap { it.lines }
            .mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                val value = line.text.trim().takeIf(String::isNotBlank) ?: return@mapNotNull null
                PositionedLine(
                    text = value,
                    centerX = box.exactCenterX(),
                    centerY = box.exactCenterY(),
                    left = box.left,
                )
            }

        val dayHeaders = dayNames.mapNotNull { (canonical, aliases) ->
            lines.firstOrNull { line ->
                val normalized = line.text.lowercase()
                aliases.any { alias ->
                    normalized == alias || normalized.startsWith("$alias ")
                }
            }?.let { canonical to it }
        }.sortedBy { it.second.centerX }

        val rowAnchors = lines
            .mapNotNull { line ->
                val match = timeRange.find(line.text) ?: return@mapNotNull null
                val normalized = normalizeRange(match)
                Triple(normalized, line.centerY, line.centerX)
            }
            .distinctBy { it.first + "@" + it.second.toInt() }
            .sortedBy { it.second }

        if (dayHeaders.size < MIN_DAY_HEADERS || rowAnchors.size < MIN_TIME_ROWS) return null

        val leftMostDay = dayHeaders.minOf { it.second.centerX }
        val timeRows = rowAnchors
            .filter { it.third < leftMostDay }
            .ifEmpty { rowAnchors }
            .sortedBy { it.second }

        if (timeRows.size < MIN_TIME_ROWS) return null

        val excluded = lines.filter { line ->
            dayHeaders.any { it.second === line } || timeRange.containsMatchIn(line.text)
        }.toSet()

        return buildString {
            dayHeaders.forEachIndexed { dayIndex, (dayName, header) ->
                if (dayIndex > 0) appendLine()
                appendLine(dayName)

                timeRows.forEachIndexed { rowIndex, row ->
                    val previousY = timeRows.getOrNull(rowIndex - 1)?.second
                    val nextY = timeRows.getOrNull(rowIndex + 1)?.second
                    val top = previousY?.let { (it + row.second) / 2f } ?: row.second - rowBand(timeRows)
                    val bottom = nextY?.let { (it + row.second) / 2f } ?: row.second + rowBand(timeRows)

                    val cellLines = lines
                        .asSequence()
                        .filterNot(excluded::contains)
                        .filter { it.centerY in top..<bottom }
                        .filter { nearestDay(it.centerX, dayHeaders) == header }
                        .sortedWith(compareBy<PositionedLine>({ it.centerY }, { it.left }))
                        .map { it.text.replace(Regex("""\s+"""), " ").trim() }
                        .filter(String::isNotBlank)
                        .toList()

                    if (cellLines.isNotEmpty()) {
                        append(row.first)
                        append(' ')
                        appendLine(cellLines.first())
                        cellLines.drop(1).forEach(::appendLine)
                    }
                }
            }
        }.trim()
    }

    private fun nearestDay(
        x: Float,
        headers: List<Pair<String, PositionedLine>>,
    ): PositionedLine? = headers.minByOrNull { (_, header) ->
        kotlin.math.abs(header.centerX - x)
    }?.second

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
