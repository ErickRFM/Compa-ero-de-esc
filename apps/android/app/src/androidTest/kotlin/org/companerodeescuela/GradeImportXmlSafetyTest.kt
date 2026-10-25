package org.companerodeescuela

import org.companerodeescuela.feature.grading.SpreadsheetReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Exercises Android's actual XML parser, which differs from the JVM test implementation. */
class GradeImportXmlSafetyTest {
    @Test fun ordinaryExcelParsesOnAndroidWithoutExternalXmlSupport() {
        val worksheet =
            """
            <worksheet><sheetData>
                <row><c r="A1" t="inlineStr"><is><t>Matricula</t></is></c><c r="B1" t="inlineStr"><is><t>Proyecto</t></is></c></row>
                <row><c r="A2" t="inlineStr"><is><t>A001</t></is></c><c r="B2"><v>8.5</v></c></row>
            </sheetData></worksheet>
            """.trimIndent()
        val result = SpreadsheetReader.read("gradebook.xlsx", excel(worksheet))
        assertEquals("A001", result.rows.single().studentId)
        assertEquals(8.5, result.rows.single().values["Proyecto"])
    }

    @Test fun externalXmlEntitiesAreRejectedOnAndroid() {
        val worksheet =
            "<!DOCTYPE worksheet [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>" +
                "<worksheet><sheetData><row><c r=\"A1\"><v>&xxe;</v></c></row></sheetData></worksheet>"
        val rejected = runCatching { SpreadsheetReader.read("gradebook.xlsx", excel(worksheet)) }.isFailure
        assertTrue("Android parser must reject DTD/external entities", rejected)
    }

    private fun excel(sheet: String): ByteArray {
        val result = ByteArrayOutputStream()
        ZipOutputStream(result).use { zip ->
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write(sheet.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return result.toByteArray()
    }
}
