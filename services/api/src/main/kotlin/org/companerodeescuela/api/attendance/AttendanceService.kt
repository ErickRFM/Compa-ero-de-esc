package org.companerodeescuela.api.attendance

import java.time.Clock
import java.time.Duration
import java.util.UUID
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.IntegrationException
import org.companerodeescuela.api.integrations.academic.AcademicProvider
import org.companerodeescuela.shared.contracts.AttendanceAttemptRequest
import org.companerodeescuela.shared.contracts.AttendanceRecordResponse
import org.companerodeescuela.shared.contracts.AttendanceRosterResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionStatus
import org.companerodeescuela.shared.contracts.AttendanceStatus
import org.companerodeescuela.shared.contracts.CreateAttendanceSessionRequest
import org.companerodeescuela.shared.contracts.ReviewAttendanceRequest

class AttendanceService(
    private val repository: AttendanceRepository,
    private val academicProvider: AcademicProvider,
    private val clock: Clock = Clock.systemUTC(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun openSession(
        teacherId: String,
        request: CreateAttendanceSessionRequest,
    ): AttendanceSessionResponse {
        val courseId = request.courseId.trim()
        val groupName = request.groupName.trim()
        if (courseId.isBlank() || groupName.isBlank()) {
            throw ApiException.Validation("Course and group are required")
        }
        if (request.durationMinutes !in MIN_DURATION_MINUTES..MAX_DURATION_MINUTES) {
            throw ApiException.Validation(
                "Attendance duration must be between 1 and 15 minutes",
            )
        }

        val now = clock.instant()
        val duplicate = repository.findOpenSessions().firstOrNull {
            it.courseId == courseId && it.groupName == groupName &&
                it.closesAtEpochSeconds > now.epochSecond
        }
        if (duplicate != null) {
            throw ApiException.Conflict("An attendance session is already open for this class")
        }

        return repository.createSession(
            AttendanceSessionResponse(
                id = newId(),
                courseId = courseId,
                groupName = groupName,
                openedBy = teacherId,
                openedAtEpochSeconds = now.epochSecond,
                closesAtEpochSeconds = now.plus(
                    Duration.ofMinutes(request.durationMinutes.toLong()),
                ).epochSecond,
                status = AttendanceSessionStatus.OPEN,
            ),
        )
    }

    suspend fun activeFor(studentId: String): List<AttendanceSessionResponse> {
        val load = academicLoad(studentId)
        val enrolled = load.enrollments
            .map { it.course.externalId to it.course.groupName.trim() }
            .toSet()
        val now = clock.instant().epochSecond

        return repository.findOpenSessions().filter { session ->
            session.closesAtEpochSeconds > now &&
                (session.courseId to session.groupName) in enrolled
        }
    }

    suspend fun register(
        studentId: String,
        sessionId: String,
        request: AttendanceAttemptRequest,
    ): AttendanceRecordResponse {
        val operationId = request.operationId.trim()
        if (operationId.isBlank()) {
            throw ApiException.Validation("operationId is required")
        }

        val session = repository.findSession(sessionId)
            ?: throw ApiException.NotFound("Attendance session was not found")
        val now = clock.instant()
        if (
            session.status != AttendanceSessionStatus.OPEN ||
            session.closesAtEpochSeconds <= now.epochSecond
        ) {
            throw ApiException.Conflict("Attendance session is closed")
        }

        val load = academicLoad(studentId)
        val enrolled = load.enrollments.any {
            it.course.externalId == session.courseId &&
                it.course.groupName.trim() == session.groupName
        }
        if (!enrolled) {
            throw ApiException.Forbidden("You are not enrolled in this class")
        }

        val record = AttendanceRecordResponse(
            id = session.id + ":" + studentId,
            operationId = operationId,
            sessionId = session.id,
            studentId = studentId,
            status = AttendanceStatus.LIKELY,
            reasonCode = REASON_IDENTITY_SESSION_TIME,
            attemptedAtEpochSeconds = request.deviceTimestampEpochSeconds,
            receivedAtEpochSeconds = now.epochSecond,
        )

        return when (val result = repository.writeAttempt(record)) {
            is AttemptWriteResult.Created -> result.record
            is AttemptWriteResult.Existing -> result.record
            AttemptWriteResult.OperationConflict ->
                throw ApiException.Conflict("operationId was already used for another attendance")
        }
    }

    suspend fun closeSession(
        actorId: String,
        sessionId: String,
    ): AttendanceSessionResponse {
        val session = repository.findSession(sessionId)
            ?: throw ApiException.NotFound("Attendance session was not found")
        if (session.openedBy != actorId) {
            throw ApiException.Forbidden("Only the teacher who opened the session can close it")
        }
        if (session.status == AttendanceSessionStatus.CLOSED) return session

        val closed = session.copy(
            status = AttendanceSessionStatus.CLOSED,
            closedAtEpochSeconds = clock.instant().epochSecond,
        )
        repository.replaceSession(closed)
        return closed
    }

    suspend fun roster(sessionId: String): AttendanceRosterResponse {
        val session = repository.findSession(sessionId)
            ?: throw ApiException.NotFound("Attendance session was not found")
        return AttendanceRosterResponse(
            session = session,
            records = repository.recordsForSession(sessionId)
                .sortedBy { it.studentId },
        )
    }

    suspend fun review(
        reviewerId: String,
        recordId: String,
        request: ReviewAttendanceRequest,
    ): AttendanceRecordResponse {
        if (request.reasonCode.isBlank()) {
            throw ApiException.Validation("A review reason is required")
        }
        val record = repository.findRecord(recordId)
            ?: throw ApiException.NotFound("Attendance record was not found")
        val reviewed = record.copy(
            status = request.status,
            reasonCode = request.reasonCode.trim(),
            reviewedBy = reviewerId,
            reviewedAtEpochSeconds = clock.instant().epochSecond,
        )
        repository.replaceRecord(reviewed)
        return reviewed
    }

    private suspend fun academicLoad(studentId: String) =
        try {
            academicProvider.getAcademicLoad(studentId)
        } catch (error: IntegrationException) {
            when (error.category) {
                IntegrationException.Category.NOT_FOUND ->
                    throw ApiException.NotFound("Academic record was not found")
                IntegrationException.Category.UNAUTHORIZED ->
                    throw ApiException.Unauthorized("Institutional session is no longer valid")
                IntegrationException.Category.UNAVAILABLE,
                IntegrationException.Category.TIMEOUT,
                -> throw ApiException.DependencyUnavailable(
                    "The academic system is temporarily unavailable",
                    error,
                )
                IntegrationException.Category.BAD_REQUEST,
                IntegrationException.Category.MALFORMED_RESPONSE,
                -> throw ApiException.Internal(
                    "The academic system returned data we could not use",
                    error,
                )
            }
        }

    companion object {
        const val REASON_IDENTITY_SESSION_TIME = "IDENTITY_SESSION_TIME"
        private const val MIN_DURATION_MINUTES = 1
        private const val MAX_DURATION_MINUTES = 15
    }
}
