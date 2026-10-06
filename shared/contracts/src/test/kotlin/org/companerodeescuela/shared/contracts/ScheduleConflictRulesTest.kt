package org.companerodeescuela.shared.contracts

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScheduleConflictRulesTest {
    @Test
    fun `adjacent classes do not conflict`() {
        val first = block("a", "09:00", "10:00")
        val second = block("b", "10:00", "11:00")

        assertFalse(ScheduleConflictRules.overlaps(first, second))
    }

    @Test
    fun `overlapping classes conflict`() {
        val first = block("a", "09:00", "10:30")
        val second = block("b", "10:00", "11:00")

        assertTrue(ScheduleConflictRules.overlaps(first, second))
    }

    @Test
    fun `different days do not conflict`() {
        val first = block("a", "09:00", "10:30", day = "MONDAY")
        val second = block("b", "09:00", "10:30", day = "TUESDAY")

        assertFalse(ScheduleConflictRules.overlaps(first, second))
    }

    @Test
    fun `cancelled blocks do not conflict`() {
        val first = block("a", "09:00", "10:30", status = BlockStatus.CANCELLED)
        val second = block("b", "09:30", "11:00")

        assertFalse(ScheduleConflictRules.overlaps(first, second))
    }

    @Test
    fun `same one time date conflicts`() {
        val first = block(
            "a",
            "09:00",
            "10:30",
            recurrence = ScheduleRecurrence.ONE_TIME,
            effectiveDate = "2026-10-12",
        )
        val second = block(
            "b",
            "09:30",
            "11:00",
            recurrence = ScheduleRecurrence.ONE_TIME,
            effectiveDate = "2026-10-12",
        )

        assertTrue(ScheduleConflictRules.overlaps(first, second))
    }

    @Test
    fun `different one time dates do not conflict`() {
        val first = block(
            "a",
            "09:00",
            "10:30",
            recurrence = ScheduleRecurrence.ONE_TIME,
            effectiveDate = "2026-10-12",
        )
        val second = block(
            "b",
            "09:30",
            "11:00",
            recurrence = ScheduleRecurrence.ONE_TIME,
            effectiveDate = "2026-10-19",
        )

        assertFalse(ScheduleConflictRules.overlaps(first, second))
    }

    private fun block(
        id: String,
        start: String,
        end: String,
        day: String = "MONDAY",
        status: BlockStatus = BlockStatus.SCHEDULED,
        recurrence: ScheduleRecurrence = ScheduleRecurrence.WEEKLY,
        effectiveDate: String? = null,
    ) = ScheduleBlock(
        id = id,
        dayOfWeek = day,
        startTime = start,
        endTime = end,
        subjectName = id,
        provenance = AcademicProvenance(
            source = AcademicDataSource.USER_MANUAL,
            verified = false,
            createdAtEpochSeconds = 1,
            updatedAtEpochSeconds = 1,
        ),
        status = status,
        recurrence = recurrence,
        effectiveDate = effectiveDate,
    )
}
