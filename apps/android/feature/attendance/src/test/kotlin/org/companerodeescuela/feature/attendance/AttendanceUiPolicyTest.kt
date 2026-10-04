package org.companerodeescuela.feature.attendance

import kotlin.test.Test
import kotlin.test.assertEquals
import org.companerodeescuela.core.database.LocalAttendanceRecord
import org.companerodeescuela.core.database.LocalAttendanceSyncState
import org.companerodeescuela.shared.contracts.AttendanceReasonCode
import org.companerodeescuela.shared.contracts.AttendanceStatus

class AttendanceUiPolicyTest {

    @Test
    fun `pending local scan can never present as verified`() {
        val record = local(
            syncState = LocalAttendanceSyncState.PENDING,
            attendanceStatus = AttendanceStatus.VERIFIED,
        )

        assertEquals(
            AttendanceClientVerdict.PENDING,
            attendanceClientVerdict(record),
        )
    }

    @Test
    fun `verified label requires synced server status`() {
        val record = local(
            syncState = LocalAttendanceSyncState.SYNCED,
            attendanceStatus = AttendanceStatus.VERIFIED,
        )

        assertEquals(
            AttendanceClientVerdict.SERVER_VERIFIED,
            attendanceClientVerdict(record),
        )
    }

    @Test
    fun `server review and rejection remain explicit`() {
        assertEquals(
            AttendanceClientVerdict.REVIEW_REQUIRED,
            attendanceClientVerdict(
                local(
                    syncState = LocalAttendanceSyncState.REVIEW_REQUIRED,
                    attendanceStatus = AttendanceStatus.REVIEW_REQUIRED,
                ),
            ),
        )
        assertEquals(
            AttendanceClientVerdict.SERVER_REJECTED,
            attendanceClientVerdict(
                local(
                    syncState = LocalAttendanceSyncState.REJECTED,
                    attendanceStatus = AttendanceStatus.REJECTED,
                ),
            ),
        )
    }

    private fun local(
        syncState: LocalAttendanceSyncState,
        attendanceStatus: AttendanceStatus?,
    ) = LocalAttendanceRecord(
        operationId = "op-1",
        ownerId = "student-1",
        sessionId = "session-1",
        syncState = syncState,
        attendanceStatus = attendanceStatus,
        reasonCode = AttendanceReasonCode.IDENTITY_SESSION_TIME,
        updatedAtEpochSeconds = 1,
    )
}
