package org.companerodeescuela.shared.validation

/**
 * Source-shape guard shared by server projections and client presentation.
 * It grants no authority: review identity/resource authorization belongs to API.
 * No independent automatic campus witness is implemented in this version.
 */
fun requiresAttendancePresenceReview(
    evidenceVerified: Boolean,
    hasPositiveDisposition: Boolean,
    reviewerId: String?,
    reviewedAtEpochSeconds: Long?,
): Boolean = (evidenceVerified || hasPositiveDisposition) &&
    (reviewerId.isNullOrBlank() || reviewedAtEpochSeconds == null || reviewedAtEpochSeconds <= 0)
