package org.companerodeescuela.api.attendance

import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.academic.AcademicOccurrenceProjection
import org.companerodeescuela.api.academic.groups.AcademicGroupMembershipRecord
import org.companerodeescuela.api.academic.groups.AcademicGroupRecord
import org.companerodeescuela.api.academic.groups.InMemoryAcademicGroupRepository
import org.companerodeescuela.api.integrations.academic.mapper.AcademicMappers
import org.companerodeescuela.api.integrations.mock.MockAcademicProvider
import org.companerodeescuela.api.presence.InMemorySchoolPresenceRepository
import org.companerodeescuela.api.presence.SchoolPresencePolicy
import org.companerodeescuela.api.presence.SchoolPresenceService
import org.companerodeescuela.shared.contracts.SchoolNetworkEvidence
import org.companerodeescuela.shared.contracts.StartSchoolPresenceRequest
import org.companerodeescuela.shared.model.PersonId

class TeacherCampusRosterServiceTest {
    private val now = Instant.parse("2026-10-05T08:02:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val date = LocalDate.parse("2026-10-05")
    private val provider = MockAcademicProvider()

    @Test
    fun `assigned teacher sees only matching group and verified campus entries`() = runTest {
        val groups = InMemoryAcademicGroupRepository()
        groups.create(AcademicGroupRecord("group-101", "101-A", true, now))
        groups.create(AcademicGroupRecord("other", "404-Z", true, now))
        groups.assign(AcademicGroupMembershipRecord("group-101", "2020-10455", now))
        groups.assign(AcademicGroupMembershipRecord("group-101", "student-pending", now))
        groups.assign(AcademicGroupMembershipRecord("other", "student-secret", now))
        val presence = createPresence()
        presence.start(
            "2020-10455",
            StartSchoolPresenceRequest(
                "operation-1", "school-entry", SchoolNetworkEvidence(ssid = "UD4-Alumno"),
                now.epochSecond,
            ),
        )
        val service = TeacherCampusRosterService(
            ProviderAttendanceOccurrenceResolver(provider), groups, presence, InMemoryAttendanceRepository(),
        )
        val occurrence = teacherOccurrence()
        val roster = service.forTeacher("T-0001", occurrence.id.value, date.toString())
        assertEquals("101-A", roster.groupName)
        assertEquals(2, roster.students.size)
        assertEquals(listOf("2020-10455", "student-pending"), roster.students.map { it.studentId })
        assertEquals(now.epochSecond, roster.students.first().campusEntryAtEpochSeconds)
        assertEquals(null, roster.students.last().campusEntryAtEpochSeconds)
        assertFails { service.forTeacher("T-OTHER", occurrence.id.value, date.toString()) }
    }

    @Test
    fun `missing group mapping fails closed rather than reporting nobody in school`() = runTest {
        val service = TeacherCampusRosterService(
            ProviderAttendanceOccurrenceResolver(provider),
            InMemoryAcademicGroupRepository(),
            createPresence(),
            InMemoryAttendanceRepository(),
        )
        val occurrence = teacherOccurrence()
        assertFails { service.forTeacher("T-0001", occurrence.id.value, date.toString()) }
    }

    private suspend fun teacherOccurrence() = run {
        val courses = provider.listCourses().associateBy { it.externalId }
        val schedule = AcademicMappers.toSchedule(
            ownerId = PersonId("T-0001"),
            slots = provider.getSchedule("T-0001", date),
            coursesById = courses,
        )
        AcademicOccurrenceProjection.project(schedule, date).first()
    }

    private fun createPresence(): SchoolPresenceService {
        val qr = MessageDigest.getInstance("SHA-256")
            .digest("school-entry".toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
        return SchoolPresenceService(
            repository = InMemorySchoolPresenceRepository(),
            policy = SchoolPresencePolicy(qr, setOf("UD4-Alumno"), emptySet()),
            clock = clock,
        )
    }
}
