package org.companerodeescuela.api.attendance

import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.academic.AcademicProvider
import org.companerodeescuela.api.integrations.mock.MockAcademicProvider
import org.companerodeescuela.api.integrations.mock.MockFixtures
import org.companerodeescuela.shared.contracts.AttendanceIdentitySource
import org.companerodeescuela.shared.contracts.AttendanceReasonCode
import org.companerodeescuela.shared.contracts.AttendanceRecordResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionStatus
import org.companerodeescuela.shared.contracts.AttendanceStatus
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AttendanceStudentHistoryTest {
    private val provider = MockAcademicProvider()
    private val studentId = MockFixtures.STUDENT_ID

    @Test
    fun `history reads closed persisted decisions after service recreation without active campus presence`() =
        runTest {
            val repository = InMemoryAttendanceRepository()
            val session = session("closed")
            repository.createSession(session)
            val record = record(session, "mine", 20)
            repository.writeAttempt(record)
            repository.writeAttempt(record(session, "other", 30).copy(studentId = "another-student"))

            val recreated = AttendanceStudentService(repository, AttendanceEnrollmentResolver(provider))

            assertEquals(listOf(record), recreated.myHistory(studentId))
            assertEquals(emptyList(), recreated.myHistory(MockFixtures.QA_STUDENT_ID))
        }

    @Test
    fun `history is bounded sorted deterministically and excludes another student's newer rows`() =
        runTest {
            val repository = InMemoryAttendanceRepository()
            for (index in 0..104) {
                val session = session("session-$index")
                repository.createSession(session)
                repository.writeAttempt(record(session, "record-${index.toString().padStart(3, '0')}", index.toLong()))
            }
            val service = AttendanceStudentService(repository, AttendanceEnrollmentResolver(provider))
            val history = service.myHistory(studentId)
            assertEquals(100, history.size)
            assertEquals("record-104", history.first().id)
            assertEquals("record-005", history.last().id)

            val equalTime = session("equal-time")
            repository.createSession(equalTime)
            repository.writeAttempt(record(equalTime, "record-tie", 104))
            assertEquals(listOf("record-104", "record-tie"), service.myHistory(studentId).take(2).map { it.id })
        }

    @Test
    fun `history does not cross native tenant or identity source for matching student IDs`() =
        runTest {
            val repository = InMemoryAttendanceRepository()
            val scoped =
                session("native").copy(
                    identitySource = AttendanceIdentitySource.NATIVE,
                    institutionId = "other-campus",
                    groupId = "other-group",
                )
            repository.createSession(scoped)
            repository.writeAttempt(record(scoped, "not-institutional", 10))
            val service = AttendanceStudentService(repository, AttendanceEnrollmentResolver(provider))
            assertEquals(emptyList(), service.myHistory(studentId))
            assertFailsWith<ApiException.Forbidden> {
                service.myHistory(studentId, UserSummary("different-student", "Different", roles = setOf(UserRole.STUDENT)))
            }
        }

    @Test
    fun `history immediately removes revoked enrollment and never converts authority outage to empty success`() =
        runTest {
            val repository = InMemoryAttendanceRepository()
            val session = session("closed")
            repository.createSession(session)
            repository.writeAttempt(record(session, "mine", 10))
            var revoked = false
            var unavailable = false
            val authority =
                object : AcademicProvider by provider {
                    override suspend fun getAcademicLoad(externalId: String) =
                        provider.getAcademicLoad(externalId).let { load ->
                            if (unavailable) throw ApiException.DependencyUnavailable("Synthetic authority outage")
                            if (revoked) load.copy(enrollments = emptyList()) else load
                        }
                }
            val service = AttendanceStudentService(repository, AttendanceEnrollmentResolver(authority))
            assertEquals(1, service.myHistory(studentId).size)
            revoked = true
            assertEquals(emptyList(), service.myHistory(studentId))
            unavailable = true
            assertFailsWith<ApiException.DependencyUnavailable> { service.myHistory(studentId) }
        }

    private fun session(id: String): AttendanceSessionResponse {
        val course = MockFixtures.ENROLLMENTS.first().course
        return AttendanceSessionResponse(
            id = id,
            occurrenceId = "occurrence-$id",
            courseId = course.externalId,
            groupName = course.groupName,
            occurrenceDate = "2026-10-05",
            scheduledStartsAt = "07:00",
            scheduledEndsAt = "08:00",
            openedBy = MockFixtures.TEACHER_ID,
            openedAtEpochSeconds = 1,
            closesAtEpochSeconds = 2,
            closedAtEpochSeconds = 2,
            status = AttendanceSessionStatus.CLOSED,
            identitySource = AttendanceIdentitySource.INSTITUTIONAL,
        )
    }

    private fun record(
        session: AttendanceSessionResponse,
        id: String,
        received: Long,
    ) = AttendanceRecordResponse(
        id = id,
        operationId = "operation-$id",
        sessionId = session.id,
        occurrenceId = session.occurrenceId,
        studentId = studentId,
        status = AttendanceStatus.REVIEW_REQUIRED,
        reasonCode = AttendanceReasonCode.CLASS_CALL_CONFIRMED,
        attemptedAtEpochSeconds = received,
        receivedAtEpochSeconds = received,
    )
}
