package org.companerodeescuela.api.attendance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.shared.contracts.AttendanceReasonCode
import org.companerodeescuela.shared.contracts.AttendanceRecordResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionStatus
import org.companerodeescuela.shared.contracts.AttendanceStatus

class AttendanceRepositoryTest {

    @Test
    fun `one occurrence maps to one attendance session`() = runTest {
        val repository = InMemoryAttendanceRepository()
        val first = session("s-1")
        val second = session("s-2")

        assertIs<SessionWriteResult.Created>(repository.createSession(first))
        val duplicate = assertIs<SessionWriteResult.Existing>(repository.createSession(second))

        assertEquals(first, duplicate.session)
    }

    @Test
    fun `operation id cannot be reused for another attendance record`() = runTest {
        val repository = InMemoryAttendanceRepository()
        val first = record("session-1:student-1", "session-1", "student-1", "op-1")
        val conflicting = record("session-2:student-2", "session-2", "student-2", "op-1")

        assertIs<AttemptWriteResult.Created>(repository.writeAttempt(first))
        assertIs<AttemptWriteResult.OperationConflict>(repository.writeAttempt(conflicting))
    }

    @Test
    fun `same student and session cannot create two records with different operations`() = runTest {
        val repository = InMemoryAttendanceRepository()
        val first = record("session-1:student-1", "session-1", "student-1", "op-1")
        val retry = record("session-1:student-1", "session-1", "student-1", "op-2")

        assertIs<AttemptWriteResult.Created>(repository.writeAttempt(first))
        val existing = assertIs<AttemptWriteResult.Existing>(repository.writeAttempt(retry))

        assertEquals(first.id, existing.record.id)
        assertEquals(1, repository.recordsForSession("session-1").size)
    }

    private fun session(id: String) = AttendanceSessionResponse(
        id = id,
        occurrenceId = "occ-1",
        courseId = "C-1",
        groupName = "A",
        occurrenceDate = "2026-10-05",
        scheduledStartsAt = "07:00",
        scheduledEndsAt = "08:30",
        openedBy = "teacher-1",
        openedAtEpochSeconds = 1,
        closesAtEpochSeconds = 300,
        status = AttendanceSessionStatus.OPEN,
    )

    private fun record(
        id: String,
        sessionId: String,
        studentId: String,
        operationId: String,
    ) = AttendanceRecordResponse(
        id = id,
        operationId = operationId,
        sessionId = sessionId,
        occurrenceId = "occ-1",
        studentId = studentId,
        status = AttendanceStatus.LIKELY,
        reasonCode = AttendanceReasonCode.IDENTITY_SESSION_TIME,
        attemptedAtEpochSeconds = 1,
        receivedAtEpochSeconds = 1,
    )
}
