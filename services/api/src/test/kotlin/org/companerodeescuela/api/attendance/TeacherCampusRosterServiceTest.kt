package org.companerodeescuela.api.attendance

import org.companerodeescuela.api.academic.*
import org.companerodeescuela.api.auth.*
import org.companerodeescuela.api.classroom.*
import org.companerodeescuela.api.test.ScopedAcademicFixtures
import org.companerodeescuela.shared.contracts.*
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
        groups.create(ScopedAcademicFixtures.group("group-101","101-A"))
        groups.create(AcademicGroupRecord("other", "404-Z", true, now))
        groups.assign(AcademicGroupMembershipRecord("group-101", "2020-10455", now))
        groups.assign(AcademicGroupMembershipRecord("group-101", "student-pending", now))
        groups.assign(AcademicGroupMembershipRecord("other", "student-secret", now))
        val teacher=UserSummary("T-0001","Actual Teacher",roles=setOf(UserRole.TEACHER),institutionId=ScopedAcademicFixtures.INSTITUTION)
        val student=UserSummary("2020-10455","Actual Student",roles=setOf(UserRole.STUDENT),institutionId=ScopedAcademicFixtures.INSTITUTION)
        val pending=student.copy(id="student-pending",displayName="Student with no school day")
        val accounts=ScopedAcademicFixtures.accounts(teacher,student,pending)
        val classRepo=InMemoryClassroomRepository()
        classRepo.createClassroom(NativeClassroom("native-class","Actual Mathematics",null,"Room","group-101","101-A",teacher.id,teacher.displayName,ClassroomStatus.ACTIVE,now,0,0))
        classRepo.addMembershipIfAbsent(NativeClassroomMembership("native-class",teacher.id,ClassroomMemberRole.TEACHER,now))
        val classes=ClassroomService(classRepo,groups,clock,accounts=accounts)
        val schedules=InMemoryAcademicScheduleOverrideRepository()
        schedules.save(ManagedScheduleBlock("group-101",ScheduleBlock("block","MONDAY","08:00","08:50","native-class","Actual Mathematics",teacher.id,teacher.displayName,groupId="group-101",groupName="101-A",provenance=AcademicProvenance(AcademicDataSource.ADMIN_MANUAL,createdAtEpochSeconds=now.epochSecond,updatedAtEpochSeconds=now.epochSecond)),institutionId=ScopedAcademicFixtures.INSTITUTION,groupScopeRevision=0,revision=1,operationId="schedule",operationDigest="digest"))
        val projection=NativeAcademicProjection(accounts,classes,groups,schedules,clock)
        val presence = createPresence(accounts)
        presence.start(
            "2020-10455",
            StartSchoolPresenceRequest(
                "operation-1", "school-entry", SchoolNetworkEvidence(ssid = "UD4-Alumno"),
                now.epochSecond,
            ),
            student,
        )
        val service = TeacherCampusRosterService(
            ProviderAttendanceOccurrenceResolver(provider), groups, presence, InMemoryAttendanceRepository(),projection,accounts,
        )
        val occurrence = projection.week(teacher,date).occurrences.single()
        val roster = service.forTeacher("T-0001", occurrence.id, date.toString(),teacher)
        assertEquals("101-A", roster.groupName)
        assertEquals(2, roster.students.size)
        assertEquals(listOf("2020-10455", "student-pending"), roster.students.map { it.studentId })
        assertEquals(now.epochSecond, roster.students.first().campusEntryAtEpochSeconds)
        assertEquals(null, roster.students.last().campusEntryAtEpochSeconds)
        assertFails { service.forTeacher("T-OTHER", occurrence.id, date.toString(),teacher) }
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

    private fun createPresence(accounts:PlatformAccountRepository?=null): SchoolPresenceService {
        val qr = MessageDigest.getInstance("SHA-256")
            .digest("school-entry".toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
        return SchoolPresenceService(
            repository = InMemorySchoolPresenceRepository(),
            policy = SchoolPresencePolicy(qr, setOf("UD4-Alumno"), emptySet(),institutionId=ScopedAcademicFixtures.INSTITUTION),
            clock = clock,
            accounts=accounts,
        )
    }
}
