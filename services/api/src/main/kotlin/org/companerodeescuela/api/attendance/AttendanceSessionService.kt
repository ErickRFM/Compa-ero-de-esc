package org.companerodeescuela.api.attendance

import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.util.UUID
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.AttendanceRosterResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionStatus
import org.companerodeescuela.shared.contracts.CreateAttendanceSessionRequest
import org.companerodeescuela.shared.model.ClassOccurrenceStatus

class AttendanceSessionService(
    private val repository: AttendanceRepository,
    private val occurrenceResolver: AttendanceOccurrenceResolver,
    private val accessPolicy: AttendanceAccessPolicy = AttendanceAccessPolicy(repository),
    private val clock: Clock = Clock.systemUTC(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun openSession(
        teacherId: String,
        request: CreateAttendanceSessionRequest,
    ): AttendanceSessionResponse {
        val occurrenceId = request.occurrenceId.trim()
        if (occurrenceId.isBlank()) {
            throw ApiException.Validation("occurrenceId is required")
        }
        if (request.durationMinutes !in MIN_DURATION_MINUTES..MAX_DURATION_MINUTES) {
            throw ApiException.Validation("Attendance duration must be between 1 and 15 minutes")
        }
        val occurrenceDate = runCatching { LocalDate.parse(request.occurrenceDate) }
            .getOrElse { throw ApiException.Validation("occurrenceDate must be YYYY-MM-DD") }

        val occurrence = occurrenceResolver.resolveTeacherOccurrence(
            teacherId = teacherId,
            occurrenceId = occurrenceId,
            occurrenceDate = occurrenceDate,
        )
        if (occurrence.status == ClassOccurrenceStatus.CANCELLED) {
            throw ApiException.Conflict("Attendance cannot open for a cancelled class")
        }

        val now = clock.instant()
        val candidate = AttendanceSessionResponse(
            id = newId(),
            occurrenceId = occurrence.id.value,
            courseId = occurrence.group.course.id.value,
            groupName = occurrence.group.name,
            occurrenceDate = occurrence.date.toString(),
            scheduledStartsAt = occurrence.startsAt.toString(),
            scheduledEndsAt = occurrence.endsAt.toString(),
            openedBy = teacherId,
            openedAtEpochSeconds = now.epochSecond,
            closesAtEpochSeconds = now.plus(Duration.ofMinutes(request.durationMinutes.toLong()))
                .epochSecond,
            status = AttendanceSessionStatus.OPEN,
        )

        return when (val result = repository.createSession(candidate)) {
            is SessionWriteResult.Created -> result.session
            is SessionWriteResult.Existing -> {
                if (result.session.openedBy != teacherId) {
                    throw ApiException.Conflict("Attendance session already belongs to another teacher")
                }
                result.session
            }
        }
    }

    suspend fun activeForTeacher(teacherId: String): List<AttendanceSessionResponse> {
        val now = clock.instant().epochSecond
        return repository.findOpenSessions()
            .filter { it.openedBy == teacherId && it.closesAtEpochSeconds > now }
            .sortedBy { it.closesAtEpochSeconds }
    }

    suspend fun activeForAdministration(): List<AttendanceSessionResponse> {
        val now = clock.instant().epochSecond
        return repository.findOpenSessions()
            .filter { it.status == AttendanceSessionStatus.OPEN && it.closesAtEpochSeconds > now }
            .sortedBy { it.closesAtEpochSeconds }
    }

    suspend fun closeSession(
        actorId: String,
        sessionId: String,
        allowCrossOwner: Boolean = false,
    ): AttendanceSessionResponse {
        val session = accessPolicy.requireSession(sessionId)
        accessPolicy.requireOwnerOrAdministrative(actorId, session, allowCrossOwner)
        if (session.status == AttendanceSessionStatus.CLOSED) return session

        val closed = session.copy(
            status = AttendanceSessionStatus.CLOSED,
            closedAtEpochSeconds = clock.instant().epochSecond,
        )
        repository.replaceSession(closed)
        return closed
    }

    suspend fun roster(
        actorId: String,
        sessionId: String,
        allowCrossOwner: Boolean = false,
    ): AttendanceRosterResponse {
        val session = accessPolicy.requireSession(sessionId)
        accessPolicy.requireOwnerOrAdministrative(actorId, session, allowCrossOwner)
        return AttendanceRosterResponse(
            session = session,
            records = repository.recordsForSession(sessionId).sortedBy { it.studentId },
        )
    }

    private companion object {
        const val MIN_DURATION_MINUTES = 1
        const val MAX_DURATION_MINUTES = 15
    }
}
