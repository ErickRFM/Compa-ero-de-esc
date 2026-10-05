package org.companerodeescuela.feature.home

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import org.companerodeescuela.shared.contracts.AcademicLoadResponse
import org.companerodeescuela.shared.contracts.ScheduleEntry

data class TodayOverview(
    val studentName: String,
    val hasSchedule: Boolean,
    val current: ScheduleEntry?,
    val next: ScheduleEntry?,
    val classes: List<ScheduleEntry>,
    val nextScheduled: ScheduleEntry? = null,
    val nextScheduledDaysAway: Int? = null,
)

object TodaySchedule {
    fun calculate(
        load: AcademicLoadResponse,
        now: LocalDateTime = LocalDateTime.now(),
    ): TodayOverview {
        val allEntries = load.schedule.entries
        val today = allEntries
            .filter { it.dayOfWeek == now.dayOfWeek.name }
            .mapNotNull { entry ->
                val start = runCatching { LocalTime.parse(entry.startsAt) }.getOrNull()
                val end = runCatching { LocalTime.parse(entry.endsAt) }.getOrNull()
                if (start == null || end == null || start >= end) null
                else ParsedEntry(entry, start, end)
            }
            .sortedBy { it.start }

        val current = today.firstOrNull {
            !now.toLocalTime().isBefore(it.start) && now.toLocalTime().isBefore(it.end)
        }
        val next = today.firstOrNull { it.start.isAfter(now.toLocalTime()) }
        val weeklyNext = findNextScheduled(allEntries, now)

        return TodayOverview(
            studentName = load.student.displayName,
            hasSchedule = allEntries.isNotEmpty(),
            current = current?.entry,
            next = next?.entry,
            classes = today.map { it.entry },
            nextScheduled = weeklyNext?.entry,
            nextScheduledDaysAway = weeklyNext?.daysAway,
        )
    }

    private fun findNextScheduled(
        entries: List<ScheduleEntry>,
        now: LocalDateTime,
    ): WeeklyCandidate? = entries.mapNotNull { entry ->
        val day = runCatching { DayOfWeek.valueOf(entry.dayOfWeek) }.getOrNull()
            ?: return@mapNotNull null
        val start = runCatching { LocalTime.parse(entry.startsAt) }.getOrNull()
            ?: return@mapNotNull null
        var daysAway = (day.value - now.dayOfWeek.value + 7) % 7
        if (daysAway == 0 && !start.isAfter(now.toLocalTime())) {
            daysAway = 7
        }
        WeeklyCandidate(entry = entry, start = start, daysAway = daysAway)
    }.minWithOrNull(
        compareBy<WeeklyCandidate> { it.daysAway }
            .thenBy { it.start },
    )

    private data class ParsedEntry(
        val entry: ScheduleEntry,
        val start: LocalTime,
        val end: LocalTime,
    )

    private data class WeeklyCandidate(
        val entry: ScheduleEntry,
        val start: LocalTime,
        val daysAway: Int,
    )
}
