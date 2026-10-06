package org.companerodeescuela.api.academic

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.academic.groups.AcademicGroupMembershipRecord
import org.companerodeescuela.api.academic.groups.AcademicGroupRecord
import org.companerodeescuela.api.academic.groups.InMemoryAcademicGroupRepository
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.mock.MockAcademicProvider
import org.companerodeescuela.shared.contracts.AcademicDataSource
import org.companerodeescuela.shared.contracts.ClassOccurrenceStatusContract
import org.companerodeescuela.shared.contracts.UpsertScheduleBlockRequest

class AcademicServiceTest {

    @Test
    fun `mock academic load becomes a stable client contract`() = runTest {
        val response = AcademicService(MockAcademicProvider())
            .loadFor("2020-10455")

        assertEquals("2020-10455", response.student.id)
        assertEquals("Ana López Hernández", response.student.displayName)
        assertEquals(3, response.schedule.entries.size)
        assertEquals("Álgebra Lineal", response.schedule.entries.first().subjectName)
        assertEquals("A-204", response.schedule.entries.first().classroomName)
    }

    @Test
    fun `dated schedule v2 projects recurring slots into the requested week`() = runTest {
        val service = AcademicService(MockAcademicProvider())
        val response = service.scheduleWeekFor(
            externalId = "2020-10455",
            weekOf = LocalDate.parse("2026-10-07"),
        )

        assertEquals("2026-10-05", response.weekStartsOn)
        assertEquals("2026-10-11", response.weekEndsOn)
        assertEquals(3, response.occurrences.size)
        assertEquals(
            listOf("2026-10-05", "2026-10-06", "2026-10-08"),
            response.occurrences.map { it.date },
        )
        assertEquals(
            listOf("07:00", "11:00", "09:00"),
            response.occurrences.map { it.startsAt },
        )
        assertEquals(
            List(3) { ClassOccurrenceStatusContract.SCHEDULED },
            response.occurrences.map { it.status },
        )
    }

    @Test
    fun `occurrence ids are stable for one meeting and differ across weeks`() = runTest {
        val service = AcademicService(MockAcademicProvider())
        val weekOne = service.scheduleWeekFor("2020-10455", LocalDate.parse("2026-10-05"))
        val weekOneAgain = service.scheduleWeekFor("2020-10455", LocalDate.parse("2026-10-08"))
        val weekTwo = service.scheduleWeekFor("2020-10455", LocalDate.parse("2026-10-12"))

        assertEquals(
            weekOne.occurrences.map { it.id },
            weekOneAgain.occurrences.map { it.id },
        )
        assertNotEquals(
            weekOne.occurrences.first().id,
            weekTwo.occurrences.first().id,
        )
        assertEquals(
            weekOne.occurrences.first().patternId,
            weekTwo.occurrences.first().patternId,
        )
    }

    @Test
    fun `dated schedule v2 also works for teacher identities`() = runTest {
        val response = AcademicService(MockAcademicProvider())
            .scheduleWeekFor(
                externalId = "T-0001",
                weekOf = LocalDate.parse("2026-10-05"),
            )

        assertEquals("T-0001", response.ownerId)
        assertEquals(3, response.occurrences.size)
        assertEquals(
            setOf("Mtra. Elena Ríos Salgado"),
            response.occurrences.map { it.teacherName }.toSet(),
        )
    }

    @Test
    fun `missing academic identity becomes public not found`() = runTest {
        assertFailsWith<ApiException.NotFound> {
            AcademicService(MockAcademicProvider()).loadFor("missing")
        }
    }

    @Test
    fun groupScheduleIsInheritedByLinkedStudentsWithoutSchoolApiDependency() = runTest {
        val groups = InMemoryAcademicGroupRepository()
        val schedules = InMemoryAcademicScheduleOverrideRepository()
        val instant = Instant.parse("2026-10-06T17:00:00Z")
        groups.create(
            AcademicGroupRecord(
                id = "9A",
                name = "9A",
                active = true,
                createdAt = instant,
            ),
        )
        groups.assign(AcademicGroupMembershipRecord("9A", "2020-10455", instant))
        groups.assign(AcademicGroupMembershipRecord("9A", "local-student", instant))

        AcademicScheduleManagementService(
            repository = schedules,
            clock = Clock.fixed(instant, ZoneOffset.UTC),
            newId = { "SCH-GROUP" },
        ).upsert(
            actorId = "admin-1",
            source = AcademicDataSource.ADMIN_MANUAL,
            request = UpsertScheduleBlockRequest(
                ownerId = "9A",
                dayOfWeek = "FRIDAY",
                startTime = "12:00",
                endTime = "14:00",
                subjectId = "PM-9",
                subjectName = "Programación Móvil",
                teacherId = "teacher-1",
                teacherName = "Mtra. Elena",
                room = "LAB-3",
                groupId = "9A",
                groupName = "9A",
            ),
        )

        val service = AcademicService(
            provider = MockAcademicProvider(),
            scheduleOverrides = schedules,
            groupRepository = groups,
        )
        val institutionalUser = service.loadFor("2020-10455")
        assertTrue(institutionalUser.schedule.entries.any { it.subjectName == "Programación Móvil" })

        val independentUser = service.loadFor("local-student")
        assertEquals("local-student", independentUser.schedule.ownerId)
        assertEquals(listOf("Programación Móvil"), independentUser.schedule.entries.map { it.subjectName })
    }

}
