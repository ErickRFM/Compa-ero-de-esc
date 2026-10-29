package org.companerodeescuela.feature.home

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale
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

/** Personal OCR imports share the placeholder code PERSONAL, not the same subject. */
internal fun scheduleSubjectKey(entry: ScheduleEntry): String {
    val code = entry.subjectCode.trim()
    return if (code.isEmpty() || code.equals("PERSONAL", ignoreCase = true)) {
        entry.subjectName.trim().lowercase(Locale.ROOT)
    } else {
        code.uppercase(Locale.ROOT)
    }
}

/** The compact stat remains informative after today's final class. */
internal fun nextClassStatLabel(day: TodayOverview): String {
    day.next?.let { return it.startsAt }
    val upcoming = day.nextScheduled ?: return "—"
    return when (val away = day.nextScheduledDaysAway) {
        1 -> "Mañana"
        null, 0 -> upcoming.startsAt
        else -> "En $away días"
    }
}

data class HomeScheduleChoice(
    val overview: TodayOverview,
    val usesPersonalSchedule: Boolean,
)

/** Official assignments take precedence. An empty official timetable must not hide imported lessons. */
object TodaySchedule {
    fun select(
        academic: AcademicLoadResponse,
        personal: List<ScheduleEntry>,
        now: LocalDateTime = LocalDateTime.now(),
    ): HomeScheduleChoice {
        val usePersonal = academic.schedule.entries.isEmpty() && personal.isNotEmpty()
        return HomeScheduleChoice(
            overview =
                if (usePersonal) {
                    calculateEntries(personal, academic.student.displayName, now)
                } else {
                    calculate(academic, now)
                },
            usesPersonalSchedule = usePersonal,
        )
    }

    fun calculate(
        load: AcademicLoadResponse,
        now: LocalDateTime = LocalDateTime.now(),
    ): TodayOverview = calculateEntries(load.schedule.entries, load.student.displayName, now)

    /** Shares the exact same timetable logic with locally imported classes. */
    fun calculateEntries(
        allEntries: List<ScheduleEntry>,
        studentName: String = "",
        now: LocalDateTime = LocalDateTime.now(),
    ): TodayOverview {
        val today =
            allEntries
                .filter { it.dayOfWeek == now.dayOfWeek.name }
                .mapNotNull { entry ->
                    val start = runCatching { LocalTime.parse(entry.startsAt) }.getOrNull()
                    val end = runCatching { LocalTime.parse(entry.endsAt) }.getOrNull()
                    if (start == null || end == null || start >= end) {
                        null
                    } else {
                        ParsedEntry(entry, start, end)
                    }
                }.sortedBy { it.start }

        val current =
            today.firstOrNull {
                !now.toLocalTime().isBefore(it.start) && now.toLocalTime().isBefore(it.end)
            }
        val next = today.firstOrNull { it.start.isAfter(now.toLocalTime()) }
        val weeklyNext = findNextScheduled(allEntries, now)

        return TodayOverview(
            studentName = studentName,
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
    ): WeeklyCandidate? {
        val candidates =
            entries.mapNotNull { entry ->
                val day = runCatching { DayOfWeek.valueOf(entry.dayOfWeek) }.getOrNull() ?: return@mapNotNull null
                val start = runCatching { LocalTime.parse(entry.startsAt) }.getOrNull() ?: return@mapNotNull null
                var daysAway = (day.value - now.dayOfWeek.value + 7) % 7
                if (daysAway == 0 && !start.isAfter(now.toLocalTime())) {
                    daysAway = 7
                }
                WeeklyCandidate(entry = entry, start = start, daysAway = daysAway)
            }
        return candidates.minWithOrNull(
            compareBy<WeeklyCandidate> { it.daysAway }.thenBy { it.start },
        )
    }

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
