package org.companerodeescuela.shared.contracts

import java.time.LocalTime

object ScheduleConflictRules {
    fun overlaps(a: ScheduleBlock, b: ScheduleBlock): Boolean {
        if (a.id == b.id) return false
        if (a.status == BlockStatus.CANCELLED || b.status == BlockStatus.CANCELLED) return false
        if (a.dayOfWeek != b.dayOfWeek) return false
        if (!sameEffectiveDateScope(a, b)) return false

        val aStart = parseTime(a.startTime) ?: return false
        val aEnd = parseTime(a.endTime) ?: return false
        val bStart = parseTime(b.startTime) ?: return false
        val bEnd = parseTime(b.endTime) ?: return false

        return aStart < bEnd && aEnd > bStart
    }

    fun conflictsFor(
        candidate: ScheduleBlock,
        existing: List<ScheduleBlock>,
    ): List<ScheduleBlock> =
        existing
            .filter { overlaps(candidate, it) }
            .sortedWith(compareBy({ it.dayOfWeek }, { it.startTime }, { it.subjectName }))

    private fun sameEffectiveDateScope(a: ScheduleBlock, b: ScheduleBlock): Boolean {
        val aDate = a.effectiveDate
        val bDate = b.effectiveDate
        return when {
            a.recurrence == ScheduleRecurrence.ONE_TIME &&
                b.recurrence == ScheduleRecurrence.ONE_TIME ->
                aDate != null && aDate == bDate
            a.recurrence == ScheduleRecurrence.ONE_TIME ||
                b.recurrence == ScheduleRecurrence.ONE_TIME -> true
            else -> true
        }
    }

    private fun parseTime(raw: String): LocalTime? =
        runCatching { LocalTime.parse(raw) }.getOrNull()
}
