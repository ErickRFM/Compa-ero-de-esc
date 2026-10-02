package org.companerodeescuela.shared.model

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.Instant

/**
 * Institutional identifier of a person inside the platform.
 *
 * Deliberately one type for every role. Splitting it into `StudentId` and
 * `TeacherId` would only be worth the cost if the two arrived from different
 * sources or were looked up through different paths, and neither is true yet:
 * every identifier comes from the same identity feed.
 *
 * What actually stops a student's id reaching a teacher's view is the
 * [Student] / [Teacher] split below, not this type. An earlier version of this
 * file claimed the opposite in its KDoc, which described a guarantee the
 * compiler was not providing.
 */
@JvmInline
value class PersonId(val value: String) {
    init {
        require(value.isNotBlank()) { "PersonId must not be blank" }
    }

    override fun toString(): String = value
}

/** Institutional identifier of an academic entity: subject, course, group, room. */
@JvmInline
value class AcademicId(val value: String) {
    init {
        require(value.isNotBlank()) { "AcademicId must not be blank" }
    }

    override fun toString(): String = value
}

/**
 * Identity of any member of the institution.
 *
 * Kept separate from [Student] and [Teacher] so role-specific data lives on
 * the role type, and a `Student` can never be passed where a `Teacher` is
 * expected.
 */
data class Person(
    val id: PersonId,
    val displayName: String,
    val institutionalEmail: String? = null,
) {
    init {
        require(displayName.isNotBlank()) { "displayName must not be blank" }
    }
}

/** A person enrolled in the institution in the student role. */
data class Student(
    val person: Person,
)

/** A person who teaches at least one course. */
data class Teacher(
    val person: Person,
    val departmentCode: String? = null,
)

/**
 * A teachable academic unit (for example "Álgebra", "Historia"). The catalogue
 * entry is independent of any particular term or class.
 */
data class Subject(
    val id: AcademicId,
    val code: String,
    val name: String,
    val credits: Int? = null,
) {
    init {
        require(code.isNotBlank()) { "code must not be blank" }
        require(name.isNotBlank()) { "name must not be blank" }
        require(credits == null || credits >= 0) { "credits must not be negative" }
    }
}

/** A course offering: one [subject] taught in a term by a [teacher]. */
data class Course(
    val id: AcademicId,
    val subject: Subject,
    val teacher: Teacher,
    val term: String,
) {
    init {
        require(term.isNotBlank()) { "term must not be blank" }
    }
}

/** A class group: students who take the same [course] together. */
data class Group(
    val id: AcademicId,
    val course: Course,
    val name: String,
) {
    init {
        require(name.isNotBlank()) { "name must not be blank" }
    }
}

/** A physical campus. */
data class Campus(
    val id: AcademicId,
    val name: String,
)

/** A building inside a [Campus]. */
data class Building(
    val id: AcademicId,
    val campus: Campus,
    val name: String,
    val code: String? = null,
)

/** A classroom inside a [Building]. */
data class Classroom(
    val id: AcademicId,
    val building: Building,
    val name: String,
    val capacity: Int? = null,
    val hasProjector: Boolean = false,
) {
    init {
        require(capacity == null || capacity > 0) { "capacity must be positive when present" }
    }
}

/** A student enrolled in a [Group]. */
data class Enrollment(
    val studentId: PersonId,
    val group: Group,
    val enrolledAt: Instant,
)

/** A single recurring meeting of a [Group]. */
data class ScheduleSlot(
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

/** The weekly timetable of a student or teacher. */
data class Schedule(
    val ownerId: PersonId,
    val slots: List<ScheduleSlot>,
) {
    /**
     * Slots for one weekday, ordered by start time. Kept as a function so
     * callers cannot accidentally cache a stale ordering.
     */
    fun slotsFor(day: DayOfWeek): List<ScheduleSlot> =
        slots.filter { it.dayOfWeek == day }.sortedBy { it.startsAt }
}
