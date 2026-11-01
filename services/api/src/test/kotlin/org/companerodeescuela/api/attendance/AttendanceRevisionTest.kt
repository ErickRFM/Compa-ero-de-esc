package org.companerodeescuela.api.attendance

import kotlinx.coroutines.test.runTest
import org.companerodeescuela.shared.contracts.AttendanceReasonCode
import org.companerodeescuela.shared.contracts.AttendanceRecordResponse
import org.companerodeescuela.shared.contracts.AttendanceStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttendanceRevisionTest {
    @Test
    fun `full scans include old corrected records beyond the first hundred without leaking another student`() =
        runTest {
            val repository = InMemoryAttendanceRepository()
            for (index in 1..105) {
                repository.writeAttempt(
                    AttendanceRecordResponse(
                        "record-${index.toString().padStart(3, '0')}",
                        "op-$index",
                        "session-$index",
                        "occurrence-$index",
                        "student",
                        AttendanceStatus.REVIEW_REQUIRED,
                        AttendanceReasonCode.CLASS_CALL_CONFIRMED,
                        10,
                        10,
                    ),
                )
            }
            val first = repository.studentHistoryPage("student", 1)
            val second = repository.studentHistoryPage("student", 2)
            assertEquals(101, first.size)
            assertEquals(5, second.size)
            assertEquals("record-101", second.first().id)
            val old = second.last()
            assertTrue(repository.compareAndSetRecord(old.copy(recordRevision = 2), 1))
            assertEquals(2, repository.studentHistoryPage("student", 2).last().recordRevision)
            assertTrue(repository.studentHistoryPage("foreign-student", 1).isEmpty())
        }

    @Test
    fun `stale writer cannot replace a newer decision even in the same second`() =
        runTest {
            val repository = InMemoryAttendanceRepository()
            val original =
                AttendanceRecordResponse(
                    "record",
                    "operation",
                    "session",
                    "occurrence",
                    "student",
                    AttendanceStatus.REVIEW_REQUIRED,
                    AttendanceReasonCode.CLASS_CALL_CONFIRMED,
                    10,
                    10,
                )
            repository.writeAttempt(original)
            val next = original.copy(recordRevision = 2, status = AttendanceStatus.VERIFIED)
            assertTrue(repository.compareAndSetRecord(next, 1))
            assertFalse(repository.compareAndSetRecord(next.copy(status = AttendanceStatus.REJECTED), 1))
            assertEquals(next, repository.findRecord(original.id))
        }

    @Test
    fun `replacing rejected evidence preserves monotonic revision`() =
        runTest {
            val repository = InMemoryAttendanceRepository()
            val rejected =
                AttendanceRecordResponse(
                    "record",
                    "operation",
                    "session",
                    "occurrence",
                    "student",
                    AttendanceStatus.REJECTED,
                    AttendanceReasonCode.QR_INVALID,
                    10,
                    10,
                )
            repository.writeAttempt(rejected)
            val replaced = repository.replaceRejectedRecord(rejected.copy(status = AttendanceStatus.REVIEW_REQUIRED))
            assertEquals(2, replaced.recordRevision)
            assertFalse(repository.compareAndSetRecord(rejected.copy(recordRevision = 2), 1))
        }
}
