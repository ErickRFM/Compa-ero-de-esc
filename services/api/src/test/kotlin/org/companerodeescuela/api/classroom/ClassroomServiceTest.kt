package org.companerodeescuela.api.classroom

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.academic.groups.AcademicGroupMembershipRecord
import org.companerodeescuela.api.academic.groups.AcademicGroupRecord
import org.companerodeescuela.api.academic.groups.InMemoryAcademicGroupRepository
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.CreateClassInviteRequest
import org.companerodeescuela.shared.contracts.CreateClassroomRequest
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary

class ClassroomServiceTest {
    @Test
    fun adminCreatesClassAndAssignedTeacherReceivesMembership() = runTest {
        val repository = InMemoryClassroomRepository()
        val service = service(repository, BASE_TIME)

        val classroom = service.create(ADMIN, request())

        assertEquals("9A", classroom.groupName)
        assertEquals(TEACHER.id, classroom.teacherId)
        assertTrue(classroom.canManageEnrollment)

        val teacherClasses = service.listFor(TEACHER)
        assertEquals(1, teacherClasses.size)
        assertEquals(classroom.id, teacherClasses.single().id)
        assertTrue(teacherClasses.single().canManage)
        assertFalse(teacherClasses.single().canManageEnrollment)
    }

    @Test
    fun teacherCannotCreateClassroom() = runTest {
        val service = service(InMemoryClassroomRepository(), BASE_TIME)

        assertFailsWith<ApiException.Forbidden> {
            service.create(TEACHER, request())
        }
    }

    @Test
    fun pendingTeacherCannotCreateClassroom() = runTest {
        val service = service(InMemoryClassroomRepository(), BASE_TIME)

        assertFailsWith<ApiException.Forbidden> {
            service.create(PENDING_TEACHER, request())
        }
    }

    @Test
    fun teacherCannotGenerateEnrollmentCode() = runTest {
        val service = service(InMemoryClassroomRepository(), BASE_TIME)
        val classroom = service.create(ADMIN, request())

        assertFailsWith<ApiException.Forbidden> {
            service.createInvite(
                TEACHER,
                classroom.id,
                CreateClassInviteRequest(ttlMinutes = 30, maxUses = 10),
            )
        }
    }

    @Test
    fun adminEnrollmentCodeLetsStudentJoinAssignedClass() = runTest {
        val repository = InMemoryClassroomRepository()
        val service = service(repository, BASE_TIME)
        val classroom = service.create(ADMIN, request())
        val invite = service.createInvite(
            ADMIN,
            classroom.id,
            CreateClassInviteRequest(ttlMinutes = 30, maxUses = 10),
        )

        assertTrue(invite.code.matches(Regex("[A-Z2-9]{4}-[A-Z2-9]{4}")))
        val joined = service.join(STUDENT, invite.code)

        assertEquals(classroom.id, joined.classroom.id)
        assertEquals(STUDENT.id, joined.membership.userId)
        assertEquals(1, service.listFor(STUDENT).size)
    }

    @Test
    fun expiredEnrollmentCodeIsRejected() = runTest {
        val repository = InMemoryClassroomRepository()
        val classroom = service(repository, BASE_TIME).create(ADMIN, request(name = "Bases de datos"))
        val invite = service(repository, BASE_TIME).createInvite(
            ADMIN,
            classroom.id,
            CreateClassInviteRequest(ttlMinutes = 30),
        )

        assertFailsWith<ApiException.Conflict> {
            service(repository, BASE_TIME.plusSeconds(31 * 60)).join(STUDENT, invite.code)
        }
    }

    @Test
    fun revokedEnrollmentCodeIsRejected() = runTest {
        val repository = InMemoryClassroomRepository()
        val service = service(repository, BASE_TIME)
        val classroom = service.create(ADMIN, request(name = "Redes"))
        val invite = service.createInvite(ADMIN, classroom.id, CreateClassInviteRequest())
        service.revokeInvite(ADMIN, classroom.id, invite.id)

        assertFailsWith<ApiException.Conflict> {
            service.join(STUDENT, invite.code)
        }
    }

    @Test
    fun joiningSameClassroomIsIdempotent() = runTest {
        val repository = InMemoryClassroomRepository()
        val service = service(repository, BASE_TIME)
        val classroom = service.create(ADMIN, request(name = "Ingeniería de software"))
        val invite = service.createInvite(
            ADMIN,
            classroom.id,
            CreateClassInviteRequest(maxUses = 1),
        )

        val first = service.join(STUDENT, invite.code)
        val second = service.join(STUDENT, invite.code)

        assertEquals(first.membership, second.membership)
    }


    @Test
    fun studentInGroupAutomaticallySeesAssignedClasses() = runTest {
        val classrooms = InMemoryClassroomRepository()
        val groups = InMemoryAcademicGroupRepository()
        groups.create(
            AcademicGroupRecord(
                id = "group-9a",
                name = "9A",
                active = true,
                createdAt = BASE_TIME,
            ),
        )
        groups.assign(
            AcademicGroupMembershipRecord(
                groupId = "group-9a",
                userId = STUDENT.id,
                joinedAt = BASE_TIME,
            ),
        )
        val service = ClassroomService(
            repository = classrooms,
            groupRepository = groups,
            clock = Clock.fixed(BASE_TIME, ZoneOffset.UTC),
        )
        val classroom = service.create(ADMIN, request())

        val visible = service.listFor(STUDENT)

        assertEquals(listOf(classroom.id), visible.map { it.id })
        assertEquals(classroom.id, service.requireCanRead(STUDENT, classroom.id).id)
    }

    private fun request(name: String = "Programación móvil") = CreateClassroomRequest(
        name = name,
        room = "Laboratorio A",
        groupId = "group-9a",
        groupName = "9A",
        teacherId = TEACHER.id,
        teacherDisplayName = TEACHER.displayName,
    )

    private fun service(
        repository: ClassroomRepository,
        instant: Instant,
    ) = ClassroomService(
        repository = repository,
        clock = Clock.fixed(instant, ZoneOffset.UTC),
    )

    private companion object {
        val BASE_TIME: Instant = Instant.parse("2026-10-06T12:00:00Z")

        val ADMIN = UserSummary(
            id = "admin-1",
            displayName = "Control escolar",
            roles = setOf(UserRole.ADMIN),
        )
        val TEACHER = UserSummary(
            id = "teacher-1",
            displayName = "Mtra. Elena",
            roles = setOf(UserRole.TEACHER),
        )
        val PENDING_TEACHER = UserSummary(
            id = "teacher-pending",
            displayName = "Docente pendiente",
            roles = setOf(UserRole.TEACHER_PENDING),
        )
        val STUDENT = UserSummary(
            id = "student-1",
            displayName = "Ana",
            roles = setOf(UserRole.STUDENT),
        )
    }
}
