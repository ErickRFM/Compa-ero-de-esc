package org.companerodeescuela.api.attendance

import org.companerodeescuela.api.devices.PushOutboxService
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.AttendanceIdentitySource
import org.companerodeescuela.shared.contracts.AttendancePushContract
import org.companerodeescuela.shared.contracts.AttendanceRosterResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionStatus
import org.companerodeescuela.shared.contracts.CreateAttendanceSessionRequest
import org.companerodeescuela.shared.model.ClassOccurrenceStatus
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class AttendanceSessionService(
    private val repository: AttendanceRepository,
    private val occurrenceResolver: AttendanceOccurrenceResolver,
    private val accessPolicy: AttendanceAccessPolicy = AttendanceAccessPolicy(repository, occurrenceResolver),
    private val clock: Clock = Clock.systemUTC(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val nativeProjection: org.companerodeescuela.api.academic.NativeAcademicProjection? = null,
    private val pushService: PushOutboxService? = null,
) {
    suspend fun openSession(
        teacherId: String,
        request: CreateAttendanceSessionRequest,
        nativeActor: org.companerodeescuela.shared.contracts.UserSummary? = null,
    ): AttendanceSessionResponse {
        val occurrenceId = request.occurrenceId.trim()
        if (occurrenceId.isBlank()) {
            throw ApiException.Validation("occurrenceId is required")
        }
        if (request.durationMinutes !in MIN_DURATION_MINUTES..MAX_DURATION_MINUTES) {
            throw ApiException.Validation("Attendance duration must be between 1 and 15 minutes")
        }
        val occurrenceDate =
            runCatching { LocalDate.parse(request.occurrenceDate) }
                .getOrElse { throw ApiException.Validation("occurrenceDate must be YYYY-MM-DD") }

        val occurrence =
            if (nativeActor != null) {
                if (nativeActor.id != teacherId) throw ApiException.Forbidden("Teacher does not match the actual native actor")
                (
                    nativeProjection ?: throw ApiException.DependencyUnavailable(
                        "Native academic projection is unavailable",
                    )
                ).teacherOccurrence(nativeActor, occurrenceId, occurrenceDate)
            } else {
                occurrenceResolver.resolveTeacherOccurrence(
                    teacherId = teacherId,
                    occurrenceId = occurrenceId,
                    occurrenceDate = occurrenceDate,
                )
            }
        if (occurrence.status == ClassOccurrenceStatus.CANCELLED) {
            throw ApiException.Conflict("Attendance cannot open for a cancelled class")
        }

        val now = clock.instant()
        val scheduledEnd =
            occurrence.date
                .atTime(occurrence.endsAt)
                .atZone(ZoneId.systemDefault())
                .toInstant()
        if (!now.isBefore(scheduledEnd)) {
            throw ApiException.Conflict("Attendance cannot open after the scheduled class has ended")
        }
        val candidate =
            AttendanceSessionResponse(
                id = newId(),
                occurrenceId = occurrence.id.value,
                courseId = occurrence.group.course.id.value,
                groupName = occurrence.group.name,
                occurrenceDate = occurrence.date.toString(),
                scheduledStartsAt = occurrence.startsAt.toString(),
                scheduledEndsAt = occurrence.endsAt.toString(),
                openedBy = teacherId,
                openedAtEpochSeconds = now.epochSecond,
                closesAtEpochSeconds =
                    now
                        .plus(Duration.ofMinutes(request.durationMinutes.toLong()))
                        .epochSecond,
                status = AttendanceSessionStatus.OPEN,
                identitySource = org.companerodeescuela.shared.contracts.AttendanceIdentitySource.INSTITUTIONAL,
            ).let { candidate -> if (nativeActor == null) candidate else nativeProjection!!.bindSession(nativeActor, candidate) }

        val session =
            when (val result = repository.createSession(candidate)) {
                is SessionWriteResult.Created -> result.session
                is SessionWriteResult.Existing -> {
                    if (result.session.identitySource != candidate.identitySource ||
                        result.session.institutionId != candidate.institutionId ||
                        result.session.groupScopeRevision != candidate.groupScopeRevision
                    ) {
                        throw ApiException.Forbidden("Existing attendance belongs to a different identity source or scope")
                    }
                    if (result.session.openedBy != teacherId) {
                        throw ApiException.Conflict("Attendance session already belongs to another teacher")
                    }
                    if (result.session.status != AttendanceSessionStatus.OPEN ||
                        result.session.closedAtEpochSeconds != null ||
                        result.session.closesAtEpochSeconds <= now.epochSecond
                    ) {
                        throw ApiException.Conflict("Attendance session for this occurrence has already ended")
                    }
                    result.session
                }
            }
        if (session.status == AttendanceSessionStatus.OPEN && session.closesAtEpochSeconds > now.epochSecond) {
            enqueueSessionNotice(
                session = session,
                actor = nativeActor,
                eventId = "attendance-session-open:${session.id}",
            )
        }
        return session
    }

    /** FCM delivery is asynchronous; this only commits idempotent destinations to the durable push outbox. */
    suspend fun resendSessionNotice(
        actorId: String,
        sessionId: String,
        allowCrossOwner: Boolean = false,
        nativeActor: org.companerodeescuela.shared.contracts.UserSummary? = null,
    ): Int {
        val session = accessPolicy.requireSession(sessionId)
        accessPolicy.requireOwnerOrAdministrative(actorId, session, allowCrossOwner, nativeActor)
        val now = clock.instant().epochSecond
        if (session.status != AttendanceSessionStatus.OPEN || session.closesAtEpochSeconds <= now) {
            throw ApiException.Conflict("The attendance pass is no longer open")
        }
        return enqueueSessionNotice(
            session = session,
            actor = nativeActor,
            eventId = "attendance-session-reminder:${session.id}:${now / REMINDER_WINDOW_SECONDS}",
        )
    }

    private suspend fun enqueueSessionNotice(
        session: AttendanceSessionResponse,
        actor: org.companerodeescuela.shared.contracts.UserSummary?,
        eventId: String,
    ): Int {
        val push = pushService ?: return 0
        if (actor == null ||
            actor.id != session.openedBy ||
            session.identitySource != AttendanceIdentitySource.NATIVE
        ) {
            return 0
        }
        val projection = nativeProjection ?: return 0
        val recipients = projection.activeStudentIdsForTeacher(actor, session.courseId).distinct()
        val data =
            mapOf(
                "type" to AttendancePushContract.TYPE,
                AttendancePushContract.SESSION_ID to session.id,
            )
        var queued = 0
        for (studentId in recipients) {
            queued +=
                push.enqueueForOwner(
                    eventId = eventId,
                    owner = "NATIVE:$studentId",
                    title = "Pase de asistencia disponible",
                    body = "Tu docente abrió el pase de lista. Toca para revisarlo.",
                    data = data,
                )
        }
        return queued
    }

    suspend fun activeForTeacher(
        teacherId: String,
        nativeActor: org.companerodeescuela.shared.contracts.UserSummary? = null,
    ): List<AttendanceSessionResponse> {
        val now = clock.instant().epochSecond
        val candidates =
            repository
                .findOpenSessions()
                .filter { it.openedBy == teacherId && it.closesAtEpochSeconds > now }
        val authorized = mutableListOf<AttendanceSessionResponse>()
        for (session in candidates) {
            try {
                accessPolicy.requireOwnerOrAdministrative(teacherId, session, allowCrossOwner = false, nativeActor = nativeActor)
                authorized += session
            } catch (_: ApiException.Forbidden) {
                // Revocation removes this resource; unavailable authority fails the request.
            }
        }
        return authorized.sortedBy { it.closesAtEpochSeconds }
    }

    suspend fun historyForTeacher(
        teacherId: String,
        nativeActor: org.companerodeescuela.shared.contracts.UserSummary? = null,
    ): List<AttendanceSessionResponse> {
        val authorized = mutableListOf<AttendanceSessionResponse>()
        for (session in repository.findSessionsOpenedBy(teacherId, HISTORY_LIMIT)) {
            try {
                accessPolicy.requireOwnerOrAdministrative(
                    teacherId,
                    session,
                    allowCrossOwner = false,
                    nativeActor = nativeActor,
                )
                authorized += session
            } catch (_: ApiException.Forbidden) {
                // Revoked teacher assignments and stale tenant scope are hidden from history.
            }
        }
        return authorized.sortedByDescending { it.openedAtEpochSeconds }
    }

    suspend fun activeForAdministration(
        actor: org.companerodeescuela.shared.contracts.UserSummary? = null,
    ): List<AttendanceSessionResponse> {
        val now = clock.instant().epochSecond
        val current = actor ?: throw ApiException.Forbidden("Actual native academic administration is required")
        accessPolicy.requireAdministration(current)
        val authorized = mutableListOf<AttendanceSessionResponse>()
        for (session in repository.findOpenSessions().filter {
            it.status == AttendanceSessionStatus.OPEN && it.closesAtEpochSeconds > now
        }) {
            try {
                accessPolicy.requireOwnerOrAdministrative(current.id, session, true, current)
                authorized += session
            } catch (_: ApiException.Forbidden) {
                // Other tenants and stale class scopes are never disclosed; outages propagate.
            }
        }
        return authorized.sortedBy { it.closesAtEpochSeconds }
    }

    suspend fun closeSession(
        actorId: String,
        sessionId: String,
        allowCrossOwner: Boolean = false,
        nativeActor: org.companerodeescuela.shared.contracts.UserSummary? = null,
    ): AttendanceSessionResponse {
        val session = accessPolicy.requireSession(sessionId)
        accessPolicy.requireOwnerOrAdministrative(actorId, session, allowCrossOwner, nativeActor)
        if (session.status == AttendanceSessionStatus.CLOSED) return session

        val closed =
            session.copy(
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
        nativeActor: org.companerodeescuela.shared.contracts.UserSummary? = null,
    ): AttendanceRosterResponse {
        val session = accessPolicy.requireSession(sessionId)
        accessPolicy.requireOwnerOrAdministrative(actorId, session, allowCrossOwner, nativeActor)
        return AttendanceRosterResponse(
            session = session,
            records = repository.recordsForSession(sessionId).map { it.withCurrentPresencePolicy() }.sortedBy { it.studentId },
        )
    }

    private companion object {
        const val MIN_DURATION_MINUTES = 1
        const val MAX_DURATION_MINUTES = 15
        const val REMINDER_WINDOW_SECONDS = 60L
        const val HISTORY_LIMIT = 100
    }
}
