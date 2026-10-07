package org.companerodeescuela.api.attendance

import java.time.Clock
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.AttendanceRecordResponse
import org.companerodeescuela.shared.contracts.ReviewAttendanceRequest

class AttendanceReviewService(
    private val repository: AttendanceRepository,
    private val accessPolicy: AttendanceAccessPolicy = AttendanceAccessPolicy(repository),
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun review(
        reviewerId: String,
        recordId: String,
        request: ReviewAttendanceRequest,
        allowCrossOwner: Boolean = false,
    ): AttendanceRecordResponse {
        val record = repository.findRecord(recordId)
            ?: throw ApiException.NotFound("Attendance record was not found")
        val session = accessPolicy.requireSession(record.sessionId)
        accessPolicy.requireOwnerOrAdministrative(reviewerId, session, allowCrossOwner)

        val reviewed = record.copy(
            status = if (request.disposition == null) request.status else record.status,
            reasonCode = if (request.disposition == null) request.reasonCode else record.reasonCode,
            disposition = request.disposition ?: record.disposition,
            reviewedBy = reviewerId,
            reviewedAtEpochSeconds = clock.instant().epochSecond,
        )
        repository.replaceRecord(reviewed)
        return reviewed
    }
}
