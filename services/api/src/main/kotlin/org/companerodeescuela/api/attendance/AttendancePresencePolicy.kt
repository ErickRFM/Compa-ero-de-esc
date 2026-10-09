package org.companerodeescuela.api.attendance

import org.companerodeescuela.shared.contracts.AttendanceDisposition
import org.companerodeescuela.shared.contracts.AttendanceRecordResponse
import org.companerodeescuela.shared.contracts.AttendanceStatus
import org.companerodeescuela.shared.validation.requiresAttendancePresenceReview

/**
 * No independently authenticated campus witness is implemented yet. Historical
 * automatic QR/Wi-Fi decisions therefore remain evidence for human review.
 * This is a read projection; stored evidence is not rewritten or fabricated.
 */
internal fun AttendanceRecordResponse.withCurrentPresencePolicy(): AttendanceRecordResponse {
    if (!requiresAttendancePresenceReview(
            status == AttendanceStatus.VERIFIED,
            disposition in setOf(AttendanceDisposition.PRESENT, AttendanceDisposition.LATE),
            reviewedBy, reviewedAtEpochSeconds,
        )) return this
    return copy(
        status = AttendanceStatus.REVIEW_REQUIRED,
        disposition = null,
        originalStatus = originalStatus ?: status,
        originalReasonCode = originalReasonCode ?: reasonCode,
    )
}
