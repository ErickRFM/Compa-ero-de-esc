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
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.academic.AcademicOccurrenceProjection
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.academic.mapper.AcademicMappers
import org.companerodeescuela.api.integrations.mock.MockAcademicProvider
import org.companerodeescuela.shared.contracts.AttendanceAttemptRequest
import org.companerodeescuela.shared.contracts.AttendanceReasonCode
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionStatus
import org.companerodeescuela.shared.contracts.AttendanceStatus
import org.companerodeescuela.shared.contracts.CreateAttendanceSessionRequest
import org.companerodeescuela.shared.contracts.ReviewAttendanceRequest
import org.companerodeescuela.shared.model.ClassOccurrence
import org.companerodeescuela.shared.model.PersonId

class AttendanceServiceTest {
    private val provider = MockAcademicProvider()
    private val initialInstant = Instant.parse("2026-10-05T08:02:00Z")
    private val occurrenceDate = LocalDate.parse("2026-10-05")

    @Test
    fun `teacher opens attendance only for a dated occurrence they teach`() = runTest {
        val occurrence = teacherOccurrence()
        val service = service()

        val session = service.openSession(
            teacherId = "T-0001",
            request = CreateAttendanceSessionRequest(
                occurrenceId = occurrence.id.value,
                occurrenceDate = occurrence.date.toString(),
            ),
        )

        assertEquals(occurrence.id.value, session.occurrenceId)
        assertEquals("C-9001", session.courseId)
        assertEquals("101-A", session.groupName)
        assertEquals("T-0001", session.openedBy)
    }

    @Test
    fun `same occurrence cannot create duplicate attendance sessions`() = runTest {
        val occurrence = teacherOccurrence()
        var sequence = 0
        val service = service(newId = { "session-" + (++sequence) })
        val request = CreateAttendanceSessionRequest(
            occurrenceId = occurrence.id.value,
            occurrenceDate = occurrence.date.toString(),
        )

        val first = service.openSession("T-0001", request)
        val second = service.openSession("T-0001", request)

        assertEquals("session-1", first.id)
        assertEquals(first, second)
    }

    @Test
    fun `identity enrollment and open session create likely attendance`() = runTest {
        val service = service()
        val session = service.openSession(
            "T-0001",
            requestFor(teacherOccurrence()),
        )

        val record = service.register(
            studentId = "2020-10455",
            sessionId = session.id,
            request = AttendanceAttemptRequest(
                operationId = "op-1",
                deviceTimestampEpochSeconds = initialInstant.epochSecond,
            ),
        )

        assertEquals(AttendanceStatus.LIKELY, record.status)
        assertEquals(AttendanceReasonCode.IDENTITY_SESSION_TIME, record.reasonCode)
        assertEquals(session.occurrenceId, record.occurrenceId)
    }

    @Test
    fun `retry with same operation id is idempotent`() = runTest {
        val service = service()
        val session = service.openSession("T-0001", requestFor(teacherOccurrence()))
        val request = AttendanceAttemptRequest(
            operationId = "op-1",
            deviceTimestampEpochSeconds = initialInstant.epochSecond,
        )

        val first = service.register("2020-10455", session.id, request)
        val second = service.register("2020-10455", session.id, request)

        assertEquals(first, second)
    }

    @Test
    fun `second client operation for same student still returns one canonical record`() = runTest {
        val repository = InMemoryAttendanceRepository()
        val service = service(repository = repository)
        val session = service.openSession("T-0001", requestFor(teacherOccurrence()))

        val first = service.register(
            "2020-10455",
            session.id,
            AttendanceAttemptRequest("op-1", initialInstant.epochSecond),
        )
        val second = service.register(
            "2020-10455",
            session.id,
            AttendanceAttemptRequest("op-2", initialInstant.epochSecond),
        )

        assertEquals(first.id, second.id)
        assertEquals(1, repository.recordsForSession(session.id).size)
    }

    @Test
    fun `teacher cannot inspect another teacher session without administrative scope`() = runTest {
        val service = service()
        val session = service.openSession("T-0001", requestFor(teacherOccurrence()))

        assertFailsWith<ApiException.Forbidden> {
            service.roster(
                actorId = "T-OTHER",
                sessionId = session.id,
                allowCrossOwner = false,
            )
        }
    }

    @Test
    fun `administrative scope can inspect another teacher session`() = runTest {
        val service = service()
        val session = service.openSession("T-0001", requestFor(teacherOccurrence()))

        val roster = service.roster(
            actorId = "admin-1",
            sessionId = session.id,
            allowCrossOwner = true,
        )

        assertSame(session, roster.session)
    }

    @Test
    fun `teacher review records reviewer and explicit reason`() = runTest {
        val service = service()
        val session = service.openSession("T-0001", requestFor(teacherOccurrence()))
        val record = service.register(
            "2020-10455",
            session.id,
            AttendanceAttemptRequest("op-1", initialInstant.epochSecond),
        )

        val reviewed = service.review(
            reviewerId = "T-0001",
            recordId = record.id,
            request = ReviewAttendanceRequest(
                status = AttendanceStatus.VERIFIED,
                reasonCode = AttendanceReasonCode.TEACHER_REVIEW,
            ),
        )

        assertEquals(AttendanceStatus.VERIFIED, reviewed.status)
        assertEquals("T-0001", reviewed.reviewedBy)
        assertEquals(AttendanceReasonCode.TEACHER_REVIEW, reviewed.reasonCode)
    }

    @Test
    fun `expired server window rejects a late direct attempt`() = runTest {
        val clock = MutableClock(initialInstant)
        val service = service(clock = clock)
        val session = service.openSession(
            "T-0001",
            requestFor(teacherOccurrence()).copy(durationMinutes = 1),
        )

        clock.advance(Duration.ofMinutes(2))

        assertFailsWith<ApiException.Conflict> {
            service.register(
                "2020-10455",
                session.id,
                AttendanceAttemptRequest("op-late", clock.instant().epochSecond),
            )
        }
    }

    @Test
    fun `active sessions are filtered by student enrollment`() = runTest {
        val repository = InMemoryAttendanceRepository()
        val service = service(repository = repository)
        val real = service.openSession("T-0001", requestFor(teacherOccurrence()))

        repository.createSession(
            AttendanceSessionResponse(
                id = "other-session",
                occurrenceId = "other-occurrence",
                courseId = "C-NOT-ENROLLED",
                groupName = "404-Z",
                occurrenceDate = occurrenceDate.toString(),
                scheduledStartsAt = "08:00",
                scheduledEndsAt = "09:00",
                openedBy = "T-0001",
                openedAtEpochSeconds = initialInstant.epochSecond,
                closesAtEpochSeconds = initialInstant.plusSeconds(600).epochSecond,
                status = AttendanceSessionStatus.OPEN,
            ),
        )

        assertEquals(listOf(real.id), service.activeFor("2020-10455").map { it.id })
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

    private fun requestFor(occurrence: ClassOccurrence): CreateAttendanceSessionRequest =
        CreateAttendanceSessionRequest(
            occurrenceId = occurrence.id.value,
            occurrenceDate = occurrence.date.toString(),
        )

    private fun service(
        repository: AttendanceRepository = InMemoryAttendanceRepository(),
        clock: Clock = Clock.fixed(initialInstant, ZoneOffset.UTC),
        newId: () -> String = { "session-1" },
    ): AttendanceService =
        AttendanceService(
            repository = repository,
            academicProvider = provider,
            clock = clock,
            newId = newId,
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
