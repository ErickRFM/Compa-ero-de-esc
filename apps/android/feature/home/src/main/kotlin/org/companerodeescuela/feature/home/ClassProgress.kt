package org.companerodeescuela.feature.home

import java.time.Duration
import java.time.LocalTime
import org.companerodeescuela.shared.contracts.ScheduleEntry

internal fun classProgress(
    entry: ScheduleEntry,
    now: LocalTime = LocalTime.now(),
): Float {
    val start = runCatching { LocalTime.parse(entry.startsAt) }.getOrNull() ?: return 0f
    val end = runCatching { LocalTime.parse(entry.endsAt) }.getOrNull() ?: return 0f
    if (!end.isAfter(start)) return 0f

    val totalSeconds = Duration.between(start, end).seconds
    if (totalSeconds <= 0L) return 0f

    val elapsedSeconds = Duration.between(start, now).seconds
    return (elapsedSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
}
