package org.companerodeescuela.api.attendance

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionStatus
import org.companerodeescuela.shared.model.AcademicId
import org.companerodeescuela.shared.model.ClassOccurrence
import org.companerodeescuela.shared.model.Course
import org.companerodeescuela.shared.model.Group
import org.companerodeescuela.shared.model.Person
import org.companerodeescuela.shared.model.PersonId
import org.companerodeescuela.shared.model.Subject
import org.companerodeescuela.shared.model.Teacher

class AttendanceQrServiceTest {
    private val initial = Instant.parse("2026-10-05T08:02:00Z")

    @Test
    fun `issued token verifies only for its attendance session`() = runTest {
        val repository = InMemoryAttendanceRepository()
        val clock = MutableClock(initial)
        repository.createSession(session("session-1", initial))
        val service = AttendanceQrService("q".repeat(48).toCharArray(), repository, clock, accessPolicy = policy(repository))

        val issued = service.issue("teacher-1", "session-1")

        assertIs<QrEvidenceResult.Valid>(
            service.verify(issued.token, "session-1", initial.epochSecond),
        )
        assertIs<QrEvidenceResult.WrongSession>(
            service.verify(issued.token, "session-2", initial.epochSecond),
        )
    }

    @Test
    fun `tampering any signed token byte invalidates evidence`() = runTest {
        val repository = InMemoryAttendanceRepository()
        val clock = MutableClock(initial)
        repository.createSession(session("session-1", initial))
        val service = AttendanceQrService("q".repeat(48).toCharArray(), repository, clock, accessPolicy = policy(repository))
        val issued = service.issue("teacher-1", "session-1")
        val tampered = issued.token.dropLast(1) +
            if (issued.token.last() == 'A') "B" else "A"

        assertIs<QrEvidenceResult.Invalid>(
            service.verify(tampered, "session-1", initial.epochSecond),
        )
    }

    @Test
    fun `QR rotates nonce and expires quickly`() = runTest {
        val repository = InMemoryAttendanceRepository()
        val clock = MutableClock(initial)
        repository.createSession(session("session-1", initial))
        val service = AttendanceQrService("q".repeat(48).toCharArray(), repository, clock, accessPolicy = policy(repository))

        val first = service.issue("teacher-1", "session-1")
        val second = service.issue("teacher-1", "session-1")

        assertNotEquals(first.token, second.token)
        assertEquals(15, first.rotateAfterSeconds)

        clock.advance(Duration.ofSeconds(26))
        assertIs<QrEvidenceResult.Expired>(
            service.verify(first.token, "session-1", clock.instant().epochSecond),
        )
    }

    @Test
    fun `token expiration never extends beyond attendance session close`() = runTest {
        val repository = InMemoryAttendanceRepository()
        val clock = MutableClock(initial)
        repository.createSession(
            session("session-1", initial).copy(
                closesAtEpochSeconds = initial.plusSeconds(10).epochSecond,
            ),
        )
        val service = AttendanceQrService("q".repeat(48).toCharArray(), repository, clock, accessPolicy = policy(repository))

        val issued = service.issue("teacher-1", "session-1")

        assertEquals(initial.plusSeconds(10).epochSecond, issued.expiresAtEpochSeconds)
    }

    private fun policy(repository: AttendanceRepository): AttendanceAccessPolicy {
        val teacher = Teacher(Person(PersonId("teacher-1"), "QA teacher"))
        val course = Course(AcademicId("C-1"), Subject(AcademicId("subject"), "QA", "QA subject"), teacher, "QA term")
        val occurrence = ClassOccurrence(AcademicId("occ-1"), null, Group(AcademicId("group"), course, "A"),
            LocalDate.parse("2026-10-05"), LocalTime.parse("08:00"), LocalTime.parse("09:00"), null, teacher)
        return AttendanceAccessPolicy(repository, object : AttendanceOccurrenceResolver {
            override suspend fun resolveTeacherOccurrence(teacherId: String, occurrenceId: String, occurrenceDate: LocalDate) = occurrence
        })
    }

    private fun session(id: String, openedAt: Instant) = AttendanceSessionResponse(
        id = id,
        occurrenceId = "occ-1",
        courseId = "C-1",
        groupName = "A",
        occurrenceDate = "2026-10-05",
        scheduledStartsAt = "08:00",
        scheduledEndsAt = "09:00",
        openedBy = "teacher-1",
        openedAtEpochSeconds = openedAt.epochSecond,
        closesAtEpochSeconds = openedAt.plusSeconds(600).epochSecond,
        status = AttendanceSessionStatus.OPEN,
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
