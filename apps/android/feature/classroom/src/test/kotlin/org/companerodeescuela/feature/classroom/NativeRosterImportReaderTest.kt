package org.companerodeescuela.feature.classroom

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NativeRosterImportReaderTest {
    @Test
    fun `parses emails and ids from reviewed CSV without treating names as identities`() {
        val input = """
            Nombre,Correo,Matrícula
            Ana María Pérez,ANA@example.edu,0001234567
            Ana María Pérez,ana@example.edu,0001234567
            Alumno sin cuenta,,
            Roberto Ruiz,roberto@example.edu,
        """.trimIndent()
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
