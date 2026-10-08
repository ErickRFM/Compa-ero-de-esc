package org.companerodeescuela.feature.attendance

import org.companerodeescuela.core.database.LocalAttendanceRecord
import org.companerodeescuela.core.database.LocalAttendanceSyncState
import org.companerodeescuela.shared.contracts.AttendanceStatus
import org.companerodeescuela.shared.contracts.SchoolPresenceResponse
import org.companerodeescuela.shared.contracts.SchoolPresenceStatus

fun schoolDayVerified(presence: SchoolPresenceResponse?, nowEpochSeconds: Long): Boolean =
    presence != null && presence.status == SchoolPresenceStatus.ACTIVE &&
        presence.closedAtEpochSeconds == null && presence.qrVerified && presence.networkVerified &&
        nowEpochSeconds >= presence.startedAtEpochSeconds && nowEpochSeconds < presence.expiresAtEpochSeconds

fun schoolDayVerifiedAtServerTime(presence: SchoolPresenceResponse?, elapsedSeconds: Long): Boolean =
    presence?.serverTimeEpochSeconds?.let { serverTime ->
        elapsedSeconds >= 0 && schoolDayVerified(presence, serverTime + elapsedSeconds)
    } ?: false

enum class AttendanceClientVerdict {
    PENDING,
    AUTH_REQUIRED,
    SERVER_RECEIVED,
    SERVER_VERIFIED,
    REVIEW_REQUIRED,
    SERVER_REJECTED,
}

/**
 * Presentation-only policy.
 *
 * A scan can never become SERVER_VERIFIED from local state alone. Verification
 * is rendered only after the outbox row has reconciled as SYNCED with a
 * server-returned VERIFIED attendance status.
 */
fun attendanceClientVerdict(
    record: LocalAttendanceRecord,
): AttendanceClientVerdict = when (record.syncState) {
    LocalAttendanceSyncState.PENDING -> AttendanceClientVerdict.PENDING
    LocalAttendanceSyncState.AUTH_REQUIRED -> AttendanceClientVerdict.AUTH_REQUIRED
    LocalAttendanceSyncState.REVIEW_REQUIRED -> AttendanceClientVerdict.REVIEW_REQUIRED
    LocalAttendanceSyncState.REJECTED -> AttendanceClientVerdict.SERVER_REJECTED
    LocalAttendanceSyncState.SYNCED -> when (record.attendanceStatus) {
        AttendanceStatus.VERIFIED -> AttendanceClientVerdict.SERVER_VERIFIED
        AttendanceStatus.LIKELY,
        null,
        -> AttendanceClientVerdict.SERVER_RECEIVED
        AttendanceStatus.REVIEW_REQUIRED -> AttendanceClientVerdict.REVIEW_REQUIRED
        AttendanceStatus.REJECTED -> AttendanceClientVerdict.SERVER_REJECTED
    }
}
