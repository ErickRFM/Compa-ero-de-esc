package org.companerodeescuela.api.attendance

import java.time.LocalDate
import org.companerodeescuela.api.academic.groups.AcademicGroupRepository
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.presence.SchoolPresenceService
import org.companerodeescuela.shared.contracts.CampusRosterStudent
import org.companerodeescuela.shared.contracts.TeacherCampusRosterResponse

/**
 * Bounded class roster: never expose all school presence records to a teacher.
 * The occurrence must belong to this teacher, and an unambiguous native group
 * mapping must be available. Missing mapping is an error, not an empty roster.
 */
class TeacherCampusRosterService(
    private val occurrenceResolver: AttendanceOccurrenceResolver,
    private val groups: AcademicGroupRepository,
    private val presence: SchoolPresenceService,
    private val attendance: AttendanceRepository,
) {
    suspend fun forTeacher(
        teacherId: String,
        occurrenceId: String,
        date: String,
    ): TeacherCampusRosterResponse {
        val parsed = runCatching { LocalDate.parse(date) }
            .getOrElse { throw ApiException.Validation("date must be YYYY-MM-DD") }
        val occurrence = occurrenceResolver.resolveTeacherOccurrence(teacherId, occurrenceId, parsed)
        val name = occurrence.group.name.trim()
        val matching = groups.list().filter { it.active && it.name.trim().equals(name, ignoreCase = true) }
        if (matching.size != 1) {
            throw ApiException.DependencyUnavailable(
                "A unique academic group mapping is required to show the campus roster",
            )
        }
        val ids = groups.memberIdsForGroup(matching.single().id)
        val session = attendance.findSessionByOccurrence(occurrenceId)
        val records = session?.let { attendance.recordsForSession(it.id).map { record -> record.withCurrentPresencePolicy() }.associateBy { record -> record.studentId } }
            .orEmpty()
        return TeacherCampusRosterResponse(
            occurrenceId = occurrenceId,
            groupName = name,
            sessionId = session?.id,
            students = ids.map { id ->
                CampusRosterStudent(
                    studentId = id,
                    campusEntryAtEpochSeconds = presence.activeFor(id)?.startedAtEpochSeconds,
                    classRecord = records[id],
                )
            },
        )
    }
}
