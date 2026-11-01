package org.companerodeescuela.api.attendance

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.AttendanceRecordResponse
import org.companerodeescuela.shared.contracts.AttendanceReviewEntry
import org.companerodeescuela.shared.contracts.ReviewAttendanceRequest
import java.time.Clock

class AttendanceReviewService(
    private val repository: AttendanceRepository,
    private val accessPolicy: AttendanceAccessPolicy = AttendanceAccessPolicy(repository),
    private val clock: Clock = Clock.systemUTC(),
) {
    private val reviewMutex = Mutex()

    suspend fun review(
        reviewerId: String,
        recordId: String,
        request: ReviewAttendanceRequest,
        allowCrossOwner: Boolean = false,
        nativeActor: org.companerodeescuela.shared.contracts.UserSummary? = null,
    ): AttendanceRecordResponse =
        reviewMutex.withLock {
            val record =
                repository
                    .findRecord(recordId)
                    ?.withCurrentPresencePolicy()
                    ?: throw ApiException.NotFound("Attendance record was not found")
            val session = accessPolicy.requireSession(record.sessionId)
            accessPolicy.requireOwnerOrAdministrative(reviewerId, session, allowCrossOwner, nativeActor)
            val note =
                request.note?.trim() ?: request.reasonCode.name
                    .lowercase()
                    .replace('_', ' ')
            if (note.length !in 3..500) throw ApiException.Validation("A review reason between 3 and 500 characters is required")

            val reviewed =
                record.copy(
                    recordRevision = record.recordRevision + 1,
                    status = if (request.disposition == null) request.status else record.status,
                    reasonCode = if (request.disposition == null) request.reasonCode else record.reasonCode,
                    disposition = request.disposition ?: record.disposition,
                    reviewedBy = reviewerId,
                    reviewedAtEpochSeconds = clock.instant().epochSecond,
                    originalStatus = record.originalStatus ?: record.status,
                    originalReasonCode = record.originalReasonCode ?: record.reasonCode,
                    reviewHistory =
                        record.reviewHistory +
                            AttendanceReviewEntry(
                                reviewerId,
                                clock.instant().epochSecond,
                                note,
                                if (request.disposition == null) request.status else record.status,
                                request.disposition ?: record.disposition,
                            ),
                )
            if (!repository.compareAndSetRecord(reviewed, record.recordRevision)) {
                throw ApiException.Conflict("Attendance changed during review; reload before submitting another decision")
            }
            reviewed
        }
}
