package org.companerodeescuela.feature.schedule

import java.time.LocalTime
import org.companerodeescuela.shared.contracts.ScheduleEntry

internal fun weeklyAgendaDays(entries: List<org.companerodeescuela.shared.contracts.ScheduleEntry>): List<String> =
    // Saturday is part of the normal school week even when an imperfect
    // import has temporarily missed its entries. Sunday remains optional.
    academicDaysV8.take(6) + listOf("SUNDAY").filter { day -> entries.any { it.dayOfWeek == day } }

internal fun weeklyDragTargetDay(days: List<String>, widthsPx: List<Float>, dayIndex: Int, lane: Int, laneCount: Int, offsetX: Float): String {
    require(days.size == widthsPx.size && widthsPx.all { it > 0 } && dayIndex in days.indices && laneCount > 0)
    val x = widthsPx.take(dayIndex).sum() + widthsPx[dayIndex] * (lane + 0.5f) / laneCount + offsetX
    var boundary = 0f
    return days[widthsPx.indexOfFirst { width -> boundary += width; x < boundary }.takeIf { it >= 0 } ?: days.lastIndex]
}

data class AgendaGridPlacement(val entry: ScheduleEntry, val startMinute: Int, val durationMinutes: Int, val lane: Int, val laneCount: Int)

/** Called per day. Separate connected overlap groups so free blocks regain full width. */
fun agendaGridLayout(entries: List<ScheduleEntry>): List<AgendaGridPlacement> {
    val parsed = entries.mapNotNull { entry ->
        val start = runCatching { LocalTime.parse(entry.startsAt).toSecondOfDay() / 60 }.getOrNull()
        val end = runCatching { LocalTime.parse(entry.endsAt).toSecondOfDay() / 60 }.getOrNull()
        if (start == null || end == null || end <= start) null
        else AgendaGridPlacement(entry, start, end - start, 0, 1)
    }.sortedWith(compareBy({ it.startMinute }, { it.durationMinutes }))
    val groups = mutableListOf<MutableList<AgendaGridPlacement>>()
    var groupEnd = -1
    parsed.forEach { item ->
        if (groups.isEmpty() || item.startMinute >= groupEnd) {
            groups.add(mutableListOf())
            groupEnd = item.startMinute + item.durationMinutes
        }
        groups.last().add(item)
        groupEnd = maxOf(groupEnd, item.startMinute + item.durationMinutes)
    }
    return groups.flatMap { group ->
        val laneEnds = mutableListOf<Int>()
        val assigned = group.map { item ->
            val lane = laneEnds.indexOfFirst { it <= item.startMinute }.takeIf { it >= 0 }
                ?: laneEnds.size.also { laneEnds.add(-1) }
            laneEnds[lane] = item.startMinute + item.durationMinutes
            item.copy(lane = lane)
        }
        assigned.map { it.copy(laneCount = laneEnds.size) }
    }
}
