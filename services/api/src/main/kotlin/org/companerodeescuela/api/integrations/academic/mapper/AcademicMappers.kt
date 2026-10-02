package org.companerodeescuela.api.integrations.academic.mapper

import java.time.Instant
import org.companerodeescuela.api.integrations.IntegrationException
import org.companerodeescuela.api.integrations.academic.dto.ExternalCourse
import org.companerodeescuela.api.integrations.academic.dto.ExternalScheduleSlot
import org.companerodeescuela.api.integrations.academic.dto.ExternalStudent
import org.companerodeescuela.api.integrations.academic.dto.ExternalSubject
import org.companerodeescuela.api.integrations.academic.dto.ExternalTeacher
import org.companerodeescuela.api.integrations.academic.dto.ExternalWeekdays
import org.companerodeescuela.api.integrations.academic.dto.parseUpstreamTime
import org.companerodeescuela.shared.model.AcademicId
import org.companerodeescuela.shared.model.Campus
import org.companerodeescuela.shared.model.Building
import org.companerodeescuela.shared.model.Classroom
import org.companerodeescuela.shared.model.Course
import org.companerodeescuela.shared.model.Enrollment
import org.companerodeescuela.shared.model.Group
import org.companerodeescuela.shared.model.Person
import org.companerodeescuela.shared.model.PersonId
import org.companerodeescuela.shared.model.Schedule
import org.companerodeescuela.shared.model.ScheduleSlot
import org.companerodeescuela.shared.model.Student
import org.companerodeescuela.shared.model.Subject
import org.companerodeescuela.shared.model.Teacher

/**
 * Converts external DTOs into domain models.
 *
 * This is the boundary that keeps institutional quirks (Spanish weekday names,
 * free-form identifiers, times without dates, inconsistent ids) from spreading
 * through the platform. After this class runs, the rest of the code only sees
 * [org.companerodeescuela.shared.model] types.
 *
 * Mapping is total where it can be and explicit-failing where it cannot: a slot
 * with an unrecognised weekday is a data problem, and silently dropping it
 * would hide a broken integration from users.
 */
object AcademicMappers {

    const val PROVIDER_ID = "academic"

    /** Placeholders used when upstream returns a schedule without course data. */
    const val UNKNOWN_COURSE = "Materia no disponible"
    const val UNKNOWN_TEACHER = "Profesor no disponible"
    const val UNKNOWN_TERM = "sin-periodo"

    fun toStudent(student: ExternalStudent): Student = Student(
        person = Person(
            id = PersonId(student.externalId),
            displayName = student.fullName.trim(),
            institutionalEmail = student.institutionalEmail?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
        ),
    )

    fun toTeacher(teacher: ExternalTeacher): Teacher = Teacher(
        person = Person(
            id = PersonId(teacher.externalId),
            displayName = teacher.fullName.trim(),
            institutionalEmail = teacher.institutionalEmail?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
        ),
        departmentCode = teacher.departmentCode?.trim()?.takeIf { it.isNotEmpty() },
    )

    fun toSubject(subject: ExternalSubject): Subject = Subject(
        id = AcademicId(subject.externalCode),
        code = subject.code(),
        name = subject.name.trim(),
        credits = subject.credits,
    )

    private fun ExternalSubject.code(): String = externalCode.trim()

    fun toCourse(course: ExternalCourse): Course = Course(
        id = AcademicId(course.externalId),
        subject = toSubject(course.subject),
        teacher = toTeacher(course.teacher),
        term = course.term.trim(),
    )

    fun toGroup(course: ExternalCourse): Group = Group(
        id = AcademicId("${course.externalId}:${course.groupName.trim()}"),
        course = toCourse(course),
        name = course.groupName.trim(),
    )

    fun toEnrollment(
        studentId: PersonId,
        externalEnrollment: org.companerodeescuela.api.integrations.academic.dto.ExternalEnrollment,
    ): Enrollment = Enrollment(
        studentId = studentId,
        group = toGroup(externalEnrollment.course),
        enrolledAt = parseInstant(externalEnrollment.enrolledAtIso),
    )

    /**
     * Maps every slot of a student's schedule.
     *
     * @param coursesById the caller's enrolled courses, keyed by upstream id.
     *   Schedule endpoints usually return a reference only, so the real
     *   subject and teacher data is resolved from here instead of being
     *   invented from the id.
     *
     * Throws [IntegrationException] with `MALFORMED_RESPONSE` when a slot cannot
     * be interpreted, so the caller can decide whether to fail the request or
     * serve a partial schedule with a warning.
     */
    fun toSchedule(
        ownerId: PersonId,
        slots: List<ExternalScheduleSlot>,
        coursesById: Map<String, ExternalCourse> = emptyMap(),
    ): Schedule {
        val mapped = slots.map { slot ->
            val day = ExternalWeekdays.fromName(slot.weekdayName)
                ?: throw malformed("Unrecognised weekday '${slot.weekdayName}'")
            val start = parseUpstreamTime(slot.startTime)
                ?: throw malformed("Unparsable start time '${slot.startTime}'")
            val end = parseUpstreamTime(slot.endTime)
                ?: throw malformed("Unparsable end time '${slot.endTime}'")
            if (start >= end) {
                throw malformed("Slot for '${slot.courseId}' ends before it starts")
            }

            ScheduleSlot(
                group = toGroup(slot.toCourseReference(coursesById)),
                dayOfWeek = day,
                startsAt = start,
                endsAt = end,
                classroom = toClassroom(slot),
            )
        }
        return Schedule(ownerId = ownerId, slots = mapped)
    }

    /**
     * Resolves a schedule entry against the enrolled courses.
     *
     * When the course is not in [coursesById] the slot is still mapped, but
     * with an explicitly unnamed subject and an unknown teacher. That is better
     * than dropping the class from a student's timetable, and the caller can
     * detect it because [Subject.name] is `UNKNOWN_COURSE`.
     */
    private fun ExternalScheduleSlot.toCourseReference(
        coursesById: Map<String, ExternalCourse>,
    ): ExternalCourse = coursesById[this.courseId] ?: ExternalCourse(
        externalId = courseId,
        subject = ExternalSubject(
            externalCode = courseId,
            name = UNKNOWN_COURSE,
        ),
        teacher = ExternalTeacher(
            externalId = courseId,
            fullName = UNKNOWN_TEACHER,
        ),
        term = UNKNOWN_TERM,
        groupName = groupName,
    )

    private fun toClassroom(slot: ExternalScheduleSlot): Classroom? {
        val classroomName = slot.classroomCode?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val campusName = slot.campusName?.trim()?.takeIf { it.isNotEmpty() }
        val buildingName = slot.buildingCode?.trim()?.takeIf { it.isNotEmpty() } ?: "Sin edificio"

        val campus = campusName?.let {
            Campus(id = AcademicId(slug(it)), name = it)
        } ?: Campus(id = AcademicId("unassigned"), name = "Sin campus")

        val building = Building(
            id = AcademicId("${campus.id.value}:$buildingName"),
            campus = campus,
            name = buildingName,
        )

        return Classroom(
            id = AcademicId("${building.id.value}:$classroomName"),
            building = building,
            name = classroomName,
        )
    }

    private fun slug(value: String): String = value
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifEmpty { "unknown" }

    private fun parseInstant(raw: String): Instant =
        runCatching { Instant.parse(raw) }.getOrElse {
            throw malformed("Unparsable timestamp '$raw'")
        }

    private fun malformed(detail: String): IntegrationException = IntegrationException(
        providerId = PROVIDER_ID,
        category = IntegrationException.Category.MALFORMED_RESPONSE,
        message = detail,
    )
}
