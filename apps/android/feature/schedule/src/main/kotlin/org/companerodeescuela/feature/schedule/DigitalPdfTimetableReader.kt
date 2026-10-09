package org.companerodeescuela.feature.schedule

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.companerodeescuela.core.academic.PersonalScheduleDraft

/**
 * Native-text-first PDF reading. No camera, OCR, internet, institution API or
 * language model is required for digitally generated timetable PDFs.
 *
 * The existing ML Kit path remains the fallback for scans, photos and formats
 * that do not contain a recognizable native-text timetable.
 */
internal class DigitalPdfTimetableReader(private val context: Context) {
    suspend fun extract(uri: Uri): List<PersonalScheduleDraft>? = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context.applicationContext)
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes()
        } ?: throw IOException("No se pudo abrir el PDF")
        if (bytes.size > MAX_PDF_BYTES) return@withContext null

        PDDocument.load(bytes).use { document ->
            val entries = mutableListOf<PersonalScheduleDraft>()
            for (pageIndex in 0 until minOf(document.numberOfPages, MAX_PAGES)) {
                val glyphs = mutableListOf<DigitalPdfGlyph>()
                val extractor = object : PDFTextStripper() {
                    override fun processTextPosition(position: TextPosition) {
                        val text = position.unicode
                        if (!text.isNullOrEmpty()) {
                            glyphs.add(
                                DigitalPdfGlyph(
                                    text = text,
                                    x = position.xDirAdj,
                                    y = position.yDirAdj,
                                    width = position.widthDirAdj,
                                    fontSize = position.fontSizeInPt.toFloat(),
                                ),
                            )
                        }
                        super.processTextPosition(position)
                    }
                }
                extractor.setStartPage(pageIndex + 1)
                extractor.setEndPage(pageIndex + 1)
                extractor.setSortByPosition(true)
                extractor.getText(document)
                DigitalPdfTimetableParser.parseGlyphs(glyphs)?.let(entries::addAll)
            }
            entries.takeIf { it.isNotEmpty() }
        }
    }

    private companion object {
        const val MAX_PAGES = 4
        const val MAX_PDF_BYTES = 12 * 1024 * 1024
    }
}
