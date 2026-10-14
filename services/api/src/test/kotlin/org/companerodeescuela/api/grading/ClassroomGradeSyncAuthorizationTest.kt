package org.companerodeescuela.api.grading

import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.academic.groups.AcademicGroupRecord
import org.companerodeescuela.api.academic.groups.InMemoryAcademicGroupRepository
import org.companerodeescuela.api.auth.InMemoryPlatformAccountRepository
import org.companerodeescuela.api.auth.PlatformAccount
import org.companerodeescuela.api.classroom.ClassroomService
import org.companerodeescuela.api.classroom.InMemoryClassroomRepository
import org.companerodeescuela.api.classroom.NativeClassroom
import org.companerodeescuela.api.classroom.NativeClassroomMembership
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.ClassroomMemberRole
import org.companerodeescuela.shared.contracts.ClassroomStatus
import org.companerodeescuela.shared.contracts.GradeSyncRequest
import org.companerodeescuela.shared.contracts.GradeSyncRow
import org.companerodeescuela.shared.contracts.UserRole
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ClassroomGradeSyncAuthorizationTest {
    private val now = Instant.parse("2026-10-13T12:00:00Z")

    @Test
    fun `assigned teacher may sync only enrolled students in the active classroom`() =
        runTest {
            val (authorization, teacher, _) = fixture()

            authorization.requireCanSync(teacher.toUserSummary(), request("student-a"))

            assertFailsWith<ApiException.Forbidden> {
                authorization.requireCanSync(teacher.toUserSummary(), request("student-outside"))
            }
        }

    @Test
    fun `another teacher cannot sync grades for an assigned classroom`() =
        runTest {
            val (authorization, _, otherTeacher) = fixture()

            assertFailsWith<ApiException.Forbidden> {
                authorization.requireCanSync(otherTeacher.toUserSummary(), request("student-a"))
            }
        }

    private suspend fun fixture(): Triple<ClassroomGradeSyncAuthorization, PlatformAccount, PlatformAccount> {
        val accounts = InMemoryPlatformAccountRepository()
        val teacher =
            PlatformAccount(
                id = "teacher-a",
                displayName = "Assigned Teacher",
                email = "teacher@example.test",
                passwordHash = "hash",
                roles = setOf(UserRole.TEACHER),
                institutionId = "campus-a",
                emailVerifiedAt = now,
                roleGenerations = mapOf(UserRole.TEACHER to 1),
            )
        accounts.create(teacher)
        val otherTeacher = teacher.copy(id = "teacher-b", email = "other@example.test")
        accounts.create(otherTeacher)

        val groups = InMemoryAcademicGroupRepository()
        groups.create(
            AcademicGroupRecord(
                id = "group-a",
                name = "9A",
                active = true,
                createdAt = now,
                institutionId = "campus-a",
                periodId = "2026-2",
                startsAt = now.minusSeconds(60),
                endsAt = now.plusSeconds(3600),
            ),
        )
        val repository = InMemoryClassroomRepository()
        repository.createClassroom(
            NativeClassroom(
                id = "class-a",
                name = "Matemáticas",
                description = null,
                room = null,
                groupId = "group-a",
                groupName = "9A",
                teacherId = teacher.id,
                teacherDisplayName = teacher.displayName,
                status = ClassroomStatus.ACTIVE,
                createdAt = now,
                groupScopeRevision = 0,
                teacherRoleGeneration = 1,
            ),
        )
        repository.addMembershipIfAbsent(
            NativeClassroomMembership("class-a", "student-a", ClassroomMemberRole.STUDENT, now),
        )
        val classrooms =
            ClassroomService(
                repository = repository,
                groupRepository = groups,
                clock = Clock.fixed(now, ZoneOffset.UTC),
                accounts = accounts,
            )
        return Triple(ClassroomGradeSyncAuthorization(classrooms), teacher, otherTeacher)
    }

    private fun request(vararg studentIds: String) =
        GradeSyncRequest(
            classroomId = "class-a",
            gradingPeriod = "Parcial 1",
            rows = studentIds.map { GradeSyncRow(it, 9.0) },
        )
}
