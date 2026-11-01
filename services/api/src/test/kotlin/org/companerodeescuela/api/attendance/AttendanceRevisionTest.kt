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
    fun `durable closure finishes missing records after crash without fresh source access`() =
        runTest {
            val storage = InMemoryAttendanceRepository()
            val session =
                org.companerodeescuela.shared.contracts.AttendanceSessionResponse(
                    "durable-pass",
                    "durable-occurrence",
                    "section",
                    "group",
                    "2026-10-05",
                    "2026-10-05T08:00:00Z",
                    "2026-10-05T09:00:00Z",
                    "teacher",
                    100,
                    300,
                    status = org.companerodeescuela.shared.contracts.AttendanceSessionStatus.OPEN,
                )
            storage.createSession(session)
            storage.closeSessionIfOpen(session.id, "teacher", true, 200, listOf("student-a", "student-b"))
            var writes = 0
            val failing =
                object : AttendanceRepository by storage {
                    override suspend fun writeAttempt(record: AttendanceRecordResponse): AttemptWriteResult {
                        if (++writes == 2)throw IllegalStateException("Synthetic crash")
                        return storage.writeAttempt(record)
                    }
                }

            fun service(repository: AttendanceRepository) =
                AttendanceSessionService(
                    repository,
                    ProviderAttendanceOccurrenceResolver(
                        org.companerodeescuela.api.integrations.mock
                            .MockAcademicProvider(),
                    ),
                )
            assertEquals(0, service(failing).reconcileClosures())
            assertEquals(1, storage.recordsForSession(session.id).size)
            assertEquals(2, storage.closureWork(session.id)!!.studentIds.size)
            assertEquals(1, service(storage).reconcileClosures())
            assertEquals(2, storage.recordsForSession(session.id).size)
            assertTrue(
                storage.recordsForSession(session.id).all {
                    it.disposition ==
                        org.companerodeescuela.shared.contracts.AttendanceDisposition.ABSENT
                },
            )
            assertEquals(null, storage.closureWork(session.id))
            assertEquals(0, service(storage).reconcileClosures())
        }

    @Test
    fun `closing a pass preserves the first authorized teacher closure across retries`() =
        runTest {
            val repository = InMemoryAttendanceRepository()
            val session =
                org.companerodeescuela.shared.contracts.AttendanceSessionResponse(
                    "pass",
                    "occurrence",
                    "section",
                    "group",
                    "2026-10-05",
                    "2026-10-05T08:00:00Z",
                    "2026-10-05T09:00:00Z",
                    "teacher",
                    100,
                    300,
                    status = org.companerodeescuela.shared.contracts.AttendanceSessionStatus.OPEN,
                )
            repository.createSession(session)
            val first = repository.closeSessionIfOpen(session.id, "teacher", true, 200)!!
            val repeated = repository.closeSessionIfOpen(session.id, "administrator", false, 250)!!
            assertTrue(first.closedByTeacher)
            assertEquals(first, repeated)
            assertEquals(200, repeated.closedAtEpochSeconds)
        }

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
