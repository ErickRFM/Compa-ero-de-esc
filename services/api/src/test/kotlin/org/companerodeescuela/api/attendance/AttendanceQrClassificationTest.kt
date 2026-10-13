package org.companerodeescuela.api.attendance

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.config.Environment
import org.companerodeescuela.api.integrations.academic.AcademicProvider
import org.companerodeescuela.api.integrations.academic.dto.ExternalAcademicLoad
import org.companerodeescuela.api.integrations.academic.dto.ExternalStudent
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
    fun `current signed QR is valid evidence but requires physical presence review`() = runTest {
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

        assertEquals(AttendanceStatus.REVIEW_REQUIRED, record.status)
        assertEquals(AttendanceReasonCode.QR_VALID, record.reasonCode)
    }

    @Test
    fun `non local environment without presence service rejects attendance without QR`() = runTest {
        val setup = setup(environment = Environment.STAGING)

        assertFailsWith<ApiException.Forbidden> {
            setup.attendance.register(
                studentId = "2020-10455",
                sessionId = setup.sessionId,
                request = AttendanceAttemptRequest(
                    operationId = "op-no-campus-or-qr",
                    deviceTimestampEpochSeconds = setup.clock.instant().epochSecond,
                    qrToken = null,
                ),
            )
        }
        assertEquals(emptyList(), setup.repository.recordsForSession(setup.sessionId))
    }

    @Test
    fun `server time accepts a current signed QR regardless of forged client time`() = runTest {
        val setup = setup()
        val qr = setup.qr.issue("T-0001", setup.sessionId)

        val record = setup.attendance.register(
            studentId = "2020-10455",
            sessionId = setup.sessionId,
            request = AttendanceAttemptRequest(
                operationId = "op-forged-current-time",
                deviceTimestampEpochSeconds = setup.clock.instant().plusSeconds(600).epochSecond,
                qrToken = qr.token,
            ),
        )

        assertEquals(AttendanceStatus.REVIEW_REQUIRED, record.status)
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

    @Test
    fun `forged client capture time cannot extend the server late sync window`() = runTest {
        val setup = setup(durationMinutes = 1)
        val qr = setup.qr.issue("T-0001", setup.sessionId)
        val forgedCapture = setup.clock.instant().plusSeconds(10).epochSecond
        setup.clock.advance(Duration.ofHours(3))

        assertFailsWith<ApiException.Domain> {
            setup.attendance.register(
                studentId = "2020-10455",
                sessionId = setup.sessionId,
                request = AttendanceAttemptRequest("op-forged-late", forgedCapture, qr.token),
            )
        }
    }

    @Test
    fun `fourth distinct student using the same QR nonce is explicitly marked for review`() = runTest {
        val setup = setup(academicProvider = MultiStudentAcademicProvider())
        val qr = setup.qr.issue("T-0001", setup.sessionId)
        val students = listOf("student-a", "student-b", "student-c", "student-d")

        val records = students.mapIndexed { index, studentId ->
            setup.attendance.register(
                studentId = studentId,
                sessionId = setup.sessionId,
                request = AttendanceAttemptRequest(
                    "op-nonce-$index",
                    setup.clock.instant().epochSecond,
                    qr.token,
                ),
            )
        }

        assertEquals(AttendanceStatus.REVIEW_REQUIRED, records.last().status)
        assertEquals(AttendanceReasonCode.DUPLICATE, records.last().reasonCode)
    }

    @Test
    fun `late synced students share nonce reuse tracking through the review window`() = runTest {
        val setup = setup(durationMinutes = 1, academicProvider = MultiStudentAcademicProvider())
        val qr = setup.qr.issue("T-0001", setup.sessionId)
        val capturedAt = setup.clock.instant().plusSeconds(10).epochSecond
        setup.clock.advance(Duration.ofMinutes(2))

        val records = listOf("student-a", "student-b", "student-c", "student-d").mapIndexed { index, studentId ->
            setup.attendance.register(
                studentId = studentId,
                sessionId = setup.sessionId,
                request = AttendanceAttemptRequest("op-late-nonce-$index", capturedAt, qr.token),
            )
        }

        assertEquals(AttendanceStatus.REVIEW_REQUIRED, records.last().status)
        assertEquals(AttendanceReasonCode.DUPLICATE, records.last().reasonCode)
    }

    private suspend fun setup(
        durationMinutes: Int = 6,
        academicProvider: AcademicProvider = provider,
        environment: Environment = Environment.LOCAL,
    ): Setup {
        val repository = InMemoryAttendanceRepository()
        val clock = MutableClock(initial)
        val qr = AttendanceQrService(
            secret = "q".repeat(48).toCharArray(),
            repository = repository,
            accessPolicy = AttendanceAccessPolicy(repository, ProviderAttendanceOccurrenceResolver(academicProvider)),
            clock = clock,
        )
        val sessionService = AttendanceSessionService(
            repository = repository,
            occurrenceResolver = ProviderAttendanceOccurrenceResolver(academicProvider),
            clock = clock,
            newId = { "session-1" },
        )
        val attendance = AttendanceStudentService(
            repository = repository,
            enrollmentResolver = AttendanceEnrollmentResolver(academicProvider),
            qrService = qr,
            clock = clock,
            environment = environment,
        )
        val occurrence = teacherOccurrence(academicProvider)
        val session = sessionService.openSession(
            teacherId = "T-0001",
            request = CreateAttendanceSessionRequest(
                occurrenceId = occurrence.id.value,
                occurrenceDate = occurrence.date.toString(),
                durationMinutes = durationMinutes,
            ),
        )
        return Setup(attendance, qr, clock, session.id, repository)
    }

    private suspend fun teacherOccurrence(academicProvider: AcademicProvider = provider): ClassOccurrence {
        val courses = academicProvider.listCourses().associateBy { it.externalId }
        val schedule = AcademicMappers.toSchedule(
            ownerId = PersonId("T-0001"),
            slots = academicProvider.getSchedule("T-0001", occurrenceDate),
            coursesById = courses,
        )
        return AcademicOccurrenceProjection.project(schedule, occurrenceDate).first()
    }

    private data class Setup(
        val attendance: AttendanceStudentService,
        val qr: AttendanceQrService,
        val clock: MutableClock,
        val sessionId: String,
        val repository: InMemoryAttendanceRepository,
    )

    private class MultiStudentAcademicProvider : AcademicProvider {
        private val delegate = MockAcademicProvider()
        override val id: String = "multi-student-test"
        override val displayName: String = "Multi-student test provider"

        override suspend fun getStudent(externalId: String): ExternalStudent? =
            delegate.getStudent("2020-10455")?.copy(externalId = externalId)

        override suspend fun getAcademicLoad(externalId: String): ExternalAcademicLoad =
            delegate.getAcademicLoad("2020-10455").let { it.copy(student = it.student.copy(externalId = externalId)) }

        override suspend fun listCourses() = delegate.listCourses()
        override suspend fun getSchedule(externalId: String, weekOf: LocalDate?) = delegate.getSchedule(externalId, weekOf)
        override suspend fun listTeachers() = delegate.listTeachers()
    }

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
