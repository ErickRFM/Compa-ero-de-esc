package org.companerodeescuela.api.academic

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import org.companerodeescuela.shared.model.AcademicId
import org.companerodeescuela.shared.model.ClassOccurrence
import org.companerodeescuela.shared.model.ClassOccurrenceStatus
import org.companerodeescuela.shared.model.Schedule

/**
 * Deterministically projects one normalized recurring schedule into dated meetings.
 *
 * Both the academic API and attendance use this single implementation so a
 * class occurrence id cannot drift between features.
 */
object AcademicOccurrenceProjection {

    fun weekStart(weekOf: LocalDate): LocalDate =
        weekOf.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))

    fun project(
        schedule: Schedule,
        weekOf: LocalDate,
    ): List<ClassOccurrence> {
        val weekStart = weekStart(weekOf)
        return schedule.slots
            .map { slot ->
                val date = weekStart.plusDays((slot.dayOfWeek.value - 1).toLong())
                val patternId = AcademicId(
                    stableId(
                        "pattern",
                        slot.group.course.id.value,
                        slot.group.name,
                        slot.dayOfWeek.name,
                        slot.startsAt.toString(),
                        slot.endsAt.toString(),
                    ),
                )
                ClassOccurrence(
                    id = AcademicId(stableId("occurrence", patternId.value, date.toString())),
                    patternId = patternId,
                    group = slot.group,
                    date = date,
                    startsAt = slot.startsAt,
                    endsAt = slot.endsAt,
                    classroom = slot.classroom,
                    teacher = slot.group.course.teacher,
                    status = ClassOccurrenceStatus.SCHEDULED,
                )
            }
            .sortedWith(compareBy({ it.date }, { it.startsAt }))
    }

    private fun stableId(vararg parts: String): String {
        val input = parts.joinToString("|")
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(StandardCharsets.UTF_8))
        return bytes.take(16).joinToString("") { byte -> "%02x".format(byte) }
    }
}
