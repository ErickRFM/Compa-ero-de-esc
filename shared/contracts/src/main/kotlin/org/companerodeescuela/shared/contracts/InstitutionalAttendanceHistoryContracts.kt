package org.companerodeescuela.shared.contracts

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

object InstitutionalAttendanceHistoryContract {
    const val REQUEST = "siii.attendance_history_request.v1"
    const val RESPONSE = "siii.attendance_history.v1"
    const val REQUEST_V2 = "siii.attendance_history_request.v2"
    const val RESPONSE_V2 = "siii.attendance_history.v2"
    const val ARCHIVE_REQUEST = "siii.attendance_archive_request.v1"
    const val ARCHIVE_RESPONSE = "siii.attendance_archive.v1"
    const val ARCHIVE_SCOPE = "attendance:archive"
    const val SCOPE = "attendance:read"
    const val SAMPLE_LIMIT = 100
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class InstitutionalAttendanceHistoryRequest(
    val contract: String,
    val studentExternalId: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val page: Int? = null,
)

/** Institution-scoped attendance only. No native identity, reviewer, note or device evidence leaves the API. */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
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
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val recordRevision: Long? = null,
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class InstitutionalAttendanceHistoryResponse(
    val contract: String,
    val observedAtEpochSeconds: Long,
    val sampleLimit: Int,
    val records: List<InstitutionalAttendanceHistoryRecord>,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val page: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val nextPage: Int? = null,
)
