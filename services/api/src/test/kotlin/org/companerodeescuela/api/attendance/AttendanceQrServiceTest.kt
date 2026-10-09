package org.companerodeescuela.api.attendance

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionStatus

class AttendanceQrServiceTest {
    private val initial = Instant.parse("2026-10-05T08:02:00Z")

    @Test
    fun `QR is expired exactly at its expiration boundary`() = runTest {
        val repository = InMemoryAttendanceRepository()
        repository.createSession(session("session-1", initial))
        val service = AttendanceQrService("q".repeat(48).toCharArray(), repository, MutableClock(initial))
        val issued = service.issue("teacher-1", "session-1")
        assertIs<QrEvidenceResult.Expired>(
            service.verify(issued.token, "session-1", issued.expiresAtEpochSeconds),
        )
    }

    @Test
    fun `issued token verifies only for its attendance session`() = runTest {
        val repository = InMemoryAttendanceRepository()
        val clock = MutableClock(initial)
        repository.createSession(session("session-1", initial))
        val service = AttendanceQrService("q".repeat(48).toCharArray(), repository, clock)

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
        val service = AttendanceQrService("q".repeat(48).toCharArray(), repository, clock)
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
        val service = AttendanceQrService("q".repeat(48).toCharArray(), repository, clock)

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
        val service = AttendanceQrService("q".repeat(48).toCharArray(), repository, clock)

        val issued = service.issue("teacher-1", "session-1")

        assertEquals(initial.plusSeconds(10).epochSecond, issued.expiresAtEpochSeconds)
    }

    @Test
    fun `preissued pack stays inside session and cannot be issued by another teacher`() = runTest {
        val repository = InMemoryAttendanceRepository()
        val clock = MutableClock(initial)
        repository.createSession(session("session-1", initial))
        val service = AttendanceQrService("q".repeat(48).toCharArray(), repository, clock)

        val pack = service.issuePack("teacher-1", "session-1")
        assertEquals(40, pack.size)
        assertEquals(initial.epochSecond, pack.first().issuedAtEpochSeconds)
        assertEquals(initial.plusSeconds(585).epochSecond, pack.last().issuedAtEpochSeconds)
        assertEquals(initial.plusSeconds(600).epochSecond, pack.last().expiresAtEpochSeconds)
        clock.advance(Duration.ofSeconds(45))
        assertIs<QrEvidenceResult.Valid>(
            service.verify(pack[3].token, "session-1", clock.instant().epochSecond),
        )
        kotlin.test.assertFailsWith<org.companerodeescuela.api.errors.ApiException.Forbidden> {
            service.issuePack("other-teacher", "session-1")
        }
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
