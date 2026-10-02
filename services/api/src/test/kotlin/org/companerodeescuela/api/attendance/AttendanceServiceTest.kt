package org.companerodeescuela.api.attendance

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.mock.MockAcademicProvider
import org.companerodeescuela.shared.contracts.AttendanceAttemptRequest
import org.companerodeescuela.shared.contracts.AttendanceStatus
import org.companerodeescuela.shared.contracts.CreateAttendanceSessionRequest

class AttendanceServiceTest {
    private val clock = Clock.fixed(
        Instant.parse("2026-10-05T08:02:00Z"),
        ZoneOffset.UTC,
    )

    @Test
    fun `identity plus enrollment plus open session creates likely attendance`() = runTest {
        val service = service()
        val session = service.openSession(
            teacherId = "teacher-1",
            request = CreateAttendanceSessionRequest(
                courseId = "C-9001",
                groupName = "101-A",
            ),
        )

        val record = service.register(
            studentId = "2020-10455",
            sessionId = session.id,
            request = AttendanceAttemptRequest(
                operationId = "op-1",
                deviceTimestampEpochSeconds = clock.instant().epochSecond,
            ),
        )

        assertEquals(AttendanceStatus.LIKELY, record.status)
        assertEquals(AttendanceService.REASON_IDENTITY_SESSION_TIME, record.reasonCode)
    }

    @Test
    fun `retry with same operation id is idempotent`() = runTest {
        val service = service()
        val session = service.openSession(
            "teacher-1",
            CreateAttendanceSessionRequest("C-9001", "101-A"),
        )
        val request = AttendanceAttemptRequest(
            operationId = "op-1",
            deviceTimestampEpochSeconds = clock.instant().epochSecond,
        )

        val first = service.register("2020-10455", session.id, request)
        val second = service.register("2020-10455", session.id, request)

        assertEquals(first, second)
    }

    @Test
    fun `student outside the group cannot register`() = runTest {
        val service = service()
        val session = service.openSession(
            "teacher-1",
            CreateAttendanceSessionRequest("missing-course", "missing-group"),
        )

        assertFailsWith<ApiException.Forbidden> {
            service.register(
                "2020-10455",
                session.id,
                AttendanceAttemptRequest("op-2", clock.instant().epochSecond),
            )
        }
    }

    private fun service(): AttendanceService = AttendanceService(
        repository = InMemoryAttendanceRepository(),
        academicProvider = MockAcademicProvider(),
        clock = clock,
        newId = { "session-1" },
    )
}
