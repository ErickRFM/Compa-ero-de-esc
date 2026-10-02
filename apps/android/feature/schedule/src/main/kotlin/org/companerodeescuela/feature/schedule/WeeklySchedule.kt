package org.companerodeescuela.feature.schedule

import org.companerodeescuela.shared.contracts.ScheduleEntry

object WeeklySchedule {
    fun order(entries: List<ScheduleEntry>): List<ScheduleEntry> =
        entries.sortedWith(compareBy({ weekdayOrder(it.dayOfWeek) }, { it.startsAt }))

    private fun weekdayOrder(day: String): Int = when (day) {
        "MONDAY" -> 1
        "TUESDAY" -> 2
        "WEDNESDAY" -> 3
        "THURSDAY" -> 4
        "FRIDAY" -> 5
        "SATURDAY" -> 6
        "SUNDAY" -> 7
        else -> 8
    }
}
