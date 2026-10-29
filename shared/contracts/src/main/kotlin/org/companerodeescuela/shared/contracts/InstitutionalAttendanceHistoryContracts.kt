package org.companerodeescuela.shared.contracts

import kotlinx.serialization.Serializable

object InstitutionalAttendanceHistoryContract {
    const val REQUEST = "siii.attendance_history_request.v1"
    const val RESPONSE = "siii.attendance_history.v1"
    const val SCOPE = "attendance:read"
    const val SAMPLE_LIMIT = 100
}

@Serializable
data class InstitutionalAttendanceHistoryRequest(
    val contract: String,
    val studentExternalId: String,
)

/** Institution-scoped attendance only. No native identity, reviewer, note or device evidence leaves the API. */
@Serializable
data class InstitutionalAttendanceHistoryRecord(
    val auditRecordId: String,
    val sessionId: String,
    val sectionId: String,
    val occurrenceId: String,
    val sourceVersion: Long,
    val occurrenceDate: String,
    val status: AttendanceStatus,
    val disposition: AttendanceDisposition?,
    val reasonCode: AttendanceReasonCode,
    val receivedAtEpochSeconds: Long,
    val reviewedAtEpochSeconds: Long?,
)

@Serializable
data class InstitutionalAttendanceHistoryResponse(
    val contract: String,
    val observedAtEpochSeconds: Long,
    val sampleLimit: Int,
    val records: List<InstitutionalAttendanceHistoryRecord>,
)
