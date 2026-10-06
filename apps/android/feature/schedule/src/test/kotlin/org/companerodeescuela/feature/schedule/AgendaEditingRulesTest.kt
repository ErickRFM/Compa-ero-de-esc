package org.companerodeescuela.feature.schedule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.companerodeescuela.shared.contracts.ScheduleEntry
import org.companerodeescuela.shared.contracts.ScheduleSource

class AgendaEditingRulesTest {
    @Test
    fun `move preserves duration and snaps to fifteen minutes`() {
        val entry = entry("A", "MONDAY", "19:00", "20:30")
        val proposal = AgendaEditingRules.proposeMove(
            entry = entry,
            targetDay = "WEDNESDAY",
            targetStart = "17:07",
            existing = listOf(entry),
        )!!

        assertEquals("17:00", proposal.targetStart)
        assertEquals("18:30", proposal.targetEnd)
        assertEquals("WEDNESDAY", proposal.targetDay)
    }

    @Test
    fun `adjacent classes are not conflicts`() {
        assertTrue(!AgendaEditingRules.overlaps("09:00", "10:00", "10:00", "11:00"))
    }

    @Test
    fun `overlap is reported in move proposal`() {
        val moving = entry("A", "MONDAY", "09:00", "10:00")
        val existing = entry("B", "TUESDAY", "10:00", "11:00")

        val proposal = AgendaEditingRules.proposeMove(
            moving,
            targetDay = "TUESDAY",
            targetStart = "10:15",
            existing = listOf(moving, existing),
        )!!

        assertEquals(1, proposal.conflicts.size)
    }

    @Test
    fun `horizontal drag changes only one academic day`() {
        assertEquals(
            "TUESDAY",
            AgendaEditingRules.adjacentDay("MONDAY", 200f, 80f),
        )
        assertEquals(
            "MONDAY",
            AgendaEditingRules.adjacentDay("MONDAY", -200f, 80f),
        )
    }

    private fun entry(
        id: String,
        day: String,
        start: String,
        end: String,
    ) = ScheduleEntry(
        courseId = id,
        subjectCode = id,
        subjectName = id,
        groupName = "",
        teacherName = "",
        dayOfWeek = day,
        startsAt = start,
        endsAt = end,
        source = ScheduleSource.MANUAL,
    )
}
