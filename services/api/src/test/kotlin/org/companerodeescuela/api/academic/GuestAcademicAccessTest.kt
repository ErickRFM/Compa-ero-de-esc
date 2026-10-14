package org.companerodeescuela.api.academic

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
import org.companerodeescuela.shared.contracts.UserRole
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GuestAcademicAccessTest {
    @Test
    fun `guest cannot read classrooms or academic schedule from stale membership`() =
        runTest {
            val now = Instant.parse("2026-10-14T12:00:00Z")
            val clock = Clock.fixed(now, ZoneOffset.UTC)
            val guest =
                PlatformAccount(
                    id = "guest-1",
                    displayName = "Invitado",
                    email = "guest@example.test",
                    passwordHash = "hash",
                    roles = setOf(UserRole.GUEST),
                    institutionId = "campus-a",
                    emailVerifiedAt = now,
                )
            val accounts = InMemoryPlatformAccountRepository()
            accounts.create(guest)
            val groups = InMemoryAcademicGroupRepository()
            groups.create(
                AcademicGroupRecord(
                    id = "group-1",
                    name = "Grupo 1",
                    active = true,
                    createdAt = now,
                    institutionId = "campus-a",
                    periodId = "period-1",
                    startsAt = now.minusSeconds(3600),
                    endsAt = now.plusSeconds(3600),
                ),
            )
            val classrooms = InMemoryClassroomRepository()
            classrooms.createClassroom(
                NativeClassroom(
                    id = "class-1",
                    name = "Matemáticas",
                    description = null,
                    room = "Aula 1",
                    groupId = "group-1",
                    groupName = "Grupo 1",
                    teacherId = "teacher-1",
                    teacherDisplayName = "Docente",
                    status = ClassroomStatus.ACTIVE,
                    createdAt = now,
                    groupScopeRevision = 0,
                ),
            )
            classrooms.addMembershipIfAbsent(
                NativeClassroomMembership(
                    classroomId = "class-1",
                    userId = guest.id,
                    role = ClassroomMemberRole.STUDENT,
                    joinedAt = now,
                ),
            )
            val service = ClassroomService(classrooms, groups, clock, accounts = accounts)
            val projection =
                NativeAcademicProjection(
                    accounts,
                    service,
                    groups,
                    InMemoryAcademicScheduleOverrideRepository(),
                    clock,
                )
            val actor = guest.toUserSummary()

            assertTrue(service.listFor(actor).isEmpty())
            assertFailsWith<ApiException.Forbidden> { service.requireCanRead(actor, "class-1") }
            assertFailsWith<ApiException.Forbidden> { service.join(actor, "unused-invite") }
            assertFailsWith<ApiException.Forbidden> { projection.load(actor) }
            assertFailsWith<ApiException.Forbidden> { projection.week(actor, LocalDate.parse("2026-10-14")) }
        }
}
