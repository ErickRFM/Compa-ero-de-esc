package org.companerodeescuela.api.attendance

import io.ktor.http.HttpStatusCode
import java.time.Clock
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.presence.SchoolPresenceService
import org.companerodeescuela.shared.contracts.ApiErrorCode
import org.companerodeescuela.shared.contracts.AttendanceAttemptRequest
import org.companerodeescuela.shared.contracts.ClassCallConfirmationRequest
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
    private val nativeProjection:org.companerodeescuela.api.academic.NativeAcademicProjection?=null,
    private val lateSyncReviewWindowSeconds: Long = ApiSettings.DEFAULT_LATE_SYNC_REVIEW_WINDOW_SECONDS,
    private val qrNonceReuseStudentThreshold: Int = ApiSettings.DEFAULT_ATTENDANCE_QR_NONCE_REUSE_THRESHOLD,
) {
    init {
        require(lateSyncReviewWindowSeconds in 1L..24L * 60L * 60L)
        require(qrNonceReuseStudentThreshold in 1..1_000)
    }

    suspend fun activeFor(studentId: String,nativeActor:org.companerodeescuela.shared.contracts.UserSummary?=null): List<AttendanceSessionResponse> {
        schoolPresenceService?.requireActive(studentId,nativeActor)
        val enrolled = enrollmentResolver.courseGroupsFor(studentId,nativeActor)
        val now = clock.instant().epochSecond

        return repository.findOpenSessions().filter { session ->
            session.closesAtEpochSeconds > now &&
                (session.courseId to session.groupName) in enrolled && isEnrolled(studentId,session,nativeActor)
        }
    }

    suspend fun inspectQr(
        studentId: String,
        request: AttendanceQrInspectionRequest,
        nativeActor:org.companerodeescuela.shared.contracts.UserSummary?=null,
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

        if (!isEnrolled(studentId,session,nativeActor)) {
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
        nativeActor:org.companerodeescuela.shared.contracts.UserSummary?=null,
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
                now.epochSecond <= effectiveClose + lateSyncReviewWindowSeconds

        if (!currentlyOpen && !lateSyncEligible) {
            throw ApiException.Domain(
                status = HttpStatusCode.Conflict,
                code = ApiErrorCode.ATTENDANCE_SESSION_CLOSED,
                message = "Attendance session is closed",
            )
        }

        if (!isEnrolled(studentId,session,nativeActor)) {
            throw ApiException.Domain(
                status = HttpStatusCode.Forbidden,
                code = ApiErrorCode.ATTENDANCE_NOT_ENROLLED,
                message = "You are not enrolled in this class",
            )
        }

        // A pre-signed classroom QR can preserve evidence through an outage, but
        // it is never independent proof of campus presence or a VERIFIED check-in.
        val presence = schoolPresenceService
        val hasLiveCampusEvidence = if (presence == null) true else {
            val campusActive = presence.activeFor(studentId, nativeActor) != null
            campusActive && request.schoolNetwork?.let { network ->
                runCatching { presence.verifyNetworkForAttendance(network) }.isSuccess
            } == true
        }
        val offlineQrFallback = presence != null && !hasLiveCampusEvidence
        var offlineQrNonce: String? = null
        if (offlineQrFallback) {
            val signed = qrService?.verify(
                token = request.qrToken?.trim().orEmpty(),
                expectedSessionId = session.id,
                receivedAtEpochSeconds = now.epochSecond,
            )
            val capturedWithinQrWindow = when (signed) {
                is QrEvidenceResult.Valid ->
                    request.deviceTimestampEpochSeconds in signed.issuedAtEpochSeconds..signed.expiresAtEpochSeconds
                is QrEvidenceResult.Expired ->
                    request.deviceTimestampEpochSeconds in signed.issuedAtEpochSeconds..signed.expiresAtEpochSeconds
                else -> false
            }
            if (!capturedWithinQrWindow || !attemptWasInsideWindow ||
                now.epochSecond > effectiveClose + lateSyncReviewWindowSeconds
            ) {
                throw ApiException.Forbidden("Offline attendance requires a valid signed classroom QR captured during the session")
            }
            offlineQrNonce = when (signed) {
                is QrEvidenceResult.Valid -> signed.nonce
                is QrEvidenceResult.Expired -> signed.nonce
                else -> null
            }
        }
        val evidence = if (offlineQrFallback) {
            ClassifiedEvidence(
                AttendanceStatus.REVIEW_REQUIRED,
                AttendanceReasonCode.OFFLINE_NETWORK_QR_REVIEW,
                offlineQrNonce,
            )
        } else {
            classifyEvidence(
                request = request,
                sessionId = session.id,
                lateSyncEligible = lateSyncEligible,
                receivedAtEpochSeconds = now.epochSecond,
            )
        }
        val nonceUseCount = evidence.qrNonce?.let { nonce ->
            repository.recordQrNonceUse(
                sessionId = session.id,
                nonce = nonce,
                studentId = studentId,
                expiresAtEpochSeconds = effectiveClose + lateSyncReviewWindowSeconds,
                nowEpochSeconds = now.epochSecond,
            )
        }

        val candidate = AttendanceRecordResponse(
            id = session.id + ":" + studentId,
            operationId = operationId,
            sessionId = session.id,
            occurrenceId = session.occurrenceId,
            studentId = studentId,
            status = evidence.status,
            reasonCode = if (nonceUseCount != null && nonceUseCount > qrNonceReuseStudentThreshold) {
                AttendanceReasonCode.DUPLICATE
            } else evidence.reasonCode,
            attemptedAtEpochSeconds = request.deviceTimestampEpochSeconds,
            receivedAtEpochSeconds = now.epochSecond,
        )

        return when (val result = repository.writeAttempt(candidate)) {
            is AttemptWriteResult.Created -> result.record.withCurrentPresencePolicy()
            is AttemptWriteResult.Existing -> result.record.withCurrentPresencePolicy()
            AttemptWriteResult.OperationConflict ->
                throw ApiException.Domain(
                    status = HttpStatusCode.Conflict,
                    code = ApiErrorCode.ATTENDANCE_OPERATION_CONFLICT,
                    message = "operationId was already used for another attendance",
                )
        }
    }


    /**
     * Records a class call with a campus QR capture and current network declaration.
     * Neither declaration nor QR alone authenticates physical presence; an assigned
     * teacher must review. The server capture time is retained for that decision.
     */
    suspend fun confirmClassCall(
        studentId: String,
        sessionId: String,
        request: ClassCallConfirmationRequest,
        nativeActor:org.companerodeescuela.shared.contracts.UserSummary?=null,
    ): AttendanceRecordResponse {
        val operationId = request.operationId.trim()
        if (operationId.isBlank() || operationId.length > MAX_OPERATION_ID_LENGTH) {
            throw ApiException.Validation("operationId must be between 1 and 128 characters")
        }
        val presence = schoolPresenceService
            ?: throw ApiException.DependencyUnavailable("School presence verification is not configured")
        val session = repository.findSession(sessionId)
            ?: throw ApiException.NotFound("Attendance session was not found")
        val now = clock.instant().epochSecond
        if (session.status != AttendanceSessionStatus.OPEN ||
            now < session.openedAtEpochSeconds || now >= session.closesAtEpochSeconds
        ) {
            throw ApiException.Domain(
                status = HttpStatusCode.Conflict,
                code = ApiErrorCode.ATTENDANCE_SESSION_CLOSED,
                message = "Attendance session is not currently open",
            )
        }
        if (!isEnrolled(studentId,session,nativeActor)) {
            throw ApiException.Domain(
                status = HttpStatusCode.Forbidden,
                code = ApiErrorCode.ATTENDANCE_NOT_ENROLLED,
                message = "You are not enrolled in this class",
            )
        }
        presence.requireActive(studentId,nativeActor)
        presence.verifyNetworkForAttendance(request.schoolNetwork)
        val candidate = AttendanceRecordResponse(
            id = session.id + ":" + studentId,
            operationId = operationId,
            sessionId = session.id,
            occurrenceId = session.occurrenceId,
            studentId = studentId,
            status = AttendanceStatus.REVIEW_REQUIRED,
            reasonCode = AttendanceReasonCode.CLASS_CALL_CONFIRMED,
            attemptedAtEpochSeconds = now,
            receivedAtEpochSeconds = now,
        )
        return when (val result = repository.writeAttempt(candidate)) {
            is AttemptWriteResult.Created -> result.record.withCurrentPresencePolicy()
            is AttemptWriteResult.Existing -> result.record.withCurrentPresencePolicy()
            AttemptWriteResult.OperationConflict -> throw ApiException.Domain(
                status = HttpStatusCode.Conflict,
                code = ApiErrorCode.ATTENDANCE_OPERATION_CONFLICT,
                message = "operationId was already used for another attendance",
            )
        }
    }

    /** Existing canonical decision; only the enrolled student can read their own row. */
    suspend fun myClassRecord(studentId: String, sessionId: String,nativeActor:org.companerodeescuela.shared.contracts.UserSummary?=null): AttendanceRecordResponse? {
        val session = repository.findSession(sessionId)
            ?: throw ApiException.NotFound("Attendance session was not found")
        if (!isEnrolled(studentId,session,nativeActor)) {
            throw ApiException.Forbidden("You are not enrolled in this class")
        }
        return repository.findRecord("$sessionId:$studentId")?.withCurrentPresencePolicy()
    }


    private suspend fun isEnrolled(studentId:String,session:AttendanceSessionResponse,actor:org.companerodeescuela.shared.contracts.UserSummary?):Boolean {
        if(actor==null) {
            if(session.identitySource==org.companerodeescuela.shared.contracts.AttendanceIdentitySource.NATIVE || session.institutionId!=null || session.groupId!=null) return false
        } else {
            if(actor.id!=studentId) throw ApiException.Forbidden("Actual student identity does not match")
            try { (nativeProjection ?: throw ApiException.DependencyUnavailable("Native academic authority is unavailable")).requireSession(actor,session) }
            catch (_:ApiException.Forbidden) { return false }
            catch (_:ApiException.NotFound) { return false }
        }
        return enrollmentResolver.isEnrolled(studentId,session.courseId,session.groupName,actor)
    }

    private fun classifyEvidence(
        request: AttendanceAttemptRequest,
        nativeActor:org.companerodeescuela.shared.contracts.UserSummary?=null,
        sessionId: String,
        lateSyncEligible: Boolean,
        receivedAtEpochSeconds: Long,
    ): ClassifiedEvidence {
        val qrToken = request.qrToken?.trim()?.takeIf(String::isNotEmpty)
        if (qrToken == null) {
            return if (lateSyncEligible) {
                ClassifiedEvidence(AttendanceStatus.REVIEW_REQUIRED, AttendanceReasonCode.OFFLINE_LATE_SYNC)
            } else {
                ClassifiedEvidence(AttendanceStatus.LIKELY, AttendanceReasonCode.IDENTITY_SESSION_TIME)
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
                if (lateSyncEligible) {
                    if (request.deviceTimestampEpochSeconds in qr.issuedAtEpochSeconds..qr.expiresAtEpochSeconds) {
                        ClassifiedEvidence(AttendanceStatus.REVIEW_REQUIRED, AttendanceReasonCode.OFFLINE_LATE_SYNC, qr.nonce)
                    } else {
                        ClassifiedEvidence(AttendanceStatus.REJECTED, AttendanceReasonCode.QR_EXPIRED)
                    }
                } else {
                    ClassifiedEvidence(AttendanceStatus.REVIEW_REQUIRED, AttendanceReasonCode.QR_VALID, qr.nonce)
                }
            }
            is QrEvidenceResult.Expired -> {
                if (
                    lateSyncEligible &&
                    request.deviceTimestampEpochSeconds in
                    qr.issuedAtEpochSeconds..qr.expiresAtEpochSeconds
                ) {
                    ClassifiedEvidence(AttendanceStatus.REVIEW_REQUIRED, AttendanceReasonCode.OFFLINE_LATE_SYNC, qr.nonce)
                } else {
                    ClassifiedEvidence(AttendanceStatus.REJECTED, AttendanceReasonCode.QR_EXPIRED)
                }
            }
            QrEvidenceResult.WrongSession ->
                ClassifiedEvidence(AttendanceStatus.REJECTED, AttendanceReasonCode.WRONG_SESSION)
            QrEvidenceResult.Invalid ->
                ClassifiedEvidence(AttendanceStatus.REJECTED, AttendanceReasonCode.QR_INVALID)
        }
    }

    private data class ClassifiedEvidence(
        val status: AttendanceStatus,
        val reasonCode: AttendanceReasonCode,
        val qrNonce: String? = null,
    )

    private companion object {
        const val MAX_OPERATION_ID_LENGTH = 128
        const val MAX_QR_TOKEN_LENGTH = 2_048
    }
}
