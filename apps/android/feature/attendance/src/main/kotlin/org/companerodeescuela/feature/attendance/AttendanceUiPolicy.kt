package org.companerodeescuela.feature.attendance

import org.companerodeescuela.core.database.LocalAttendanceRecord
import org.companerodeescuela.core.database.LocalAttendanceSyncState
import org.companerodeescuela.shared.contracts.AttendanceStatus
import org.companerodeescuela.shared.contracts.SchoolPresenceResponse
import org.companerodeescuela.shared.contracts.SchoolPresenceStatus
import org.companerodeescuela.shared.contracts.AttendanceRecordResponse
import org.companerodeescuela.shared.contracts.AttendanceDisposition
import org.companerodeescuela.shared.validation.requiresAttendancePresenceReview

fun schoolDayRecorded(presence: SchoolPresenceResponse?, nowEpochSeconds: Long): Boolean =
    presence != null && presence.status == SchoolPresenceStatus.ACTIVE &&
        presence.closedAtEpochSeconds == null && presence.qrVerified &&
        nowEpochSeconds >= presence.startedAtEpochSeconds && nowEpochSeconds < presence.expiresAtEpochSeconds

fun schoolDayRecordedAtServerTime(presence: SchoolPresenceResponse?, elapsedSeconds: Long): Boolean =
    presence?.serverTimeEpochSeconds?.let { serverTime ->
        elapsedSeconds >= 0 && schoolDayRecorded(presence, serverTime + elapsedSeconds)
    } ?: false

fun attendanceRecordRequiresReview(record: AttendanceRecordResponse): Boolean =
    record.status == AttendanceStatus.REVIEW_REQUIRED && record.disposition == null ||
        requiresAttendancePresenceReview(
            record.status == AttendanceStatus.VERIFIED,
            record.disposition in setOf(AttendanceDisposition.PRESENT, AttendanceDisposition.LATE),
            record.reviewedBy, record.reviewedAtEpochSeconds,
        )

fun attendanceRecordsRequiringReviewCount(records: List<AttendanceRecordResponse>): Int =
    records.count(::attendanceRecordRequiresReview)

fun classCallRecordMessage(record: AttendanceRecordResponse): String =
    if (attendanceRecordRequiresReview(record)) "Registro recibido · requiere revisión docente" else when (record.disposition) {
        AttendanceDisposition.PRESENT -> "Asistencia confirmada"
        AttendanceDisposition.LATE -> "Retardo confirmado"
        AttendanceDisposition.ABSENT -> "Ausencia registrada"
        null -> when (record.status) {
            AttendanceStatus.VERIFIED -> "Evidencia validada por docente"
            AttendanceStatus.REJECTED -> "Registro rechazado"
            AttendanceStatus.LIKELY, AttendanceStatus.REVIEW_REQUIRED -> "Registro recibido · requiere revisión docente"
        }
    }

enum class AttendanceClientVerdict {
    PENDING,
    AUTH_REQUIRED,
    SERVER_RECEIVED,
    REVIEW_REQUIRED,
    SERVER_REJECTED,
}

/**
 * Presentation-only policy.
 *
 * An outbox status does not retain a human decision or independent witness.
 * Even historical VERIFIED rows indicate reconciliation only. The current
 * class record, fetched separately, carries the authoritative disposition.
 */
fun attendanceClientVerdict(
    record: LocalAttendanceRecord,
): AttendanceClientVerdict = when (record.syncState) {
    LocalAttendanceSyncState.PENDING -> AttendanceClientVerdict.PENDING
    LocalAttendanceSyncState.AUTH_REQUIRED -> AttendanceClientVerdict.AUTH_REQUIRED
    LocalAttendanceSyncState.REVIEW_REQUIRED -> AttendanceClientVerdict.REVIEW_REQUIRED
    LocalAttendanceSyncState.REJECTED -> AttendanceClientVerdict.SERVER_REJECTED
    LocalAttendanceSyncState.SYNCED -> when (record.attendanceStatus) {
        AttendanceStatus.VERIFIED -> AttendanceClientVerdict.SERVER_RECEIVED
        AttendanceStatus.LIKELY,
        null,
        -> AttendanceClientVerdict.SERVER_RECEIVED
        AttendanceStatus.REVIEW_REQUIRED -> AttendanceClientVerdict.REVIEW_REQUIRED
        AttendanceStatus.REJECTED -> AttendanceClientVerdict.SERVER_REJECTED
    }
}
