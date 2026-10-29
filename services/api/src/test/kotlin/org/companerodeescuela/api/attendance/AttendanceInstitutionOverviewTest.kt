package org.companerodeescuela.api.attendance

import kotlinx.coroutines.test.runTest
import org.companerodeescuela.shared.contracts.AttendanceDisposition
import org.companerodeescuela.shared.contracts.AttendanceIdentitySource
import org.companerodeescuela.shared.contracts.AttendanceReasonCode
import org.companerodeescuela.shared.contracts.AttendanceRecordResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionStatus
import org.companerodeescuela.shared.contracts.AttendanceStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttendanceInstitutionOverviewTest {
    @Test
    fun `institution counts exclude foreign expired and closed passes but retain closed review work`() =
        runTest {
            val repository = InMemoryAttendanceRepository()
            repository.createSession(session("open"))
            repository.createSession(session("expired").copy(closesAtEpochSeconds = 100))
            repository.createSession(session("closed").copy(status = AttendanceSessionStatus.CLOSED, closedAtEpochSeconds = 90))
            repository.createSession(session("foreign").copy(institutionId = "other"))
            repository.createSession(session("unscoped").copy(identitySource = AttendanceIdentitySource.INSTITUTIONAL))
            repository.writeAttempt(record("pending", "open"))
            repository.writeAttempt(record("closed-pending", "closed"))
            repository.writeAttempt(record("expired-pending", "expired"))
            repository.writeAttempt(record("foreign-pending", "foreign"))
            repository.writeAttempt(record("unscoped-pending", "unscoped"))
            repository.writeAttempt(record("legacy-verified", "open").copy(studentId = "legacy", status = AttendanceStatus.VERIFIED))
            repository.writeAttempt(
                record("decided", "open").copy(
                    studentId = "decided",
                    status = AttendanceStatus.VERIFIED,
                    disposition = AttendanceDisposition.PRESENT,
                    reviewedBy = "teacher",
                    reviewedAtEpochSeconds = 99,
                ),
            )

            val result = repository.institutionOverview("campus", 100)
            assertEquals(1, result.openPasses)
            assertEquals(4L, result.pendingReviews)
            assertFalse(result.sampleLimited)
        }

    @Test
    fun `counts explicitly disclose bounded recent-session samples`() =
        runTest {
            val repository = InMemoryAttendanceRepository()
            for (index in 0..2) repository.createSession(session("session-$index").copy(openedAtEpochSeconds = index.toLong()))
            repository.writeAttempt(record("old-pending", "session-0"))
            val result = repository.institutionOverview("campus", 100, sessionLimit = 2)
            assertEquals(2, result.openPasses)
            assertEquals(0L, result.pendingReviews)
            assertEquals(2, result.sessionSampleLimit)
            assertTrue(result.sampleLimited)
        }

    private fun session(id: String) =
        AttendanceSessionResponse(
            id,
            "occurrence-$id",
            "course",
            "9A",
            "2026-10-28",
            "07:00",
            "08:00",
            "teacher",
            1,
            200,
            status = AttendanceSessionStatus.OPEN,
            identitySource = AttendanceIdentitySource.NATIVE,
            institutionId = "campus",
            groupId = "group",
            groupScopeRevision = 1,
        )

    private fun record(
        id: String,
        sessionId: String,
    ) = AttendanceRecordResponse(
        id,
        "operation-$id",
        sessionId,
        "occurrence-$sessionId",
        "student",
        AttendanceStatus.REVIEW_REQUIRED,
        AttendanceReasonCode.CLASS_CALL_CONFIRMED,
        50,
        50,
    )
}
