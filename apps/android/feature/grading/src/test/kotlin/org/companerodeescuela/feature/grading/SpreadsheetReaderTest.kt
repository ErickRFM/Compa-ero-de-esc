package org.companerodeescuela.feature.grading

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
}
