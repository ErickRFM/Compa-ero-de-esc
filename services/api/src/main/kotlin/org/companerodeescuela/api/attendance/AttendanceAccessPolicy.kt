package org.companerodeescuela.api.attendance

import java.time.LocalDate
import java.time.format.DateTimeParseException
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse
import org.companerodeescuela.shared.model.ClassOccurrenceStatus

class AttendanceAccessPolicy(
    private val repository: AttendanceRepository,
    private val occurrenceResolver: AttendanceOccurrenceResolver? = null,
) {
    suspend fun requireSession(sessionId: String): AttendanceSessionResponse =
        repository.findSession(sessionId)
            ?: throw ApiException.NotFound("Attendance session was not found")

    suspend fun requireOwnerOrAdministrative(
        actorId: String,
        session: AttendanceSessionResponse,
        allowCrossOwner: Boolean,
    ) {
        if (allowCrossOwner) return
        if (session.openedBy != actorId) {
            throw ApiException.Forbidden("This attendance session belongs to another teacher")
        }
        val resolver = occurrenceResolver
            ?: throw ApiException.DependencyUnavailable("Current teaching assignment verification is not configured")
        val date = try { LocalDate.parse(session.occurrenceDate) } catch (_: DateTimeParseException) {
            throw ApiException.Forbidden("This attendance session has no current teaching assignment")
        }
        val occurrence = try {
            resolver.resolveTeacherOccurrence(actorId, session.occurrenceId, date)
        } catch (_: ApiException.NotFound) {
            throw ApiException.Forbidden("This attendance session has no current teaching assignment")
        }
        if (occurrence.id.value != session.occurrenceId || occurrence.date != date ||
            occurrence.teacher.person.id.value != actorId || occurrence.status == ClassOccurrenceStatus.CANCELLED ||
            occurrence.group.course.id.value != session.courseId || occurrence.group.name != session.groupName
        ) {
            throw ApiException.Forbidden("This attendance session has no current teaching assignment")
        }
    }
}
