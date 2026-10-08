package org.companerodeescuela.shared.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AttendanceStatus {
    @SerialName("verified")
    VERIFIED,

    @SerialName("likely")
    LIKELY,

    @SerialName("review_required")
    REVIEW_REQUIRED,

    @SerialName("rejected")
    REJECTED,
}

@Serializable
enum class AttendanceDisposition {
    @SerialName("present")
    PRESENT,

    @SerialName("late")
    LATE,

    @SerialName("absent")
    ABSENT,
}

@Serializable
enum class AttendanceSessionStatus {
    @SerialName("open")
    OPEN,

    @SerialName("closed")
    CLOSED,
}

@Serializable
enum class AttendanceReasonCode {
    @SerialName("identity_session_time")
    IDENTITY_SESSION_TIME,

    @SerialName("duplicate")
    DUPLICATE,

    @SerialName("offline_late_sync")
    OFFLINE_LATE_SYNC,

    @SerialName("not_enrolled")
    NOT_ENROLLED,

    @SerialName("outside_allowed_window")
    OUTSIDE_ALLOWED_WINDOW,

    @SerialName("teacher_review")
    TEACHER_REVIEW,

    @SerialName("session_closed")
    SESSION_CLOSED,

    @SerialName("wrong_session")
    WRONG_SESSION,

    @SerialName("qr_valid")
    QR_VALID,

    @SerialName("qr_expired")
    QR_EXPIRED,

    @SerialName("qr_invalid")
    QR_INVALID,

    @SerialName("class_call_confirmed")
    CLASS_CALL_CONFIRMED,
}

@Serializable
data class CreateAttendanceSessionRequest(
    val occurrenceId: String,
    val occurrenceDate: String,
    val durationMinutes: Int = 6,
)

@Serializable
data class AttendanceSessionResponse(
    val id: String,
    val occurrenceId: String,
    val courseId: String,
    val groupName: String,
    val occurrenceDate: String,
    val scheduledStartsAt: String,
    val scheduledEndsAt: String,
    val openedBy: String,
    val openedAtEpochSeconds: Long,
    val closesAtEpochSeconds: Long,
    val closedAtEpochSeconds: Long? = null,
    val status: AttendanceSessionStatus,
)

@Serializable
data class AttendanceAttemptRequest(
    val operationId: String,
    val deviceTimestampEpochSeconds: Long,
    val qrToken: String? = null,
    val schoolNetwork: SchoolNetworkEvidence? = null,
)

@Serializable
data class AttendanceQrResponse(
    val token: String,
    val issuedAtEpochSeconds: Long,
    val expiresAtEpochSeconds: Long,
    val rotateAfterSeconds: Long,
)

@Serializable
enum class AttendanceQrInspectionStatus {
    @SerialName("valid")
    VALID,

    @SerialName("expired")
    EXPIRED,

    @SerialName("wrong_session")
    WRONG_SESSION,

    @SerialName("invalid")
    INVALID,

    @SerialName("session_closed")
    SESSION_CLOSED,

    @SerialName("not_enrolled")
    NOT_ENROLLED,
}

@Serializable
data class AttendanceQrInspectionRequest(
    val sessionId: String,
    val token: String,
)

@Serializable
data class AttendanceQrInspectionResponse(
    val status: AttendanceQrInspectionStatus,
    val session: AttendanceSessionResponse? = null,
    val expiresAtEpochSeconds: Long? = null,
)

@Serializable
data class AttendanceRecordResponse(
    val id: String,
    val operationId: String,
    val sessionId: String,
    val occurrenceId: String,
    val studentId: String,
    val status: AttendanceStatus,
    val reasonCode: AttendanceReasonCode,
    val attemptedAtEpochSeconds: Long,
    val receivedAtEpochSeconds: Long,
    val reviewedBy: String? = null,
    val reviewedAtEpochSeconds: Long? = null,
    val disposition: AttendanceDisposition? = null,
    val originalStatus: AttendanceStatus? = null,
    val originalReasonCode: AttendanceReasonCode? = null,
    val reviewHistory: List<AttendanceReviewEntry> = emptyList(),
)

@Serializable
data class AttendanceReviewEntry(
    val reviewerId: String,
    val reviewedAtEpochSeconds: Long,
    val note: String,
    val status: AttendanceStatus,
    val disposition: AttendanceDisposition? = null,
)

@Serializable
data class AttendanceRosterResponse(
    val session: AttendanceSessionResponse,
    val records: List<AttendanceRecordResponse>,
)

@Serializable
data class ReviewAttendanceRequest(
    val status: AttendanceStatus,
    val reasonCode: AttendanceReasonCode = AttendanceReasonCode.TEACHER_REVIEW,
    val disposition: AttendanceDisposition? = null,
    val note: String? = null,
)

/** A class check-in must be verified server-side; receiving a notification does not count. */
@Serializable
data class ClassCallConfirmationRequest(
    val operationId: String,
    val schoolNetwork: SchoolNetworkEvidence,
)
