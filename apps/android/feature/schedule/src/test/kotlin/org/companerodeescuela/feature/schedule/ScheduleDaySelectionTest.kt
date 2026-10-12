package org.companerodeescuela.feature.schedule

import kotlin.test.Test
import kotlin.test.assertEquals

class ScheduleDaySelectionTest {
    @Test
    fun `selected Saturday survives data refresh`() {
        assertEquals("SATURDAY", resolveScheduleDay("SATURDAY", listOf("MONDAY", "SATURDAY")))
    }

    @Test
    fun `selection falls back without modifying the saved preference`() {
        val preferred = "SATURDAY"
        assertEquals("MONDAY", resolveScheduleDay(preferred, listOf("MONDAY", "TUESDAY")))
        assertEquals("SATURDAY", resolveScheduleDay(preferred, listOf("MONDAY", "SATURDAY")))
    }

    @Test
    fun `empty agenda renders Monday without losing state`() {
        assertEquals("MONDAY", resolveScheduleDay("SUNDAY", emptyList()))
    }
}
