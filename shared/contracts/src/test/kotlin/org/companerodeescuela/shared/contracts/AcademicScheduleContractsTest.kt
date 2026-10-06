package org.companerodeescuela.shared.contracts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AcademicScheduleContractsTest {
    @Test
    fun classifiesMorningAfternoonAndCrossShiftBlocks() {
        assertEquals(ScheduleShift.MORNING, ScheduleShiftRules.forBlock("07:00", "09:00"))
        assertEquals(ScheduleShift.MORNING, ScheduleShiftRules.forBlock("13:00", "14:00"))
        assertEquals(ScheduleShift.AFTERNOON, ScheduleShiftRules.forBlock("14:00", "20:00"))
        assertEquals(ScheduleShift.MIXED, ScheduleShiftRules.forBlock("13:30", "14:30"))
    }

    @Test
    fun validatesAcademicDayBounds() {
        assertTrue(ScheduleShiftRules.isInsideAcademicDay("07:00", "20:00"))
        assertFalse(ScheduleShiftRules.isInsideAcademicDay("06:59", "08:00"))
        assertFalse(ScheduleShiftRules.isInsideAcademicDay("19:00", "20:01"))
        assertFalse(ScheduleShiftRules.isInsideAcademicDay("15:00", "14:00"))
    }
}
