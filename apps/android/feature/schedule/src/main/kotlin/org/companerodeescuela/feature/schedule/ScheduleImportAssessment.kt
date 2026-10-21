package org.companerodeescuela.feature.schedule

import org.companerodeescuela.core.academic.PersonalScheduleDraft
import org.companerodeescuela.shared.contracts.ScheduleEntry
import org.companerodeescuela.shared.contracts.ScheduleSource

/**
 * A successful parser can silently drop timetable columns. Prefer native text,
 * cross-check suspicious results against existing OCR, and always require
 * human review. Never fabricate lessons or merge conflicting data sets.
 */
internal object ScheduleImportAssessment {
    private val academicDays =
        linkedMapOf(
            "MONDAY" to "Lun",
            "TUESDAY" to "Mar",
            "WEDNESDAY" to "Mié",
            "THURSDAY" to "Jue",
            "FRIDAY" to "Vie",
            "SATURDAY" to "Sáb",
        )

    fun counts(candidates: List<PersonalScheduleDraft>): List<Pair<String, Int>> =
        academicDays.map { (day, label) ->
            label to candidates.count { it.dayOfWeek == day }
        }

    fun missingDays(candidates: List<PersonalScheduleDraft>): List<String> =
        academicDays.filterKeys { day -> candidates.none { it.dayOfWeek == day } }.values.toList()

    /**
     * Conservative cross-check trigger, not a global requirement. The 9A
     * regression has 25 merged classes across all six scheduled weekdays.
     */
    fun shouldCrossCheckNative(native: List<PersonalScheduleDraft>) = native.size < 25 || missingDays(native).isNotEmpty()

    /**
     * Prefer OCR only when it adds days without removing known ones, or adds
     * three blocks without losing day coverage or most teacher information.
     */
    fun select(
        native: List<PersonalScheduleDraft>,
        ocr: List<PersonalScheduleDraft>,
    ): List<PersonalScheduleDraft> {
        if (native.isEmpty()) return ocr
        if (ocr.isEmpty()) return native
        val nativeDays = native.map { it.dayOfWeek }.toSet()
        val ocrDays = ocr.map { it.dayOfWeek }.toSet()
        val nativeTeachers = native.count { it.teacherName.isNotBlank() }
        val ocrTeachers = ocr.count { it.teacherName.isNotBlank() }
        val enoughTeachers = ocrTeachers * 10 >= minOf(nativeTeachers, ocr.size) * 7
        val addsDays = ocrDays.containsAll(nativeDays) && ocrDays.size > nativeDays.size
        val addsBlocks = ocrDays.containsAll(nativeDays) && ocr.size >= native.size + 3
        return if (enoughTeachers && ocr.size >= native.size && (addsDays || addsBlocks)) {
            ocr
        } else {
            native
        }
    }

    fun warnings(
        candidates: List<PersonalScheduleDraft>,
        previous: List<ScheduleEntry>,
    ): List<String> =
        buildList {
            val missing = missingDays(candidates)
            if (missing.isNotEmpty()) {
                val message =
                    "Sin clases detectadas: " + missing.joinToString() +
                        ". Verifica si son días libres o si faltan columnas del PDF."
                add(message)
            }
            val saved = previous.filter { it.source == ScheduleSource.OCR_IMPORT }
            val lostDays =
                saved.map { it.dayOfWeek }.toSet() -
                    candidates.map { it.dayOfWeek }.toSet()
            if (lostDays.isNotEmpty() || (saved.isNotEmpty() && candidates.size < saved.size)) {
                val message =
                    "Se reemplazarán las clases importadas: " + saved.size +
                        " guardadas → " + candidates.size +
                        " detectadas. Comprueba horarios y materias antes de confirmar."
                add(message)
            }
        }
}
