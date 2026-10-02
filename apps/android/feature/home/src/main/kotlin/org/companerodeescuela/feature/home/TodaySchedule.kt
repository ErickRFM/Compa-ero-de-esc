package org.companerodeescuela.feature.home

import java.time.LocalDateTime
import java.time.LocalTime
import org.companerodeescuela.shared.contracts.AcademicLoadResponse
import org.companerodeescuela.shared.contracts.ScheduleEntry

data class TodayOverview(
    val studentName: String,
    val current: ScheduleEntry?,
    val next: ScheduleEntry?,
    val classes: List<ScheduleEntry>,
)

object TodaySchedule {
    fun calculate(
        load: AcademicLoadResponse,
        now: LocalDateTime = LocalDateTime.now(),
    ): TodayOverview {
        val today = load.schedule.entries
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

        return TodayOverview(
            studentName = load.student.displayName,
            current = current?.entry,
            next = next?.entry,
            classes = today.map { it.entry },
        )
    }

    private data class ParsedEntry(
        val entry: ScheduleEntry,
        val start: LocalTime,
        val end: LocalTime,
    )
}
