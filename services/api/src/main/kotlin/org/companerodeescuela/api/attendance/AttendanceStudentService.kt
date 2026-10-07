package org.companerodeescuela.api.attendance

import io.ktor.http.HttpStatusCode
import java.time.Clock
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.presence.SchoolPresenceService
import org.companerodeescuela.shared.contracts.ApiErrorCode
import org.companerodeescuela.shared.contracts.AttendanceAttemptRequest
import org.companerodeescuela.shared.contracts.AttendanceQrInspectionRequest
import org.companerodeescuela.shared.contracts.AttendanceQrInspectionResponse
import org.companerodeescuela.shared.contracts.AttendanceQrInspectionStatus
import org.companerodeescuela.shared.contracts.AttendanceReasonCode
import org.companerodeescuela.shared.contracts.AttendanceRecordResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionStatus
import org.companerodeescuela.shared.contracts.AttendanceStatus

class AttendanceStudentService(
    private val repository: AttendanceRepository,
    private val enrollmentResolver: AttendanceEnrollmentResolver,
    private val qrService: AttendanceQrService? = null,
    private val schoolPresenceService: SchoolPresenceService? = null,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun activeFor(studentId: String): List<AttendanceSessionResponse> {
        schoolPresenceService?.requireActive(studentId)
        val enrolled = enrollmentResolver.courseGroupsFor(studentId)
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

        if (!enrollmentResolver.isEnrolled(studentId, session.courseId, session.groupName)) {
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
            ?: throw ApiException.DependencyUnavailable(
                "Attendance QR verification is not configured",
            )
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

        if (!enrollmentResolver.isEnrolled(studentId, session.courseId, session.groupName)) {
            throw ApiException.Domain(
                status = HttpStatusCode.Forbidden,
                code = ApiErrorCode.ATTENDANCE_NOT_ENROLLED,
                message = "You are not enrolled in this class",
            )
        }

        schoolPresenceService?.let { presence ->
            presence.requireActive(studentId)
            val network = request.schoolNetwork
                ?: throw ApiException.Forbidden(
                    "Reconnect to the school Wi-Fi before confirming attendance",
                )
            presence.verifyNetworkForAttendance(network)
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
            ?: throw ApiException.DependencyUnavailable(
                "Attendance QR verification is not configured",
            )
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

    private companion object {
        const val MAX_OPERATION_ID_LENGTH = 128
        const val MAX_QR_TOKEN_LENGTH = 2_048
        const val LATE_SYNC_REVIEW_WINDOW_SECONDS = 24L * 60L * 60L
    }
}
