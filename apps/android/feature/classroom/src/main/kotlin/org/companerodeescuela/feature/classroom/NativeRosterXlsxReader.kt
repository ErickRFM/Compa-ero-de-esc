package org.companerodeescuela.feature.classroom

import org.w3c.dom.Element
import org.w3c.dom.NodeList
import org.xml.sax.SAXException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Reads email/account identifiers from bounded OOXML worksheets. Never executes formulas,
 * expands external entities, or creates a student from a display name.
 */
internal fun parseNativeRosterXlsxIdentifiers(bytes: ByteArray): List<String> {
    if (bytes.size > MAX_XLSX_BYTES) throw IOException("El XLSX excede 2 MB")
    val xmlEntries = linkedMapOf<String, ByteArray>()
    var entryCount = 0
    var extractedBytes = 0L

    try {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                if (entryCount > MAX_XLSX_ENTRIES) throw IOException("El XLSX contiene demasiados archivos")
                val name = entry.name
                val keep = name == "xl/sharedStrings.xml" || XLSX_SHEET.matches(name)
                val output = if (keep && !entry.isDirectory) ByteArrayOutputStream() else null
                val buffer = ByteArray(8192)
                while (true) {
                    val read = zip.read(buffer)
                    if (read == -1) break
                    extractedBytes += read
                    if (extractedBytes > MAX_XLSX_EXTRACTED_BYTES) {
                        throw IOException("El XLSX excede el tamaño descomprimido permitido")
                    }
                    output?.write(buffer, 0, read)
                }
                if (output != null) xmlEntries[name] = output.toByteArray()
                zip.closeEntry()
            }
        }
    } catch (error: IOException) {
        throw error
    } catch (error: Exception) {
        throw IOException("El XLSX no es válido", error)
    }

    val sheetEntries =
        xmlEntries.entries
            .filter { XLSX_SHEET.matches(it.key) }
            .sortedBy { it.key }
    if (sheetEntries.isEmpty() || sheetEntries.size > MAX_XLSX_SHEETS) {
        throw IOException("El XLSX debe contener entre 1 y 10 hojas")
    }
    val shared =
        xmlEntries["xl/sharedStrings.xml"]
            ?.let { payload ->
                val nodes = parseSafeXlsxXml(payload).getElementsByTagName("si")
                if (nodes.length > MAX_XLSX_STRINGS) throw IOException("Demasiados textos en el XLSX")
                (0 until nodes.length).map { index ->
                    nodes.item(index).let { node ->
                        val texts = (node as Element).getElementsByTagName("t")
                        (0 until texts.length).joinToString("") { texts.item(it).textContent }
                    }
                }
            }.orEmpty()

    val rows = mutableListOf<String>()
    for ((_, payload) in sheetEntries) {
        val nodes = parseSafeXlsxXml(payload).getElementsByTagName("row")
        if (nodes.length > MAX_XLSX_ROWS || rows.size + nodes.length > MAX_XLSX_ROWS) {
            throw IOException("La lista tiene demasiadas filas")
        }
        for (rowIndex in 0 until nodes.length) {
            val cells = (nodes.item(rowIndex) as Element).getElementsByTagName("c")
            if (cells.length > MAX_XLSX_CELLS_PER_ROW) throw IOException("Demasiadas columnas en la lista")
            val values = mutableListOf<String>()
            for (cellIndex in 0 until cells.length) {
                val cell = cells.item(cellIndex) as Element
                val value =
                    when (cell.getAttribute("t")) {
                        "s" -> {
                            val index = cell.getElementsByTagName("v").firstText()?.toIntOrNull()
                            if (index == null) "" else shared.getOrNull(index).orEmpty()
                        }
                        "inlineStr" -> cell.getElementsByTagName("t").allText()
                        else -> cell.getElementsByTagName("v").firstText().orEmpty()
                    }
                values += value.take(MAX_XLSX_CELL_LENGTH)
            }
            rows += values.joinToString("\t")
        }
    }
    return parseNativeRosterIdentifiers(rows.joinToString("\n"))
}

private const val MAX_XLSX_BYTES = 2 * 1024 * 1024
private const val MAX_XLSX_EXTRACTED_BYTES = 8L * 1024 * 1024
private const val MAX_XLSX_ENTRIES = 150
private const val MAX_XLSX_SHEETS = 10
private const val MAX_XLSX_STRINGS = 5000
private const val MAX_XLSX_ROWS = 2500
private const val MAX_XLSX_CELLS_PER_ROW = 64
private const val MAX_XLSX_CELL_LENGTH = 512
private val XLSX_SHEET = Regex("""xl/worksheets/sheet[1-9][0-9]*\.xml""")

private fun parseSafeXlsxXml(payload: ByteArray): org.w3c.dom.Document {
    // Do not rely on Android's partial JAXP secure-processing implementation.
    if (payload.any { it == 0.toByte() }) throw IOException("Sólo se admite XML UTF-8 en XLSX")
    val source = payload.toString(Charsets.UTF_8)
    if (Regex("""<!\s*(DOCTYPE|ENTITY)""", RegexOption.IGNORE_CASE).containsMatchIn(source)) {
        throw IOException("El XLSX contiene XML no permitido")
    }
    return try {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        factory.isExpandEntityReferences = false
        val builder = factory.newDocumentBuilder()
        builder.setEntityResolver { _, _ -> throw SAXException("Entidades externas no permitidas") }
        builder.parse(ByteArrayInputStream(payload))
    } catch (error: IOException) {
        throw error
    } catch (error: Exception) {
        throw IOException("No se pudo interpretar la hoja XLSX", error)
    }
}

private fun NodeList.firstText(): String? = item(0)?.textContent

private fun NodeList.allText(): String = (0 until length).joinToString("") { item(it).textContent }
