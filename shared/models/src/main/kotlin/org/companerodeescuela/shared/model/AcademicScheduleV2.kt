package org.companerodeescuela.shared.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Normalized academic term independent of any provider-specific code.
 */
data class AcademicTerm(
    val id: AcademicId,
    val code: String,
    val name: String = code,
    val startsOn: LocalDate? = null,
    val endsOn: LocalDate? = null,
) {
    init {
        require(code.isNotBlank()) { "code must not be blank" }
        require(name.isNotBlank()) { "name must not be blank" }
        require(startsOn == null || endsOn == null || !endsOn.isBefore(startsOn)) {
            "endsOn must not be before startsOn"
        }
    }
}

enum class ClassOccurrenceStatus {
    SCHEDULED,
    CANCELLED,
    RESCHEDULED,
    ONLINE,
}

enum class ScheduleChangeKind {
    TIME_CHANGED,
    ROOM_CHANGED,
    TEACHER_CHANGED,
    CANCELLED,
    MOVED_ONLINE,
}

/**
 * Stable recurring meeting definition. It is distinct from a dated class occurrence.
 *
 * Attendance must bind to [ClassOccurrence], never directly to a recurring pattern.
 */
data class RecurringSchedulePattern(
    val id: AcademicId,
    val group: Group,
    val dayOfWeek: DayOfWeek,
    val startsAt: LocalTime,
    val endsAt: LocalTime,
    val classroom: Classroom?,
) {
    init {
        require(startsAt < endsAt) { "startsAt must be earlier than endsAt" }
    }
}

data class ScheduleChange(
    val kind: ScheduleChangeKind,
    val note: String? = null,
    val originalDate: LocalDate? = null,
    val originalStartsAt: LocalTime? = null,
    val originalEndsAt: LocalTime? = null,
    val originalClassroomName: String? = null,
    val originalTeacherName: String? = null,
)

/**
 * One concrete class meeting on one calendar date.
 */
data class ClassOccurrence(
    val id: AcademicId,
    val patternId: AcademicId?,
    val group: Group,
    val date: LocalDate,
    val startsAt: LocalTime,
    val endsAt: LocalTime,
    val classroom: Classroom?,
    val teacher: Teacher,
    val status: ClassOccurrenceStatus = ClassOccurrenceStatus.SCHEDULED,
    val changes: List<ScheduleChange> = emptyList(),
) {
    init {
        require(startsAt < endsAt) { "startsAt must be earlier than endsAt" }
    }
}
