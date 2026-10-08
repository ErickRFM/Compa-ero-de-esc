package org.companerodeescuela.feature.grading

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.companerodeescuela.shared.contracts.GradeImportPreview
import org.companerodeescuela.shared.contracts.GradeImportRow
import org.w3c.dom.Element

object SpreadsheetReader {
    private val idAliases = setOf("matricula", "matrícula", "studentid", "student_id", "id", "no.control", "numero de control", "número de control")
    private val nameAliases = setOf("nombre", "alumno", "estudiante", "name")

    fun read(fileName: String, bytes: ByteArray): GradeImportPreview =
        when (fileName.substringAfterLast('.', "").lowercase()) {
            "xlsx" -> readXlsx(bytes)
            "csv" -> readCsv(bytes.toString(Charsets.UTF_8))
            "xls" -> throw IllegalArgumentException("El formato .xls antiguo no es compatible. Guárdalo como .xlsx.")
            else -> throw IllegalArgumentException("Selecciona un archivo .xlsx o .csv.")
        }

    private fun readXlsx(bytes: ByteArray): GradeImportPreview {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
                entry = zip.nextEntry
            }
        }
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        val sheet = entries["xl/worksheets/sheet1.xml"]
            ?: throw IllegalArgumentException("El Excel no contiene una primera hoja legible.")
        val table = parseSheet(sheet, sharedStrings)
        if (table.isEmpty()) throw IllegalArgumentException("El Excel está vacío.")
        return toPreview(table.first(), table.drop(1))
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val doc = newDocument(bytes)
        val nodes = doc.getElementsByTagName("si")
        return (0 until nodes.length).map { index -> nodes.item(index).textContent.orEmpty().trim() }
    }

    private fun parseSheet(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val doc = newDocument(bytes)
        val rows = doc.getElementsByTagName("row")
        return (0 until rows.length).map { rowIndex ->
            val row = rows.item(rowIndex) as Element
            val cells = row.getElementsByTagName("c")
            val byColumn = mutableMapOf<Int, String>()
            var maxColumn = -1
            for (cellIndex in 0 until cells.length) {
                val cell = cells.item(cellIndex) as Element
                val reference = cell.getAttribute("r")
                val column = columnIndex(reference.takeWhile { it.isLetter() })
                maxColumn = maxOf(maxColumn, column)
                val type = cell.getAttribute("t")
                val raw = cell.getElementsByTagName("v").item(0)?.textContent
                    ?: cell.getElementsByTagName("t").item(0)?.textContent
                    ?: ""
                byColumn[column] = if (type == "s") {
                    sharedStrings.getOrNull(raw.toIntOrNull() ?: -1).orEmpty()
                } else raw
            }
            (0..maxColumn.coerceAtLeast(0)).map { byColumn[it].orEmpty().trim() }
        }
    }

    private fun columnIndex(letters: String): Int {
        var value = 0
        letters.uppercase().forEach { value = value * 26 + (it - 'A' + 1) }
        return (value - 1).coerceAtLeast(0)
    }

    private fun readCsv(text: String): GradeImportPreview {
        val clean = text.removePrefix("\uFEFF")
        require(clean.isNotBlank()) { "El CSV está vacío." }
        val firstLine = clean.lineSequence().first()
        val delimiter = if (firstLine.count { it == ';' } > firstLine.count { it == ',' }) ';' else ','
        val rows = parseCsv(clean, delimiter).filter { row -> row.any(String::isNotBlank) }
        require(rows.isNotEmpty()) { "El CSV está vacío." }
        return toPreview(rows.first(), rows.drop(1))
    }

    private fun parseCsv(text: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0
        fun finishCell() { row += cell.toString().trim(); cell.setLength(0) }
        fun finishRow() { finishCell(); rows += row.toList(); row.clear() }
        while (index < text.length) {
            val char = text[index]
            when {
                char == '"' && quoted && text.getOrNull(index + 1) == '"' -> { cell.append('"'); index++ }
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

    private fun toPreview(headers: List<String>, body: List<List<String>>): GradeImportPreview {
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
        val rows = body.mapIndexedNotNull { rowIndex, row ->
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

    private fun newDocument(bytes: ByteArray) =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
}
