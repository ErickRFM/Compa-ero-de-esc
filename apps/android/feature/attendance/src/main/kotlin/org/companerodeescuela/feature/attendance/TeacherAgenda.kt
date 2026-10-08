package org.companerodeescuela.feature.attendance

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.companerodeescuela.shared.contracts.ClassOccurrenceContract
import org.companerodeescuela.shared.contracts.ClassOccurrenceStatusContract

/** End time is exclusive; cancelled and malformed occurrences cannot become the next class. */
internal fun upcomingTeacherClasses(occurrences: List<ClassOccurrenceContract>, now: LocalDateTime): List<ClassOccurrenceContract> =
    occurrences.filter { occurrence ->
        occurrence.status != ClassOccurrenceStatusContract.CANCELLED && runCatching {
            val date = LocalDate.parse(occurrence.date)
            val start = LocalTime.parse(occurrence.startsAt)
            val end = LocalTime.parse(occurrence.endsAt)
            val endsAt = LocalDateTime.of(date, end).let { if (end < start) it.plusDays(1) else it }
            endsAt > now
        }.getOrDefault(false)
    }.sortedWith(compareBy({ it.date }, { it.startsAt }))
