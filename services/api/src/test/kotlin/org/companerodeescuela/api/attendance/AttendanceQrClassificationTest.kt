package org.companerodeescuela.api.attendance

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.academic.AcademicOccurrenceProjection
import org.companerodeescuela.api.integrations.academic.mapper.AcademicMappers
import org.companerodeescuela.api.integrations.mock.MockAcademicProvider
import org.companerodeescuela.shared.contracts.AttendanceAttemptRequest
import org.companerodeescuela.shared.contracts.AttendanceReasonCode
import org.companerodeescuela.shared.contracts.AttendanceStatus
import org.companerodeescuela.shared.contracts.CreateAttendanceSessionRequest
import org.companerodeescuela.shared.model.ClassOccurrence
import org.companerodeescuela.shared.model.PersonId

class AttendanceQrClassificationTest {
    private val provider = MockAcademicProvider()
    private val initial = Instant.parse("2026-10-05T08:02:00Z")
    private val occurrenceDate = LocalDate.parse("2026-10-05")

    @Test
    fun `current signed QR verifies attendance`() = runTest {
        val setup = setup()
        val qr = setup.qr.issue("T-0001", setup.sessionId)

        val record = setup.attendance.register(
            studentId = "2020-10455",
            sessionId = setup.sessionId,
            request = AttendanceAttemptRequest(
                operationId = "op-qr",
                deviceTimestampEpochSeconds = setup.clock.instant().epochSecond,
                qrToken = qr.token,
            ),
        )

        assertEquals(AttendanceStatus.VERIFIED, record.status)
        assertEquals(AttendanceReasonCode.QR_VALID, record.reasonCode)
    }

    @Test
    fun `changed QR payload is rejected and remains auditable`() = runTest {
        val setup = setup()
        val qr = setup.qr.issue("T-0001", setup.sessionId)
        val changed = qr.token.dropLast(1) +
            if (qr.token.last() == 'A') "B" else "A"

        val record = setup.attendance.register(
            studentId = "2020-10455",
            sessionId = setup.sessionId,
            request = AttendanceAttemptRequest(
                operationId = "op-invalid",
                deviceTimestampEpochSeconds = setup.clock.instant().epochSecond,
                qrToken = changed,
            ),
        )

        assertEquals(AttendanceStatus.REJECTED, record.status)
        assertEquals(AttendanceReasonCode.QR_INVALID, record.reasonCode)
    }

    @Test
    fun `offline signed QR captured in time becomes review required when synced late`() = runTest {
        val setup = setup(durationMinutes = 1)
        val qr = setup.qr.issue("T-0001", setup.sessionId)
        val capturedAt = setup.clock.instant().plusSeconds(10).epochSecond

        setup.clock.advance(Duration.ofMinutes(2))

        val record = setup.attendance.register(
            studentId = "2020-10455",
            sessionId = setup.sessionId,
            request = AttendanceAttemptRequest(
                operationId = "op-offline",
                deviceTimestampEpochSeconds = capturedAt,
                qrToken = qr.token,
            ),
        )

        assertEquals(AttendanceStatus.REVIEW_REQUIRED, record.status)
        assertEquals(AttendanceReasonCode.OFFLINE_LATE_SYNC, record.reasonCode)
    }

    private suspend fun setup(durationMinutes: Int = 6): Setup {
        val repository = InMemoryAttendanceRepository()
        val clock = MutableClock(initial)
        val qr = AttendanceQrService(
            secret = "q".repeat(48).toCharArray(),
            repository = repository,
            clock = clock,
        )
        val sessionService = AttendanceSessionService(
            repository = repository,
            occurrenceResolver = ProviderAttendanceOccurrenceResolver(provider),
            clock = clock,
            newId = { "session-1" },
        )
        val attendance = AttendanceStudentService(
            repository = repository,
            enrollmentResolver = AttendanceEnrollmentResolver(provider),
            qrService = qr,
            clock = clock,
        )
        val occurrence = teacherOccurrence()
        val session = sessionService.openSession(
            teacherId = "T-0001",
            request = CreateAttendanceSessionRequest(
                occurrenceId = occurrence.id.value,
                occurrenceDate = occurrence.date.toString(),
                durationMinutes = durationMinutes,
            ),
        )
        return Setup(attendance, qr, clock, session.id)
    }

    private suspend fun teacherOccurrence(): ClassOccurrence {
        val courses = provider.listCourses().associateBy { it.externalId }
        val schedule = AcademicMappers.toSchedule(
            ownerId = PersonId("T-0001"),
            slots = provider.getSchedule("T-0001", occurrenceDate),
            coursesById = courses,
        )
        return AcademicOccurrenceProjection.project(schedule, occurrenceDate).first()
    }

    private data class Setup(
        val attendance: AttendanceStudentService,
        val qr: AttendanceQrService,
        val clock: MutableClock,
        val sessionId: String,
    )

    private class MutableClock(
        private var now: Instant,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
        fun advance(duration: Duration) {
            now = now.plus(duration)
        }
    }
}
