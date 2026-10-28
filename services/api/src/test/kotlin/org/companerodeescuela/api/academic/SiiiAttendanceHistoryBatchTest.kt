package org.companerodeescuela.api.academic

import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.academic.groups.InMemoryAcademicGroupRepository
import org.companerodeescuela.api.attendance.AttendanceEnrollmentResolver
import org.companerodeescuela.api.attendance.AttendanceStudentService
import org.companerodeescuela.api.attendance.InMemoryAttendanceRepository
import org.companerodeescuela.api.attendance.SiiiAttendanceAuthority
import org.companerodeescuela.api.classroom.ClassroomService
import org.companerodeescuela.api.classroom.InMemoryClassroomRepository
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.mock.MockAcademicProvider
import org.companerodeescuela.shared.contracts.AttendanceIdentitySource
import org.companerodeescuela.shared.contracts.AttendanceReasonCode
import org.companerodeescuela.shared.contracts.AttendanceRecordResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionStatus
import org.companerodeescuela.shared.contracts.AttendanceStatus
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SiiiAttendanceHistoryBatchTest {
    @Test
    fun `one hundred historical meetings share bounded fresh authority reads and still reject revocation and outages`() =
        runTest {
            val f = SiiiAcademicRoutesTest.Fixture().also { it.initialize() }
            val groups = InMemoryAcademicGroupRepository()
            val classrooms = ClassroomService(InMemoryClassroomRepository(), groups, accounts = f.accounts)
            val authority = SiiiAttendanceAuthority(f.service, f.reader, f.links, f.identities, f.accounts)
            val projection =
                NativeAcademicProjection(
                    f.accounts,
                    classrooms,
                    groups,
                    InMemoryAcademicScheduleOverrideRepository(),
                    institutionalAttendance = authority,
                )
            val repository = InMemoryAttendanceRepository()
            val occurrences =
                (0..99).map {
                    f.snapshot.occurrences
                        .single()
                        .copy(sourceId = UUID.randomUUID().toString())
                }
            f.stored = f.stored.copy(snapshot = f.snapshot.copy(occurrences = occurrences))
            for ((index, occurrence) in occurrences.withIndex()) {
                val id = "session-$index"
                repository.createSession(
                    AttendanceSessionResponse(
                        id,
                        occurrence.sourceId,
                        f.sectionId,
                        f.snapshot.sectionCode,
                        occurrence.date,
                        occurrence.startsAt,
                        occurrence.endsAt,
                        "teacher",
                        1,
                        2,
                        status = AttendanceSessionStatus.CLOSED,
                        closedAtEpochSeconds = 2,
                        identitySource = AttendanceIdentitySource.NATIVE,
                        institutionId = "tenant-a",
                        groupId = f.sectionId,
                        groupScopeRevision = 1,
                        academicSource = SiiiAttendanceAuthority.SOURCE,
                    ),
                )
                repository.writeAttempt(
                    AttendanceRecordResponse(
                        "record-$index",
                        "operation-$index",
                        id,
                        occurrence.sourceId,
                        "student",
                        AttendanceStatus.REVIEW_REQUIRED,
                        AttendanceReasonCode.CLASS_CALL_CONFIRMED,
                        index.toLong(),
                        index.toLong(),
                    ),
                )
            }
            val students =
                AttendanceStudentService(
                    repository,
                    AttendanceEnrollmentResolver(MockAcademicProvider(), classrooms, authority),
                    nativeProjection = projection,
                )
            val student = f.accounts.findById("student")!!.toUserSummary()
            f.authorityReads = 0
            assertEquals(100, students.myHistory(student.id, student).size)
            assertTrue(f.authorityReads <= 15, "One section must not fetch one manifest per historical session; got ${f.authorityReads}")
            f.sections = emptyList()
            assertEquals(emptyList(), students.myHistory(student.id, student))
            f.sections =
                listOf(
                    org.companerodeescuela.api.integrations.academic.siii
                        .SiiiAuthoritySection(f.sectionId, 1),
                )
            f.onAuthorityRead = { throw ApiException.DependencyUnavailable("Synthetic source outage") }
            assertFailsWith<ApiException.DependencyUnavailable> { students.myHistory(student.id, student) }
            f.authorityReads = 0
            f.onAuthorityRead = { if (f.authorityReads >= 7) f.sections = emptyList() }
            assertFailsWith<ApiException.DependencyUnavailable> { students.myHistory(student.id, student) }
        }
}
