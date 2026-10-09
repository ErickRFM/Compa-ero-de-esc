package org.companerodeescuela.feature.schedule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimetableGridGeometryTest {
    @Test
    fun `detects seven columns and a separate hour rail from a PDF table`() {
        // Proportional to the rendered 792 x 612pt digital 9A PDF at 3x.
        val rules = listOf(36, 266, 562, 858, 1154, 1450, 1746, 2042, 2339)
        val columns = detectWeeklyPdfGrid(2376, 1836) { x, _ ->
            rules.any { rule -> x in rule - 1..rule + 1 }
        }
        requireNotNull(columns)
        assertEquals(7, columns.size)
        assertEquals(266f, columns[0].first)
        assertEquals(858f, columns[2].first)
        assertEquals(1450f, columns[4].first)
        assertEquals(1746f, columns[5].first)
        assertEquals(2042f, columns[5].second)
        assertTrue(columns.zipWithNext().all { (a, b) -> a.second == b.first })
    }

    @Test
    fun `refuses to guess columns on a non-grid document`() {
        assertNull(detectWeeklyPdfGrid(1000, 1500) { x, _ -> x == 300 || x == 700 })
    }
}
