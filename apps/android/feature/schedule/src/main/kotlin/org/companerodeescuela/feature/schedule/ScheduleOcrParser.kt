package org.companerodeescuela.feature.schedule

import org.companerodeescuela.core.academic.PersonalScheduleDraft
import org.companerodeescuela.shared.contracts.ScheduleSource

object ScheduleOcrParser {
    private val timeRange = Regex(
        """\b([01]?\d|2[0-3])[:.]([0-5]\d)\s*(?:-|–|—|a)\s*([01]?\d|2[0-3])[:.]([0-5]\d)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val dayAliases = linkedMapOf(
        "MONDAY" to listOf("lunes", "monday"),
        "TUESDAY" to listOf("martes", "tuesday"),
        "WEDNESDAY" to listOf("miércoles", "miercoles", "wednesday"),
        "THURSDAY" to listOf("jueves", "thursday"),
        "FRIDAY" to listOf("viernes", "friday"),
        "SATURDAY" to listOf("sábado", "sabado", "saturday"),
        "SUNDAY" to listOf("domingo", "sunday"),
    )

    fun parse(rawText: String): List<PersonalScheduleDraft> {
        val lines = rawText
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .toList()

        var currentDay: String? = null
        val result = mutableListOf<PersonalScheduleDraft>()

        lines.forEachIndexed { index, line ->
            dayFrom(line)?.let { currentDay = it }
            val match = timeRange.find(line) ?: return@forEachIndexed
            val day = dayFrom(line) ?: currentDay ?: return@forEachIndexed

            val start = formatTime(match.groupValues[1], match.groupValues[2])
            val end = formatTime(match.groupValues[3], match.groupValues[4])
            var subject = cleanSubject(line.substring(match.range.last + 1), day)
            var contextStart = index + 1

            if (subject.isBlank()) {
                subject = lines.getOrNull(index + 1)
                    ?.takeIf { next ->
                        timeRange.find(next) == null && dayFrom(next) == null
                    }
                    ?.let { cleanSubject(it, day) }
                    .orEmpty()
                if (subject.isNotBlank()) contextStart = index + 2
            }
            if (subject.isBlank()) return@forEachIndexed

            val context = lines.drop(contextStart).take(3)
            val room = context.firstOrNull(::looksLikeRoom)
            val teacher = context.firstOrNull(::looksLikeTeacher)

            result += PersonalScheduleDraft(
                subjectName = subject,
                teacherName = teacher.orEmpty(),
                dayOfWeek = day,
                startsAt = start,
                endsAt = end,
                classroomName = room,
                source = ScheduleSource.OCR_IMPORT,
            )
        }

        return result.distinctBy {
            listOf(
                it.dayOfWeek,
                it.startsAt,
                it.endsAt,
                it.subjectName.trim().lowercase(),
            ).joinToString("|")
        }
    }

    private fun dayFrom(line: String): String? {
        val normalized = line.lowercase()
        return dayAliases.entries.firstOrNull { (_, aliases) ->
            aliases.any { alias -> Regex("""\b""" + Regex.escape(alias) + """\b""").containsMatchIn(normalized) }
        }?.key
    }

    private fun cleanSubject(value: String, day: String): String {
        var cleaned = value
            .trim(' ', '-', '–', '—', '|', ':')
            .replace(Regex("""\s+"""), " ")
        dayAliases[day].orEmpty().forEach { alias ->
            cleaned = cleaned.replace(alias, "", ignoreCase = true).trim()
        }
        return cleaned
    }

    private fun looksLikeRoom(value: String): Boolean =
        Regex("""\b(aula|sal[oó]n|lab(?:oratorio)?|edificio|taller)\b""", RegexOption.IGNORE_CASE)
            .containsMatchIn(value)

    private fun looksLikeTeacher(value: String): Boolean =
        Regex("""\b(mtr\.?|mtra\.?|mtro\.?|prof\.?|profa\.?|ing\.?|docente|dr\.?|dra\.?)\b""", RegexOption.IGNORE_CASE)
            .containsMatchIn(value)

    private fun formatTime(hour: String, minute: String): String =
        hour.toInt().toString().padStart(2, '0') + ":" + minute
}
