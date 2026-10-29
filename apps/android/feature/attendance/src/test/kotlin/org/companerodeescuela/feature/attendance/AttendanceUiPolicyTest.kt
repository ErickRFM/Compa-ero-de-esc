package org.companerodeescuela.feature.attendance

import org.companerodeescuela.core.database.LocalAttendanceRecord
import org.companerodeescuela.core.database.LocalAttendanceSyncState
import org.companerodeescuela.shared.contracts.AttendanceReasonCode
import org.companerodeescuela.shared.contracts.AttendanceStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttendanceUiPolicyTest {
    @Test fun `student history excludes active cards and duplicate sessions`() {
        val record =
            org.companerodeescuela.shared.contracts.AttendanceRecordResponse(
                "record",
                "op",
                "closed",
                "occ",
                "student",
                AttendanceStatus.REVIEW_REQUIRED,
                AttendanceReasonCode.CLASS_CALL_CONFIRMED,
                1,
                1,
            )
        val records = listOf(record, record.copy(id = "old-duplicate"), record.copy(id = "active-record", sessionId = "active"))
        assertEquals(listOf("record"), studentHistoricalRecords(records, setOf("active")).map { it.id })
    }

    @Test
    fun `school day capture requires current server lifetime regardless of network declaration`() {
        assertFalse(schoolDayRecorded(null, 100))
        val presence =
            org.companerodeescuela.shared.contracts.SchoolPresenceResponse(
                id = "presence",
                studentId = "student",
                startedAtEpochSeconds = 50,
                expiresAtEpochSeconds = 200,
                status = org.companerodeescuela.shared.contracts.SchoolPresenceStatus.ACTIVE,
                qrVerified = true,
                networkVerified = true,
                networkVerificationMethod = org.companerodeescuela.shared.contracts.NetworkVerificationMethod.SSID,
            )
        assertTrue(schoolDayRecorded(presence, 100))
        assertFalse(schoolDayRecorded(presence, 200))
        assertFalse(schoolDayRecorded(presence, 49))
        assertFalse(schoolDayRecorded(presence.copy(closedAtEpochSeconds = 90), 100))
        assertFalse(schoolDayRecorded(presence.copy(qrVerified = false), 100))
        assertTrue(schoolDayRecorded(presence.copy(networkVerified = false), 100))
        assertFalse(schoolDayRecordedAtServerTime(presence, 0))
        val timestamped = presence.copy(serverTimeEpochSeconds = 100)
        assertTrue(schoolDayRecordedAtServerTime(timestamped, 99))
        assertFalse(schoolDayRecordedAtServerTime(timestamped, 100))
        assertFalse(schoolDayRecordedAtServerTime(timestamped, -1))
    }

    @Test
    fun `pending local scan can never present as verified`() {
        val record =
            local(
                syncState = LocalAttendanceSyncState.PENDING,
                attendanceStatus = AttendanceStatus.VERIFIED,
            )

        assertEquals(
            AttendanceClientVerdict.PENDING,
            attendanceClientVerdict(record),
        )
    }

    @Test
    fun `synced verified cache indicates reception without a human decision`() {
        val record =
            local(
                syncState = LocalAttendanceSyncState.SYNCED,
                attendanceStatus = AttendanceStatus.VERIFIED,
            )

        assertEquals(
            AttendanceClientVerdict.SERVER_RECEIVED,
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

    @Test fun `recorded school day permits evidence capture while independent verification is absent`() {
        val presence =
            org.companerodeescuela.shared.contracts.SchoolPresenceResponse(
                "presence",
                "student",
                50,
                200,
                status = org.companerodeescuela.shared.contracts.SchoolPresenceStatus.ACTIVE,
                qrVerified = true,
                networkVerified = false,
                networkVerificationMethod = org.companerodeescuela.shared.contracts.NetworkVerificationMethod.SSID,
            )
        assertTrue(schoolDayRecorded(presence, 100))
        assertFalse(schoolDayRecorded(presence, 200))
        assertFalse(schoolDayRecorded(presence.copy(closedAtEpochSeconds = 90), 100))
    }

    @Test fun `pending class call never displays confirmed attendance`() {
        val record =
            org.companerodeescuela.shared.contracts.AttendanceRecordResponse(
                "record",
                "op",
                "session",
                "occ",
                "student",
                AttendanceStatus.REVIEW_REQUIRED,
                AttendanceReasonCode.CLASS_CALL_CONFIRMED,
                1,
                1,
            )
        assertEquals("Registro recibido · requiere revisión docente", classCallRecordMessage(record))
        assertEquals(
            "Asistencia confirmada",
            classCallRecordMessage(
                record.copy(
                    disposition = org.companerodeescuela.shared.contracts.AttendanceDisposition.PRESENT,
                    reviewedBy = "teacher",
                    reviewedAtEpochSeconds = 1,
                ),
            ),
        )
    }

    @Test fun `legacy automatic positive disposition is never displayed as confirmed`() {
        val legacy =
            org.companerodeescuela.shared.contracts.AttendanceRecordResponse(
                "record",
                "op",
                "session",
                "occ",
                "student",
                AttendanceStatus.VERIFIED,
                AttendanceReasonCode.CLASS_CALL_CONFIRMED,
                1,
                1,
                disposition = org.companerodeescuela.shared.contracts.AttendanceDisposition.PRESENT,
            )
        assertEquals("Registro recibido · requiere revisión docente", classCallRecordMessage(legacy))
    }

    @Test fun `dashboard counts pending and legacy rows but excludes completed human decisions`() {
        val pending =
            org.companerodeescuela.shared.contracts.AttendanceRecordResponse(
                "record",
                "op",
                "session",
                "occ",
                "student",
                AttendanceStatus.REVIEW_REQUIRED,
                AttendanceReasonCode.CLASS_CALL_CONFIRMED,
                1,
                1,
            )
        val reviewed =
            org.companerodeescuela.shared.contracts.AttendanceDisposition.entries.map { disposition ->
                pending.copy(disposition = disposition, reviewedBy = "teacher", reviewedAtEpochSeconds = 1)
            }
        val legacy =
            pending.copy(
                status = AttendanceStatus.VERIFIED,
                disposition = org.companerodeescuela.shared.contracts.AttendanceDisposition.PRESENT,
            )
        assertEquals(0, attendanceRecordsRequiringReviewCount(reviewed))
        assertEquals(2, attendanceRecordsRequiringReviewCount(listOf(pending, legacy) + reviewed))
    }

    @Test fun `human exception validation without a disposition communicates completed evidence review`() {
        val validated =
            org.companerodeescuela.shared.contracts.AttendanceRecordResponse(
                "record",
                "op",
                "session",
                "occ",
                "student",
                AttendanceStatus.VERIFIED,
                AttendanceReasonCode.TEACHER_REVIEW,
                1,
                1,
                reviewedBy = "teacher",
                reviewedAtEpochSeconds = 1,
            )
        assertFalse(attendanceRecordRequiresReview(validated))
        assertEquals("Evidencia validada por docente", classCallRecordMessage(validated))
    }

    @Test fun `rejected class evidence communicates rejection without implying pending review`() {
        val rejected =
            org.companerodeescuela.shared.contracts.AttendanceRecordResponse(
                "record",
                "op",
                "session",
                "occ",
                "student",
                AttendanceStatus.REJECTED,
                AttendanceReasonCode.QR_INVALID,
                1,
                1,
            )
        assertFalse(attendanceRecordRequiresReview(rejected))
        assertEquals("Registro rechazado", classCallRecordMessage(rejected))
    }

    @Test fun `history filters require teacher decisions rather than automatic optimistic states`() {
        val pending =
            org.companerodeescuela.shared.contracts.AttendanceRecordResponse(
                "pending",
                "op",
                "session-a",
                "occ",
                "student",
                AttendanceStatus.REVIEW_REQUIRED,
                AttendanceReasonCode.CLASS_CALL_CONFIRMED,
                1,
                1,
            )
        val present =
            pending.copy(
                id = "present",
                sessionId = "session-b",
                disposition = org.companerodeescuela.shared.contracts.AttendanceDisposition.PRESENT,
                reviewedBy = "teacher",
                reviewedAtEpochSeconds = 2,
            )
        val late =
            present.copy(
                id = "late",
                sessionId = "session-c",
                disposition = org.companerodeescuela.shared.contracts.AttendanceDisposition.LATE,
            )
        val absent =
            present.copy(
                id = "absent",
                sessionId = "session-d",
                disposition = org.companerodeescuela.shared.contracts.AttendanceDisposition.ABSENT,
            )
        val legacy =
            pending.copy(
                id = "legacy",
                sessionId = "session-e",
                status = AttendanceStatus.VERIFIED,
                disposition = org.companerodeescuela.shared.contracts.AttendanceDisposition.PRESENT,
            )
        val records = listOf(pending, present, late, absent, legacy)
        assertEquals(5, filterStudentHistory(records, StudentHistoryFilter.ALL).size)
        assertEquals(listOf("present"), filterStudentHistory(records, StudentHistoryFilter.PRESENT).map { it.id })
        assertEquals(listOf("late"), filterStudentHistory(records, StudentHistoryFilter.LATE).map { it.id })
        assertEquals(listOf("absent"), filterStudentHistory(records, StudentHistoryFilter.ABSENT).map { it.id })
        assertEquals(listOf("pending", "legacy"), filterStudentHistory(records, StudentHistoryFilter.PENDING).map { it.id })
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
