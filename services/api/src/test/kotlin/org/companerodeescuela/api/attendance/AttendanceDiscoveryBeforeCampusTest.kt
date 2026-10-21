package org.companerodeescuela.api.attendance

import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.integrations.mock.MockAcademicProvider
import org.companerodeescuela.api.presence.InMemorySchoolPresenceRepository
import org.companerodeescuela.api.presence.SchoolPresencePolicy
import org.companerodeescuela.api.presence.SchoolPresenceService
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class AttendanceDiscoveryBeforeCampusTest {
    @Test
    fun `enrolled student can list classroom before institutional QR check in`() =
        runTest {
            val now = Instant.parse("2026-10-05T08:02:00Z")
            val clock = Clock.fixed(now, ZoneOffset.UTC)
            val repository = InMemoryAttendanceRepository()
            val session =
                AttendanceSessionResponse(
                    id = "session-1",
                    occurrenceId = "occurrence-1",
                    courseId = "C-9001",
                    groupName = "101-A",
                    occurrenceDate = "2026-10-05",
                    scheduledStartsAt = "08:00",
                    scheduledEndsAt = "09:00",
                    openedBy = "T-0001",
                    openedAtEpochSeconds = now.epochSecond,
                    closesAtEpochSeconds = now.plusSeconds(600).epochSecond,
                    status = AttendanceSessionStatus.OPEN,
                )
            repository.createSession(session)
            val presence =
                SchoolPresenceService(
                    repository = InMemorySchoolPresenceRepository(),
                    policy =
                        SchoolPresencePolicy(
                            entryQrSha256 = "0".repeat(64),
                            allowedSsids = setOf("UPTLAX-CAMPUS"),
                            allowedBssids = emptySet(),
                        ),
                    clock = clock,
                )
            val service =
                AttendanceStudentService(
                    repository = repository,
                    enrollmentResolver = AttendanceEnrollmentResolver(MockAcademicProvider()),
                    schoolPresenceService = presence,
                    clock = clock,
                )
            assertEquals(null, presence.activeFor("2020-10455"))
            assertEquals(listOf(session.id), service.activeFor("2020-10455").map { it.id })
            assertEquals(0, repository.recordsForSession(session.id).size)
        }
}
