package org.companerodeescuela.api.attendance

import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.util.UUID
import io.ktor.http.HttpStatusCode
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.IntegrationException
import org.companerodeescuela.api.integrations.academic.AcademicProvider
import org.companerodeescuela.shared.contracts.ApiErrorCode
import org.companerodeescuela.shared.contracts.AttendanceAttemptRequest
import org.companerodeescuela.shared.contracts.AttendanceQrInspectionRequest
import org.companerodeescuela.shared.contracts.AttendanceQrInspectionResponse
import org.companerodeescuela.shared.contracts.AttendanceQrInspectionStatus
import org.companerodeescuela.shared.contracts.AttendanceReasonCode
import org.companerodeescuela.shared.contracts.AttendanceRecordResponse
import org.companerodeescuela.shared.contracts.AttendanceRosterResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionStatus
import org.companerodeescuela.shared.contracts.AttendanceStatus
import org.companerodeescuela.shared.contracts.CreateAttendanceSessionRequest
import org.companerodeescuela.shared.contracts.ReviewAttendanceRequest
import org.companerodeescuela.shared.model.ClassOccurrenceStatus

class AttendanceService(
    private val repository: AttendanceRepository,
    private val academicProvider: AcademicProvider,
    private val occurrenceResolver: AttendanceOccurrenceResolver =
        ProviderAttendanceOccurrenceResolver(academicProvider),
    private val qrService: AttendanceQrService? = null,
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
            throw ApiException.Validation(
                "Attendance duration must be between 1 and 15 minutes",
            )
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
            closesAtEpochSeconds = now.plus(
                Duration.ofMinutes(request.durationMinutes.toLong()),
            ).epochSecond,
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
            .filter { session ->
                session.openedBy == teacherId &&
                    session.closesAtEpochSeconds > now
            }
            .sortedBy { it.closesAtEpochSeconds }
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

    suspend fun inspectQr(
        studentId: String,
        request: AttendanceQrInspectionRequest,
    ): AttendanceQrInspectionResponse {
        val sessionId = request.sessionId.trim()
        val token = request.token.trim()
        if (sessionId.isBlank() || token.isBlank() || token.length > MAX_QR_TOKEN_LENGTH) {
            throw ApiException.Validation("QR inspection payload is invalid")
        }

        val session = repository.findSession(sessionId)
            ?: return AttendanceQrInspectionResponse(
                status = AttendanceQrInspectionStatus.INVALID,
            )

        val load = academicLoad(studentId)
        val enrolled = load.enrollments.any {
            it.course.externalId == session.courseId &&
                it.course.groupName.trim() == session.groupName
        }
        if (!enrolled) {
            return AttendanceQrInspectionResponse(
                status = AttendanceQrInspectionStatus.NOT_ENROLLED,
            )
        }

        val now = clock.instant().epochSecond
        if (
            session.status != AttendanceSessionStatus.OPEN ||
            session.closesAtEpochSeconds <= now
        ) {
            return AttendanceQrInspectionResponse(
                status = AttendanceQrInspectionStatus.SESSION_CLOSED,
                session = session,
            )
        }

        val verifier = qrService
            ?: throw ApiException.DependencyUnavailable("Attendance QR verification is not configured")
        return when (
            val result = verifier.verify(
                token = token,
                expectedSessionId = session.id,
                receivedAtEpochSeconds = now,
            )
        ) {
            is QrEvidenceResult.Valid -> AttendanceQrInspectionResponse(
                status = AttendanceQrInspectionStatus.VALID,
                session = session,
                expiresAtEpochSeconds = result.expiresAtEpochSeconds,
            )
            is QrEvidenceResult.Expired -> AttendanceQrInspectionResponse(
                status = AttendanceQrInspectionStatus.EXPIRED,
                session = session,
                expiresAtEpochSeconds = result.expiresAtEpochSeconds,
            )
            QrEvidenceResult.WrongSession -> AttendanceQrInspectionResponse(
                status = AttendanceQrInspectionStatus.WRONG_SESSION,
            )
            QrEvidenceResult.Invalid -> AttendanceQrInspectionResponse(
                status = AttendanceQrInspectionStatus.INVALID,
            )
        }
    }

    suspend fun register(
        studentId: String,
        sessionId: String,
        request: AttendanceAttemptRequest,
    ): AttendanceRecordResponse {
        val operationId = request.operationId.trim()
        if (operationId.isBlank() || operationId.length > MAX_OPERATION_ID_LENGTH) {
            throw ApiException.Validation("operationId must be between 1 and 128 characters")
        }
        if (request.deviceTimestampEpochSeconds <= 0) {
            throw ApiException.Validation("device timestamp is invalid")
        }

        val session = repository.findSession(sessionId)
            ?: throw ApiException.NotFound("Attendance session was not found")
        val now = clock.instant()
        val effectiveClose = session.closedAtEpochSeconds ?: session.closesAtEpochSeconds
        val currentlyOpen =
            session.status == AttendanceSessionStatus.OPEN &&
                session.closesAtEpochSeconds > now.epochSecond
        val attemptWasInsideWindow =
            request.deviceTimestampEpochSeconds >= session.openedAtEpochSeconds &&
                request.deviceTimestampEpochSeconds <= effectiveClose
        val lateSyncEligible =
            !currentlyOpen &&
                attemptWasInsideWindow &&
                now.epochSecond <= effectiveClose + LATE_SYNC_REVIEW_WINDOW_SECONDS

        if (!currentlyOpen && !lateSyncEligible) {
            throw ApiException.Domain(
                status = HttpStatusCode.Conflict,
                code = ApiErrorCode.ATTENDANCE_SESSION_CLOSED,
                message = "Attendance session is closed",
            )
        }

        val load = academicLoad(studentId)
        val enrolled = load.enrollments.any {
            it.course.externalId == session.courseId &&
                it.course.groupName.trim() == session.groupName
        }
        if (!enrolled) {
            throw ApiException.Domain(
                status = HttpStatusCode.Forbidden,
                code = ApiErrorCode.ATTENDANCE_NOT_ENROLLED,
                message = "You are not enrolled in this class",
            )
        }

        val evidence = classifyEvidence(
            request = request,
            sessionId = session.id,
            lateSyncEligible = lateSyncEligible,
            receivedAtEpochSeconds = now.epochSecond,
        )

        val candidate = AttendanceRecordResponse(
            id = session.id + ":" + studentId,
            operationId = operationId,
            sessionId = session.id,
            occurrenceId = session.occurrenceId,
            studentId = studentId,
            status = evidence.first,
            reasonCode = evidence.second,
            attemptedAtEpochSeconds = request.deviceTimestampEpochSeconds,
            receivedAtEpochSeconds = now.epochSecond,
        )

        return when (val result = repository.writeAttempt(candidate)) {
            is AttemptWriteResult.Created -> result.record
            is AttemptWriteResult.Existing -> result.record
            AttemptWriteResult.OperationConflict ->
                throw ApiException.Domain(
                    status = HttpStatusCode.Conflict,
                    code = ApiErrorCode.ATTENDANCE_OPERATION_CONFLICT,
                    message = "operationId was already used for another attendance",
                )
        }
    }

    suspend fun closeSession(
        actorId: String,
        sessionId: String,
        allowCrossOwner: Boolean = false,
    ): AttendanceSessionResponse {
        val session = repository.findSession(sessionId)
            ?: throw ApiException.NotFound("Attendance session was not found")
        requireOwnerOrAdministrative(actorId, session, allowCrossOwner)
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
        val session = repository.findSession(sessionId)
            ?: throw ApiException.NotFound("Attendance session was not found")
        requireOwnerOrAdministrative(actorId, session, allowCrossOwner)

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
        allowCrossOwner: Boolean = false,
    ): AttendanceRecordResponse {
        val record = repository.findRecord(recordId)
            ?: throw ApiException.NotFound("Attendance record was not found")
        val session = repository.findSession(record.sessionId)
            ?: throw ApiException.NotFound("Attendance session was not found")
        requireOwnerOrAdministrative(reviewerId, session, allowCrossOwner)

        val reviewed = record.copy(
            status = request.status,
            reasonCode = request.reasonCode,
            reviewedBy = reviewerId,
            reviewedAtEpochSeconds = clock.instant().epochSecond,
        )
        repository.replaceRecord(reviewed)
        return reviewed
    }

    private fun classifyEvidence(
        request: AttendanceAttemptRequest,
        sessionId: String,
        lateSyncEligible: Boolean,
        receivedAtEpochSeconds: Long,
    ): Pair<AttendanceStatus, AttendanceReasonCode> {
        val qrToken = request.qrToken?.trim()?.takeIf(String::isNotEmpty)
        if (qrToken == null) {
            return if (lateSyncEligible) {
                AttendanceStatus.REVIEW_REQUIRED to AttendanceReasonCode.OFFLINE_LATE_SYNC
            } else {
                AttendanceStatus.LIKELY to AttendanceReasonCode.IDENTITY_SESSION_TIME
            }
        }

        val verifier = qrService
            ?: throw ApiException.DependencyUnavailable("Attendance QR verification is not configured")
        return when (
            val qr = verifier.verify(
                token = qrToken,
                expectedSessionId = sessionId,
                receivedAtEpochSeconds = receivedAtEpochSeconds,
            )
        ) {
            is QrEvidenceResult.Valid -> {
                if (
                    request.deviceTimestampEpochSeconds in
                    qr.issuedAtEpochSeconds..qr.expiresAtEpochSeconds
                ) {
                    if (lateSyncEligible) {
                        AttendanceStatus.REVIEW_REQUIRED to AttendanceReasonCode.OFFLINE_LATE_SYNC
                    } else {
                        AttendanceStatus.VERIFIED to AttendanceReasonCode.QR_VALID
                    }
                } else {
                    AttendanceStatus.REJECTED to AttendanceReasonCode.QR_EXPIRED
                }
            }
            is QrEvidenceResult.Expired -> {
                if (
                    request.deviceTimestampEpochSeconds in
                    qr.issuedAtEpochSeconds..qr.expiresAtEpochSeconds
                ) {
                    AttendanceStatus.REVIEW_REQUIRED to AttendanceReasonCode.OFFLINE_LATE_SYNC
                } else {
                    AttendanceStatus.REJECTED to AttendanceReasonCode.QR_EXPIRED
                }
            }
            QrEvidenceResult.WrongSession ->
                AttendanceStatus.REJECTED to AttendanceReasonCode.WRONG_SESSION
            QrEvidenceResult.Invalid ->
                AttendanceStatus.REJECTED to AttendanceReasonCode.QR_INVALID
        }
    }

    private fun requireOwnerOrAdministrative(
        actorId: String,
        session: AttendanceSessionResponse,
        allowCrossOwner: Boolean,
    ) {
        if (!allowCrossOwner && session.openedBy != actorId) {
            throw ApiException.Forbidden("This attendance session belongs to another teacher")
        }
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

    private companion object {
        const val MIN_DURATION_MINUTES = 1
        const val MAX_DURATION_MINUTES = 15
        const val MAX_OPERATION_ID_LENGTH = 128
        const val MAX_QR_TOKEN_LENGTH = 2_048
        const val LATE_SYNC_REVIEW_WINDOW_SECONDS = 24L * 60L * 60L
    }
}
