package org.companerodeescuela.feature.schedule

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimetableGeometryTest {
    @Test fun `vertical day list uses plain OCR fallback`() {
        assertFalse(timetableHeadersShareRow(listOf(50f to 10f, 55f to 100f, 53f to 200f, 60f to 300f), 40f))
    }
    @Test fun `distributed day headings in one row allow table reconstruction`() {
        assertTrue(timetableHeadersShareRow(listOf(100f to 10f, 200f to 15f, 300f to 10f, 400f to 20f), 40f))
        assertFalse(timetableHeadersShareRow(List(4) { 50f to 10f }, 40f))
    }
}
