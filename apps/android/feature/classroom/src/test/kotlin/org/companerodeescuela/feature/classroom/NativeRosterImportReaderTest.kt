package org.companerodeescuela.feature.classroom

import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NativeRosterImportReaderTest {
    @Test
    fun `xlsx shared strings inline strings and numeric ids use the same deduplicating roster policy`() {
        val shared =
            """<sst><si><t>ANA@example.edu</t></si><si><t>Nombre sin cuenta</t></si></sst>"""
        val sheet =
            """<worksheet><sheetData>""" +
                """<row><c t="s"><v>0</v></c><c t="s"><v>1</v></c></row>""" +
                """<row><c t="inlineStr"><is><t>roberto@example.edu</t></is></c></row>""" +
                """<row><c><v>1234567890</v></c></row>""" +
                """<row><c t="s"><v>0</v></c></row>""" +
                """</sheetData></worksheet>"""
        assertEquals(
            listOf("ana@example.edu", "roberto@example.edu", "1234567890"),
            parseNativeRosterXlsxIdentifiers(xlsx("xl/sharedStrings.xml" to shared, "xl/worksheets/sheet1.xml" to sheet)),
        )
    }

    @Test
    fun `xlsx rejects xml entity definitions instead of expanding untrusted content`() {
        val payload =
            """<!DOCTYPE worksheet [<!ENTITY external SYSTEM "file:///etc/passwd">]>""" +
                """<worksheet><sheetData><row><c t="inlineStr"><is><t>&external;</t></is></c></row></sheetData></worksheet>"""
        assertFailsWith<IOException> {
            parseNativeRosterXlsxIdentifiers(xlsx("xl/worksheets/sheet1.xml" to payload))
        }
    }

    @Test
    fun `xlsx rejects high expansion even in entries unrelated to the roster`() {
        assertFailsWith<IOException> {
            parseNativeRosterXlsxIdentifiers(xlsx("word/unused.xml" to "x".repeat(9 * 1024 * 1024)))
        }
    }

    private fun xlsx(vararg entries: Pair<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    @Test
    fun `parses emails and ids from reviewed CSV without treating names as identities`() {
        val input =
            listOf(
                "Nombre,Correo,Matrícula",
                "Ana María Pérez,ANA@example.edu,0001234567",
                "Ana María Pérez,ana@example.edu,0001234567",
                "Alumno sin cuenta,,",
                "Roberto Ruiz,roberto@example.edu,",
            ).joinToString("\n")
        assertEquals(
            listOf("ana@example.edu", "roberto@example.edu"),
            parseNativeRosterIdentifiers(input),
        )
    }

    @Test
    fun `parses registered account ids from plain text but not human names`() {
        assertEquals(
            listOf("1234567890", "abc12345-1234-1234-1234-123456789abc"),
            parseNativeRosterIdentifiers(
                "Juan Pérez\n1234567890\nabc12345-1234-1234-1234-123456789abc",
            ),
        )
    }

    @Test
    fun `bounded import rejects more than 100 student identities`() {
        assertFailsWith<IllegalArgumentException> {
            parseNativeRosterIdentifiers((1..101).joinToString("\n") { "alumno$it@example.edu" })
        }
    }
}
