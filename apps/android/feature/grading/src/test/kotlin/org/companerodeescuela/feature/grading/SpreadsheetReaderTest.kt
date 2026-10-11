package org.companerodeescuela.feature.grading

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SpreadsheetReaderTest {
    @Test
    fun readsCsvByStudentIdAndGradeColumns() {
        val csv = """
            Matricula,Nombre,Proyecto,Examen
            A001,Ana Lopez,9.5,8
            A002,Luis Perez,10,7.5
        """.trimIndent()

        val preview = SpreadsheetReader.read("calificaciones.csv", csv.toByteArray())

        assertEquals(listOf("Proyecto", "Examen"), preview.columns)
        assertEquals(2, preview.rows.size)
        assertEquals("A001", preview.rows.first().studentId)
        assertEquals(9.5, preview.rows.first().values["Proyecto"])
        assertTrue(preview.warnings.isEmpty())
    }
    @Test
    fun rejectsOversizedCsvBeforeParsing() {
        val bytes = ByteArray(5 * 1024 * 1024 + 1) { 'A'.code.toByte() }
        val error = assertFailsWith<IllegalArgumentException> {
            SpreadsheetReader.read("calificaciones.csv", bytes)
        }
        assertTrue(error.message.orEmpty().contains("5 MB"))
    }

    @Test
    fun rejectsLargeCompressedXmlBeforeBuildingDom() {
        val zip = xlsx(mapOf(
            "xl/worksheets/sheet1.xml" to "A".repeat(4 * 1024 * 1024 + 5),
        ))
        // A highly compressible ZIP still counts against expanded-entry limits.
        assertTrue(zip.size < 5 * 1024 * 1024)
        val error = assertFailsWith<IllegalArgumentException> {
            SpreadsheetReader.read("grades.xlsx", zip)
        }
        assertTrue(error.message.orEmpty().contains("comprimido"))
    }

    @Test
    fun rejectsExcessiveZipEntryCountIncludingIgnoredFiles() {
        val entries = (0..129).associate { "other/part$it.xml" to "x" }
        assertFailsWith<IllegalArgumentException> {
            SpreadsheetReader.read("grades.xlsx", xlsx(entries))
        }
    }

    @Test
    fun rejectsXmlExternalEntityDeclarations() {
        val malicious = "<!DOCTYPE worksheet [<!ENTITY leak SYSTEM \"file:///etc/passwd\">]><worksheet><sheetData><row><c r=\"A1\"><v>&leak;</v></c></row></sheetData></worksheet>"
        assertFailsWith<Exception> {
            SpreadsheetReader.read("grades.xlsx", xlsx(mapOf("xl/worksheets/sheet1.xml" to malicious)))
        }
    }

    @Test
    fun keepsOrdinaryXlsxImportWorking() {
        val sheet = """<worksheet><sheetData>
            <row><c r="A1" t="inlineStr"><is><t>Matricula</t></is></c><c r="B1" t="inlineStr"><is><t>Proyecto</t></is></c></row>
            <row><c r="A2" t="inlineStr"><is><t>A001</t></is></c><c r="B2"><v>9.5</v></c></row>
            </sheetData></worksheet>""".trimIndent()
        val result = SpreadsheetReader.read("grades.xlsx", xlsx(mapOf("xl/worksheets/sheet1.xml" to sheet)))
        assertEquals("A001", result.rows.single().studentId)
        assertEquals(9.5, result.rows.single().values["Proyecto"])
    }

    private fun xlsx(entries: Map<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, contents) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(contents.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

}
