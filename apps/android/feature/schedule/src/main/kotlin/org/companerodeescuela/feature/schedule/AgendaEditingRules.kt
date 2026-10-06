package org.companerodeescuela.feature.schedule

import java.time.Duration
import java.time.LocalTime
import org.companerodeescuela.shared.contracts.ScheduleEntry

data class AgendaMoveProposal(
    val entry: ScheduleEntry,
    val targetDay: String,
    val targetStart: String,
    val targetEnd: String,
    val conflicts: List<ScheduleEntry>,
)

object AgendaEditingRules {
    const val SNAP_MINUTES = 15

    fun proposeMove(
        entry: ScheduleEntry,
        targetDay: String,
        targetStart: String,
        existing: List<ScheduleEntry>,
    ): AgendaMoveProposal? {
        val start = parse(entry.startsAt) ?: return null
        val end = parse(entry.endsAt) ?: return null
        if (!start.isBefore(end)) return null
        val requested = parse(targetStart) ?: return null
        val snapped = snap(requested, SNAP_MINUTES)
        val duration = Duration.between(start, end)
        val targetEnd = snapped.plus(duration)
        if (targetEnd <= snapped) return null

        val startText = format(snapped)
        val endText = format(targetEnd)
        val conflicts = existing
            .filter { it.courseId != entry.courseId }
            .filter { it.dayOfWeek == targetDay }
            .filter { overlaps(startText, endText, it.startsAt, it.endsAt) }
            .sortedBy { it.startsAt }

        return AgendaMoveProposal(
            entry = entry,
            targetDay = targetDay,
            targetStart = startText,
            targetEnd = endText,
            conflicts = conflicts,
        )
    }

    fun shiftFromDrag(
        entry: ScheduleEntry,
        verticalPixels: Float,
        pixelsPerQuarterHour: Float,
    ): String? {
        if (pixelsPerQuarterHour <= 0f) return null
        val start = parse(entry.startsAt) ?: return null
        val quarterSteps = kotlin.math.round(verticalPixels / pixelsPerQuarterHour).toLong()
        return format(start.plusMinutes(quarterSteps * SNAP_MINUTES))
    }

    fun adjacentDay(currentDay: String, horizontalPixels: Float, thresholdPixels: Float): String {
        if (kotlin.math.abs(horizontalPixels) < thresholdPixels) return currentDay
        val index = academicDaysV8.indexOf(currentDay).takeIf { it >= 0 } ?: return currentDay
        val delta = if (horizontalPixels > 0) 1 else -1
        return academicDaysV8[(index + delta).coerceIn(0, academicDaysV8.lastIndex)]
    }

    fun overlaps(
        startA: String,
        endA: String,
        startB: String,
        endB: String,
    ): Boolean {
        val aStart = parse(startA) ?: return false
        val aEnd = parse(endA) ?: return false
        val bStart = parse(startB) ?: return false
        val bEnd = parse(endB) ?: return false
        return aStart < bEnd && aEnd > bStart
    }

    private fun snap(value: LocalTime, minutes: Int): LocalTime {
        val totalMinutes = value.hour * 60 + value.minute
        val snapped = ((totalMinutes + minutes / 2) / minutes) * minutes
        val bounded = snapped.coerceIn(0, 23 * 60 + 45)
        return LocalTime.of(bounded / 60, bounded % 60)
    }

    private fun parse(value: String): LocalTime? =
        runCatching { LocalTime.parse(value) }.getOrNull()

    private fun format(value: LocalTime): String =
        "%02d:%02d".format(value.hour, value.minute)
}

internal val academicDaysV8 = listOf(
    "MONDAY",
    "TUESDAY",
    "WEDNESDAY",
    "THURSDAY",
    "FRIDAY",
    "SATURDAY",
)
