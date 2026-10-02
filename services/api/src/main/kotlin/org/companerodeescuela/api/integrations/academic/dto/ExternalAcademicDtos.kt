package org.companerodeescuela.api.integrations.academic.dto

import java.time.DayOfWeek
import java.time.LocalTime

/**
 * External DTOs returned by an [org.companerodeescuela.api.integrations.academic.AcademicProvider].
 *
 * These types model what a real institutional system would return, including
 * its quirks: string-typed identifiers, nullable codes, free-form subject
 * names, times without dates. They exist only inside the adapter boundary.
 *
 * The rule enforced by this file's existence: an external DTO must never be
 * returned from a repository, serialized to a client, or used as a domain
 * model. `AcademicMappers` converts them into
 * [org.companerodeescuela.shared.model] types immediately.
 */
data class ExternalStudent(
    val externalId: String,
    val fullName: String,
    val institutionalEmail: String? = null,
    val programCode: String? = null,
    val enrollmentYear: Int? = null,
)

data class ExternalTeacher(
    val externalId: String,
    val fullName: String,
    val institutionalEmail: String? = null,
    val departmentCode: String? = null,
)

data class ExternalSubject(
    val externalCode: String,
    val name: String,
    val credits: Int? = null,
)

data class ExternalCourse(
    val externalId: String,
    val subject: ExternalSubject,
    val teacher: ExternalTeacher,
    val term: String,
    val groupName: String,
)

data class ExternalEnrollment(
    val course: ExternalCourse,
    val enrolledAtIso: String,
)

data class ExternalScheduleSlot(
    val courseId: String,
    val groupName: String,
    /** Upstream weekday name, for example `LUNES` or `Monday`. */
    val weekdayName: String,
    val startTime: String,
    val endTime: String,
    val buildingCode: String? = null,
    val classroomCode: String? = null,
    val campusName: String? = null,
)

/** Aggregated academic load, mirroring how institutions usually expose it. */
data class ExternalAcademicLoad(
    val student: ExternalStudent,
    val enrollments: List<ExternalEnrollment>,
    val schedule: List<ExternalScheduleSlot>,
)

/**
 * Upstream weekday vocabulary, kept as data rather than logic.
 *
 * Institutional systems rarely agree on weekday names, so normalising them is
 * an adapter concern. Unknown names map to `null` and the adapter then rejects
 * the slot instead of guessing a day.
 */
object ExternalWeekdays {
    private val byName: Map<String, DayOfWeek> = buildMap {
        put("lunes", DayOfWeek.MONDAY)
        put("martes", DayOfWeek.TUESDAY)
        put("miercoles", DayOfWeek.WEDNESDAY)
        put("jueves", DayOfWeek.THURSDAY)
        put("viernes", DayOfWeek.FRIDAY)
        put("sabado", DayOfWeek.SATURDAY)
        put("domingo", DayOfWeek.SUNDAY)
        put("monday", DayOfWeek.MONDAY)
        put("tuesday", DayOfWeek.TUESDAY)
        put("wednesday", DayOfWeek.WEDNESDAY)
        put("thursday", DayOfWeek.THURSDAY)
        put("friday", DayOfWeek.FRIDAY)
        put("saturday", DayOfWeek.SATURDAY)
        put("sunday", DayOfWeek.SUNDAY)
    }

    fun fromName(raw: String): DayOfWeek? = byName[raw.trim().lowercase()]
}

/** Parses `HH:mm` or `HH:mm:ss` as used by most enrolment systems. */
fun parseUpstreamTime(raw: String): LocalTime? = runCatching { LocalTime.parse(raw.trim()) }.getOrNull()
