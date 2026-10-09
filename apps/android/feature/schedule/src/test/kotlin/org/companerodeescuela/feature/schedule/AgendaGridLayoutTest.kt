package org.companerodeescuela.feature.schedule

import kotlin.test.Test
import kotlin.test.assertEquals
import org.companerodeescuela.shared.contracts.ScheduleEntry

class AgendaGridLayoutTest {
    @Test
    fun `weekend entries do not duplicate columns`() {
        val days = weeklyAgendaDays(listOf(entry("weekend", "08:00", "09:00").copy(dayOfWeek = "SATURDAY")))
        assertEquals(1, days.count { it == "SATURDAY" })
        assertEquals(6, days.size)
    }

    @Test
    fun `Saturday stays visible even if a partially recognized import has no Saturday class`() {
        val days = weeklyAgendaDays(listOf(entry("monday", "08:00", "09:00")))
        assertEquals(listOf(
            "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY",
        ), days)
    }

    @Test
    fun `drag uses full column boundaries rather than lane width and supports multiple days`() {
        val days = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY")
        val widths = listOf(192f, 96f, 96f, 96f)
        assertEquals("MONDAY", weeklyDragTargetDay(days, widths, 0, 0, 2, 50f))
        assertEquals("THURSDAY", weeklyDragTargetDay(days, widths, 0, 0, 2, 350f))
        assertEquals("MONDAY", weeklyDragTargetDay(days, widths, 3, 0, 1, -1000f))
    }
    @Test
    fun `simultaneous blocks use distinct lanes and touching blocks share a lane`() {
        val entries = listOf(entry("A", "08:00", "10:00"), entry("B", "09:00", "11:00"), entry("C", "11:00", "12:00"))
        val layout = agendaGridLayout(entries)
        assertEquals(listOf(0, 1, 0), layout.map { it.lane })
        assertEquals(2, layout.first().laneCount)
        assertEquals(480, layout.first().startMinute)
        assertEquals(120, layout.first().durationMinutes)
    }

    @Test
    fun `short late blocks preserve their duration and malformed blocks are rejected`() {
        val layout = agendaGridLayout(listOf(entry("short", "23:30", "23:45"), entry("bad", "xx", "10:00"), entry("reverse", "12:00", "11:00")))
        assertEquals(1, layout.size)
        assertEquals(15, layout.single().durationMinutes)
        assertEquals(1410, layout.single().startMinute)
    }

    private fun entry(id: String, start: String, end: String) = ScheduleEntry(
        courseId = id, subjectCode = id, subjectName = id, groupName = "", teacherName = "",
        dayOfWeek = "MONDAY", startsAt = start, endsAt = end,
    )
}
