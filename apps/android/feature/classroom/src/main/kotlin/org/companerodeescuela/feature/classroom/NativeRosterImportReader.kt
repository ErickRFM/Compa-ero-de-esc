package org.companerodeescuela.feature.classroom

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Preview of *account identifiers*, not a list of official school enrollments. */
internal class NativeRosterImportReader(
    private val context: Context,
) {
    suspend fun read(uri: Uri): List<String> =
        withContext(Dispatchers.IO) {
            val content = context.contentResolver.openInputStream(uri)
                ?: throw IOException("No se pudo leer el archivo")
            val bytes = content.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    if (output.size() > MAX_BYTES) throw IOException("La lista excede 2 MB")
                }
                output.toByteArray()
            }
            val mime = context.contentResolver.getType(uri).orEmpty()
            val pdf = mime.equals("application/pdf", ignoreCase = true) ||
                uri.lastPathSegment?.endsWith(".pdf", ignoreCase = true) == true
            val text =
                if (pdf) {
                    PDFBoxResourceLoader.init(context.applicationContext)
                    PDDocument.load(bytes).use { document ->
                        if (document.numberOfPages !in 1..MAX_PAGES) {
                            throw IOException("El PDF debe tener entre 1 y 10 páginas")
                        }
                        PDFTextStripper().getText(document)
                    }
                } else {
                    bytes.toString(Charsets.UTF_8)
                }
            val identifiers = parseNativeRosterIdentifiers(text)
            if (identifiers.isEmpty()) {
                throw IOException("No se reconocieron correos o identificadores; revisa la lista")
            }
            identifiers
        }

    private companion object {
        const val MAX_BYTES = 2 * 1024 * 1024
        const val MAX_PAGES = 10
    }
}

/** Names alone are intentionally not converted into verified student identities. */
internal fun parseNativeRosterIdentifiers(text: String): List<String> {
    val email = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")
    val id = Regex("""(?:[0-9]{5,20}|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})""")
    val identifiers = linkedSetOf<String>()
    for (line in text.lineSequence()) {
        val row = line.trim()
        if (row.isEmpty()) continue
        val candidate = email.find(row)?.value ?: row.split(',', ';', '\t', ' ')
            .firstOrNull { id.matches(it.trim()) }
            ?.trim()
        if (candidate != null) {
            identifiers += candidate.lowercase()
            if (identifiers.size > 100) throw IllegalArgumentException("Máximo 100 alumnos por lote")
        }
    }
    return identifiers.toList()
}
