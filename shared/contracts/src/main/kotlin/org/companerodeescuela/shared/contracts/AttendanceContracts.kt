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
enum class AttendanceSessionStatus {
    @SerialName("open")
    OPEN,

    @SerialName("closed")
    CLOSED,
}

@Serializable
data class CreateAttendanceSessionRequest(
    val courseId: String,
    val groupName: String,
    val durationMinutes: Int = 6,
)

@Serializable
data class AttendanceSessionResponse(
    val id: String,
    val courseId: String,
    val groupName: String,
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
)

@Serializable
data class AttendanceRecordResponse(
    val id: String,
    val operationId: String,
    val sessionId: String,
    val studentId: String,
    val status: AttendanceStatus,
    val reasonCode: String,
    val attemptedAtEpochSeconds: Long,
    val receivedAtEpochSeconds: Long,
    val reviewedBy: String? = null,
    val reviewedAtEpochSeconds: Long? = null,
)

@Serializable
data class AttendanceRosterResponse(
    val session: AttendanceSessionResponse,
    val records: List<AttendanceRecordResponse>,
)

@Serializable
data class ReviewAttendanceRequest(
    val status: AttendanceStatus,
    val reasonCode: String,
)
