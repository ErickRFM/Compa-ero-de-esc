package org.companerodeescuela.feature.classroom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.time.LocalDate

class AcademicAdminDraftsTest {
    private val today = LocalDate.of(2026, 11, 25)

    @Test
    fun `blank schedule fields are not reported until typed`() {
        assertNull(scheduleDraftIssue("", ""))
        assertNull(scheduleDraftIssue("08:00", ""))
        assertFalse(scheduleDraftReady("08:00", ""))
    }

    @Test
    fun `schedule times must be strict 24 hour HH MM`() {
        assertEquals(ScheduleDraftIssue.START_FORMAT, scheduleDraftIssue("8:00", "09:00"))
        assertEquals(ScheduleDraftIssue.START_FORMAT, scheduleDraftIssue("24:00", "09:00"))
        assertEquals(ScheduleDraftIssue.END_FORMAT, scheduleDraftIssue("08:00", "09:60"))
        assertEquals(ScheduleDraftIssue.END_FORMAT, scheduleDraftIssue("08:00", "9h"))
    }

    @Test
    fun `schedule end must be strictly after start, matching the view model`() {
        assertEquals(ScheduleDraftIssue.END_NOT_AFTER_START, scheduleDraftIssue("09:00", "09:00"))
        assertEquals(ScheduleDraftIssue.END_NOT_AFTER_START, scheduleDraftIssue("10:00", "09:00"))
        assertTrue(scheduleDraftReady("07:00", "07:50"))
        assertTrue(scheduleDraftReady("23:00", "23:59"))
    }

    @Test
    fun `group dates require ISO format`() {
        assertEquals(GroupDatesIssue.START_FORMAT, groupDatesIssue("15/01/2027", "2027-06-30", today))
        assertEquals(GroupDatesIssue.START_FORMAT, groupDatesIssue("2027-02-30", "2027-06-30", today))
        assertEquals(GroupDatesIssue.END_FORMAT, groupDatesIssue("2027-01-15", "2027-6-30", today))
        assertNull(groupDatesIssue("", "", today))
    }

    @Test
    fun `group end must follow start and still be current`() {
        assertEquals(GroupDatesIssue.END_NOT_AFTER_START, groupDatesIssue("2027-06-30", "2027-01-15", today))
        assertEquals(GroupDatesIssue.END_NOT_AFTER_START, groupDatesIssue("2027-01-15", "2027-01-15", today))
        assertEquals(GroupDatesIssue.END_IN_PAST, groupDatesIssue("2026-08-01", "2026-11-25", today))
        assertEquals(GroupDatesIssue.END_IN_PAST, groupDatesIssue("", "2026-01-01", today))
        assertTrue(groupDatesReady("2026-08-24", "2027-01-15", today))
        assertFalse(groupDatesReady("2026-08-24", "", today))
    }
}
