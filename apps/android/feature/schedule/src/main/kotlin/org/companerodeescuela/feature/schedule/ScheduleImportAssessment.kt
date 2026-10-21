package org.companerodeescuela.feature.schedule

import org.companerodeescuela.core.academic.PersonalScheduleDraft
import org.companerodeescuela.shared.contracts.ScheduleEntry
import org.companerodeescuela.shared.contracts.ScheduleSource

/**
 * A parser can return valid subjects while silently dropping timetable days.
 * Native PDF extraction is primary; compare suspiciously sparse results with
 * existing layout-aware OCR, without inventing classes or silently merging
 * conflicting sources. Every result remains subject to human review.
 */
internal object ScheduleImportAssessment {
    private val academicDays = linkedMapOf(
        "MONDAY" to "Lun", "TUESDAY" to "Mar", "WEDNESDAY" to "Mié",
        "THURSDAY" to "Jue", "FRIDAY" to "Vie", "SATURDAY" to "Sáb",
    )

    fun counts(candidates: List<PersonalScheduleDraft>): List<Pair<String, Int>> =
        academicDays.map { (day, label) ->
            label to candidates.count { it.dayOfWeek == day }
        }

    fun missingDays(candidates: List<PersonalScheduleDraft>): List<String> =
        academicDays.filterKeys { day -> candidates.none { it.dayOfWeek == day } }.values.toList()

    /**
     * Not proof of completeness: the golden 9A example has 25 consolidated
     * classes Mon-Sat. Smaller/sparser schedules get a second OCR pass.
     */
    fun shouldCrossCheckNative(native: List<PersonalScheduleDraft>): Boolean =
        native.size < 25 || missingDays(native).isNotEmpty()

    /** Prefer OCR only if it recovers days or substantially more blocks
     * without losing represented days and most teacher metadata. */
    fun select(native: List<PersonalScheduleDraft>, ocr: List<PersonalScheduleDraft>): List<PersonalScheduleDraft> {
        if (native.isEmpty()) return ocr
        if (ocr.isEmpty()) return native
        val nativeDays = native.map { it.dayOfWeek }.toSet()
        val ocrDays = ocr.map { it.dayOfWeek }.toSet()
        val nativeTeachers = native.count { it.teacherName.isNotBlank() }
        val ocrTeachers = ocr.count { it.teacherName.isNotBlank() }
        val enoughTeachers = ocrTeachers * 10 >= minOf(nativeTeachers, ocr.size) * 7
        val addsDays = ocrDays.containsAll(nativeDays) && ocrDays.size > nativeDays.size
        val addsBlocks = ocrDays.containsAll(nativeDays) && ocr.size >= native.size + 3
        return if (enoughTeachers && ocr.size >= native.size && (addsDays || addsBlocks)) ocr else native
    }

    fun warnings(candidates: List<PersonalScheduleDraft>, previous: List<ScheduleEntry>): List<String> =
        buildList {
            val missing = missingDays(candidates)
            if (missing.isNotEmpty()) {
                add("Sin clases detectadas: " + missing.joinToString() +
                    ". Verifica si son días libres o si faltan columnas del PDF.")
            }
            val saved = previous.filter { it.source == ScheduleSource.OCR_IMPORT }
            val lostDays = saved.map { it.dayOfWeek }.toSet() -
                candidates.map { it.dayOfWeek }.toSet()
            if (lostDays.isNotEmpty() || (saved.isNotEmpty() && candidates.size < saved.size)) {
                add("Se reemplazarán las clases importadas: " + saved.size +
                    " guardadas → " + candidates.size +
                    " detectadas. Comprueba horarios y materias antes de confirmar.")
            }
        }
}
