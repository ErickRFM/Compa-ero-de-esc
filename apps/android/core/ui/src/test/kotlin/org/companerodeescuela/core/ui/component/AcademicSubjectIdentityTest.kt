package org.companerodeescuela.core.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AcademicSubjectIdentityTest {
    @Test
    fun `official subject codes are stable across case and whitespace`() {
        assertEquals("MAT-1", AcademicSubjectColors.subjectKey(" mat-1 ", "Matemáticas"))
        assertEquals(AcademicSubjectColors.subjectKey("MAT-1", "Matemáticas"), AcademicSubjectColors.subjectKey("mat-1", "Otro nombre"))
    }

    @Test
    fun `personal entries are distinguished by subject instead of placeholder code`() {
        val mathematics = AcademicSubjectColors.subjectKey("PERSONAL", " Matemáticas ")
        assertEquals(mathematics, AcademicSubjectColors.subjectKey("personal", "matemáticas"))
        assertEquals(mathematics, AcademicSubjectColors.subjectKey("", "matemáticas"))
        assertNotEquals(mathematics, AcademicSubjectColors.subjectKey("PERSONAL", "Inglés"))
    }
}
