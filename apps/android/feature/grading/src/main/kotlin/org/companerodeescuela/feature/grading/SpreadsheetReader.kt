package org.companerodeescuela.feature.grading

import org.companerodeescuela.shared.contracts.GradeImportPreview
import org.companerodeescuela.shared.contracts.GradeImportRow
import org.w3c.dom.Element
import org.xml.sax.SAXException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

object SpreadsheetReader {
    private const val MAX_INPUT_BYTES = 5 * 1024 * 1024
    private const val MAX_ENTRY_BYTES = 4 * 1024 * 1024
    private const val MAX_UNCOMPRESSED_BYTES = 12 * 1024 * 1024
    private const val MAX_ZIP_ENTRIES = 128
    private const val MAX_ROWS = 5_001 // header + at most 5,000 students
    private const val MAX_COLUMNS = 100

    private val idAliases =
        setOf("matricula", "matrícula", "studentid", "student_id", "id", "no.control", "numero de control", "número de control")
    private val nameAliases = setOf("nombre", "alumno", "estudiante", "name")

    fun read(
        fileName: String,
        bytes: ByteArray,
    ): GradeImportPreview {
        require(bytes.isNotEmpty() && bytes.size <= MAX_INPUT_BYTES) {
            "El archivo debe tener como máximo 5 MB."
        }
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "xlsx" -> readXlsx(bytes)
            "csv" -> readCsv(bytes.toString(Charsets.UTF_8))
            "xls" -> throw IllegalArgumentException("El formato .xls antiguo no es compatible. Guárdalo como .xlsx.")
            else -> throw IllegalArgumentException("Selecciona un archivo .xlsx o .csv.")
        }
    }

    private fun readXlsx(bytes: ByteArray): GradeImportPreview {
        val entries = mutableMapOf<String, ByteArray>()
        var entryCount = 0
        var uncompressedBytes = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            val buffer = ByteArray(8192)
            var entry = zip.nextEntry
            while (entry != null) {
                entryCount++
                require(entryCount <= MAX_ZIP_ENTRIES) { "El Excel contiene demasiados archivos internos." }
                if (!entry.isDirectory) {
                    // Count and limit *every* entry, including files we do not parse.
                    // Never call ZipInputStream.readBytes(): compressed ZIP bombs are unbounded.
                    var entryBytes = 0
                    val wanted =
                        entry.name == "xl/sharedStrings.xml" ||
                            entry.name == "xl/worksheets/sheet1.xml"
                    val output = if (wanted) ByteArrayOutputStream() else null
                    while (true) {
                        val count = zip.read(buffer)
                        if (count == -1) break
                        entryBytes += count
                        uncompressedBytes += count
                        require(entryBytes <= MAX_ENTRY_BYTES && uncompressedBytes <= MAX_UNCOMPRESSED_BYTES) {
                            "El Excel contiene contenido comprimido demasiado grande."
                        }
                        output?.write(buffer, 0, count)
                    }
                    if (output != null) {
                        require(entry.name !in entries) { "El Excel contiene entradas XML duplicadas." }
                        entries[entry.name] = output.toByteArray()
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        val sheet =
            entries["xl/worksheets/sheet1.xml"]
                ?: throw IllegalArgumentException("El Excel no contiene una primera hoja legible.")
        val table = parseSheet(sheet, sharedStrings)
        if (table.isEmpty()) throw IllegalArgumentException("El Excel está vacío.")
        return toPreview(table.first(), table.drop(1))
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val doc = newDocument(bytes)
        val nodes = doc.getElementsByTagName("si")
        return (0 until nodes.length).map { index ->
            nodes
                .item(index)
                .textContent
                .orEmpty()
                .trim()
        }
    }

    private fun parseSheet(
        bytes: ByteArray,
        sharedStrings: List<String>,
    ): List<List<String>> {
        val doc = newDocument(bytes)
        val rows = doc.getElementsByTagName("row")
        require(rows.length <= MAX_ROWS) { "El Excel supera el máximo de 5,000 alumnos." }
        return (0 until rows.length).map { rowIndex ->
            val row = rows.item(rowIndex) as Element
            val cells = row.getElementsByTagName("c")
            require(cells.length <= MAX_COLUMNS) { "El Excel supera las 100 columnas permitidas." }
            val byColumn = mutableMapOf<Int, String>()
            var maxColumn = -1
            for (cellIndex in 0 until cells.length) {
                val cell = cells.item(cellIndex) as Element
                val reference = cell.getAttribute("r")
                val column = columnIndex(reference.takeWhile { it.isLetter() })
                require(column in 0 until MAX_COLUMNS) { "El Excel contiene columnas fuera de rango." }
                maxColumn = maxOf(maxColumn, column)
                val type = cell.getAttribute("t")
                val raw =
                    cell.getElementsByTagName("v").item(0)?.textContent
                        ?: cell.getElementsByTagName("t").item(0)?.textContent
                        ?: ""
                byColumn[column] =
                    if (type == "s") {
                        sharedStrings.getOrNull(raw.toIntOrNull() ?: -1).orEmpty()
                    } else {
                        raw
                    }
            }
            (0..maxColumn.coerceAtLeast(0)).map { byColumn[it].orEmpty().trim() }
        }
    }

    private fun columnIndex(letters: String): Int {
        var value = 0
        require(letters.isNotBlank() && letters.length <= 3) { "Referencia de celda inválida." }
        letters.uppercase().forEach {
            require(it in 'A'..'Z') { "Referencia de celda inválida." }
            value = value * 26 + (it - 'A' + 1)
        }
        return value - 1
    }

    private fun readCsv(text: String): GradeImportPreview {
        val clean = text.removePrefix("\uFEFF")
        require(clean.isNotBlank()) { "El CSV está vacío." }
        val firstLine = clean.lineSequence().first()
        val delimiter = if (firstLine.count { it == ';' } > firstLine.count { it == ',' }) ';' else ','
        val rows = parseCsv(clean, delimiter).filter { row -> row.any(String::isNotBlank) }
        require(rows.size <= MAX_ROWS) { "El CSV supera el máximo de 5,000 alumnos." }
        require(rows.isNotEmpty()) { "El CSV está vacío." }
        return toPreview(rows.first(), rows.drop(1))
    }

    private fun parseCsv(
        text: String,
        delimiter: Char,
    ): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0

        fun finishCell() {
            row += cell.toString().trim()
            cell.setLength(0)
        }

        fun finishRow() {
            finishCell()
            require(rows.size < MAX_ROWS) { "El CSV supera el máximo de 5,000 alumnos." }
            rows += row.toList()
            row.clear()
        }
        while (index < text.length) {
            val char = text[index]
            when {
                char == '"' && quoted && text.getOrNull(index + 1) == '"' -> {
                    cell.append('"')
                    index++
                }
                char == '"' -> quoted = !quoted
                char == delimiter && !quoted -> finishCell()
                char == '\n' && !quoted -> finishRow()
                char == '\r' && !quoted -> {
                    finishRow()
                    if (text.getOrNull(index + 1) == '\n') index++
                }
                else -> cell.append(char)
            }
            index++
        }
        require(!quoted) { "El CSV contiene comillas sin cerrar." }
        if (cell.isNotEmpty() || row.isNotEmpty()) finishRow()
        return rows
    }

    private fun toPreview(
        headers: List<String>,
        body: List<List<String>>,
    ): GradeImportPreview {
        require(headers.size in 2..MAX_COLUMNS && body.size < MAX_ROWS) {
            "La tabla supera los límites de filas o columnas."
        }
        val normalized = headers.map { it.lowercase().trim() }
        require(normalized.none(String::isBlank)) { "El archivo contiene encabezados vacíos." }
        require(normalized.distinct().size == normalized.size) { "El archivo contiene encabezados duplicados." }
        val idIndex = normalized.indexOfFirst { it in idAliases }
        require(idIndex >= 0) { "El archivo necesita una columna Matrícula/ID." }
        val nameIndex = normalized.indexOfFirst { it in nameAliases }
        val gradeIndexes = headers.indices.filter { it != idIndex && it != nameIndex && headers[it].isNotBlank() }
        require(gradeIndexes.isNotEmpty()) { "El archivo necesita al menos una columna de calificaciones." }
        val warnings = mutableListOf<String>()
        val ids = mutableSetOf<String>()
        val rows =
            body.mapIndexedNotNull { rowIndex, row ->
                if (row.size != headers.size) warnings += "Fila ${rowIndex + 2}: el número de celdas no coincide con los encabezados."
                val studentId = row.getOrNull(idIndex).orEmpty().trim()
                if (studentId.isBlank()) {
                    warnings += "Fila ${rowIndex + 2}: falta matrícula."
                    return@mapIndexedNotNull null
                }
                if (!ids.add(studentId)) warnings += "Fila ${rowIndex + 2}: matrícula duplicada ($studentId)."
                val values = linkedMapOf<String, Double?>()
                gradeIndexes.forEach { column ->
                    val raw = row.getOrNull(column)?.trim().orEmpty()
                    val number = raw.replace(',', '.').toDoubleOrNull()
                    if (raw.isBlank()) {
                        warnings += "Fila ${rowIndex + 2}: ${headers[column]} tiene una celda vacía."
                    } else if (number == null) {
                        warnings += "Fila ${rowIndex + 2}: ${headers[column]} no es numérica."
                    } else if (!number.isFinite() || number !in 0.0..10.0) {
                        warnings += "Fila ${rowIndex + 2}: ${headers[column]} debe estar entre 0 y 10."
                    }
                    values[headers[column]] = number
                }
                GradeImportRow(
                    studentId = studentId,
                    displayName = nameIndex.takeIf { it >= 0 }?.let { row.getOrNull(it) },
                    values = values,
                )
            }
        return GradeImportPreview(
            columns = gradeIndexes.map(headers::get),
            rows = rows,
            warnings = warnings.distinct(),
        )
    }

    private fun newDocument(bytes: ByteArray): org.w3c.dom.Document {
        // Android's built-in DOM parser does not implement the JAXP
        // disallow-doctype-decl feature. Reject markup before parsing on *both*
        // Android and the JVM; reject non-UTF-8 bytes to prevent bypasses.
        val decoded =
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        require('\u0000' !in decoded && !Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(decoded)) {
            "El Excel contiene declaraciones XML no permitidas."
        }
        val factory =
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                // Optional parser capabilities differ across JAXP and Android;
                // pre-validation and the mandatory entity resolver provide the
                // consistent fail-closed layer.
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
                runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
                runCatching { isXIncludeAware = false }
                isExpandEntityReferences = false
                runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
                runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") }
            }
        val builder = factory.newDocumentBuilder()
        builder.setEntityResolver { _, _ -> throw SAXException("External XML resources are not allowed") }
        return builder.parse(ByteArrayInputStream(bytes))
    }
}
