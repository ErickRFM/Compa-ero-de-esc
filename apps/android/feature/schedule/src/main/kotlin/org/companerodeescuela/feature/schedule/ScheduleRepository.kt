package org.companerodeescuela.feature.schedule

import org.companerodeescuela.core.database.AcademicSnapshotCache
import org.companerodeescuela.shared.contracts.ScheduleEntry

class ScheduleRepository(
    private val cache: AcademicSnapshotCache,
) {
    suspend fun readWeeklySchedule(): List<ScheduleEntry> =
        cache.read()
            ?.value
            ?.schedule
            ?.entries
            .orEmpty()
            .sortedWith(compareBy({ weekdayOrder(it.dayOfWeek) }, { it.startsAt }))

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
