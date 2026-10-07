package org.companerodeescuela.feature.schedule

import java.text.Normalizer
import org.companerodeescuela.core.academic.PersonalScheduleDraft
import org.companerodeescuela.shared.contracts.ScheduleSource

object ScheduleOcrParser {
    private val timeRange = Regex(
        """\b([01]?\d|2[0-3])[:.]([0-5]\d)\s*(?:-|–|—|a)\s*([01]?\d|2[0-3])[:.]([0-5]\d)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val teacherTitle = Regex(
        """(?i)(ma\.|mtr\.?|mtra\.?|mtro\.?|prof\.?|profa\.?|ing\.?|docente|dr\.?|dra\.?)""",
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

            val embedded = splitEmbeddedTeacher(subject)
            subject = embedded.first

            val context = lines
                .drop(contextStart)
                .takeWhile { next -> timeRange.find(next) == null && dayFrom(next) == null }
                .take(4)

            val room = context.firstOrNull(::looksLikeRoom)
            val teacher = embedded.second
                ?: context.firstOrNull(::looksLikeTeacher)
                ?: context.firstOrNull(::looksLikePersonName)

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

        val unique = result.distinctBy {
            listOf(
                it.dayOfWeek,
                it.startsAt,
                it.endsAt,
                it.subjectName.trim().lowercase(),
                it.teacherName.trim().lowercase(),
            ).joinToString("|")
        }
        return mergeContiguous(canonicalizeRepeatedLabels(unique))
    }

    private fun canonicalizeRepeatedLabels(
        entries: List<PersonalScheduleDraft>,
    ): List<PersonalScheduleDraft> {
        val subjects = entries.map { it.subjectName }.filter(String::isNotBlank)
        val teachers = entries.map { it.teacherName }.filter(String::isNotBlank)
        return entries.map { entry ->
            entry.copy(
                subjectName = representative(entry.subjectName, subjects),
                teacherName = representative(entry.teacherName, teachers),
            )
        }
    }

    private fun representative(value: String, candidates: List<String>): String {
        if (value.isBlank()) return value
        val folded = value.foldForComparison()
        val threshold = if (folded.length >= 10) 2 else 1
        return candidates
            .groupingBy { it }
            .eachCount()
            .filterKeys { candidate ->
                editDistance(folded, candidate.foldForComparison()) <= threshold
            }
            .maxWithOrNull(
                compareBy<Map.Entry<String, Int>> { it.value }
                    .thenBy { candidate ->
                        candidate.key.count { ch -> ch in "áéíóúÁÉÍÓÚñÑ" }
                    }
                    .thenBy { candidate -> -candidate.key.length },
            )
            ?.key
            ?: value
    }

    private fun editDistance(left: String, right: String): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length
        var previous = IntArray(right.length + 1) { it }
        left.forEachIndexed { index, lc ->
            val current = IntArray(right.length + 1)
            current[0] = index + 1
            right.forEachIndexed { j, rc ->
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + if (lc == rc) 0 else 1,
                )
            }
            previous = current
        }
        return previous[right.length]
    }

    private fun String.foldForComparison(): String =
        Normalizer.normalize(this, Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .lowercase()
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .trim()


    private fun mergeContiguous(entries: List<PersonalScheduleDraft>): List<PersonalScheduleDraft> {
        val sorted = entries.sortedWith(
            compareBy<PersonalScheduleDraft>(
                { dayOrder(it.dayOfWeek) },
                { it.startsAt },
                { it.subjectName.lowercase() },
            ),
        )
        val merged = mutableListOf<PersonalScheduleDraft>()
        sorted.forEach { next ->
            val previous = merged.lastOrNull()
            if (previous != null && canMerge(previous, next)) {
                merged[merged.lastIndex] = previous.copy(endsAt = next.endsAt)
            } else {
                merged += next
            }
        }
        return merged
    }

    private fun canMerge(a: PersonalScheduleDraft, b: PersonalScheduleDraft): Boolean =
        a.dayOfWeek == b.dayOfWeek &&
            a.endsAt == b.startsAt &&
            a.subjectName.normalized() == b.subjectName.normalized() &&
            a.teacherName.normalized() == b.teacherName.normalized() &&
            a.classroomName.orEmpty().normalized() == b.classroomName.orEmpty().normalized() &&
            a.groupName.normalized() == b.groupName.normalized()

    private fun splitEmbeddedTeacher(value: String): Pair<String, String?> {
        val match = teacherTitle.find(value) ?: return value.trim() to null
        if (match.range.first == 0) return value.trim() to null
        val subject = value.substring(0, match.range.first).trim()
        val teacher = value.substring(match.range.first).trim()
        return if (subject.isBlank() || teacher.isBlank()) value.trim() to null else subject to teacher
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
        teacherTitle.containsMatchIn(value)

    private fun looksLikePersonName(value: String): Boolean {
        if (looksLikeRoom(value) || timeRange.containsMatchIn(value) || dayFrom(value) != null) return false
        val words = value
            .replace(Regex("""[.,]"""), "")
            .split(Regex("""\s+"""))
            .filter(String::isNotBlank)
        if (words.size !in 2..5) return false
        return words.all { word ->
            val first = word.firstOrNull() ?: return@all false
            first.isUpperCase() || word.equals("de", true) || word.equals("del", true)
        }
    }

    private fun String.normalized(): String = trim().lowercase().replace(Regex("""\s+"""), " ")

    private fun formatTime(hour: String, minute: String): String =
        hour.toInt().toString().padStart(2, '0') + ":" + minute

    private fun dayOrder(day: String): Int = when (day) {
        "MONDAY" -> 1
        "TUESDAY" -> 2
        "WEDNESDAY" -> 3
        "THURSDAY" -> 4
        "FRIDAY" -> 5
        "SATURDAY" -> 6
        "SUNDAY" -> 7
        else -> 8
    }
}
