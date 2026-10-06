package org.companerodeescuela.api.classroom

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.CreateClassInviteRequest
import org.companerodeescuela.shared.contracts.CreateClassroomRequest
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary

class ClassroomServiceTest {
    @Test
    fun `verified teacher creates class and student joins with temporary code`() = runTest {
        val repository = InMemoryClassroomRepository()
        val service = service(repository, BASE_TIME)
        val classroom = service.create(
            TEACHER,
            CreateClassroomRequest(name = "Programación móvil", room = "Laboratorio A"),
        )
        val invite = service.createInvite(
            TEACHER,
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
    fun `pending teacher cannot create classroom`() = runTest {
        val service = service(InMemoryClassroomRepository(), BASE_TIME)

        assertFailsWith<ApiException.Forbidden> {
            service.create(
                PENDING_TEACHER,
                CreateClassroomRequest(name = "Programación móvil"),
            )
        }
    }

    @Test
    fun `expired invite is rejected`() = runTest {
        val repository = InMemoryClassroomRepository()
        val classroom = service(repository, BASE_TIME).create(
            TEACHER,
            CreateClassroomRequest(name = "Bases de datos"),
        )
        val invite = service(repository, BASE_TIME).createInvite(
            TEACHER,
            classroom.id,
            CreateClassInviteRequest(ttlMinutes = 30),
        )

        assertFailsWith<ApiException.Conflict> {
            service(repository, BASE_TIME.plusSeconds(31 * 60)).join(STUDENT, invite.code)
        }
    }

    @Test
    fun `revoked invite is rejected`() = runTest {
        val repository = InMemoryClassroomRepository()
        val service = service(repository, BASE_TIME)
        val classroom = service.create(
            TEACHER,
            CreateClassroomRequest(name = "Redes"),
        )
        val invite = service.createInvite(TEACHER, classroom.id, CreateClassInviteRequest())
        service.revokeInvite(TEACHER, classroom.id, invite.id)

        assertFailsWith<ApiException.Conflict> {
            service.join(STUDENT, invite.code)
        }
    }

    @Test
    fun `joining same classroom is idempotent and does not require a fresh membership`() = runTest {
        val repository = InMemoryClassroomRepository()
        val service = service(repository, BASE_TIME)
        val classroom = service.create(
            TEACHER,
            CreateClassroomRequest(name = "Ingeniería de software"),
        )
        val invite = service.createInvite(
            TEACHER,
            classroom.id,
            CreateClassInviteRequest(maxUses = 1),
        )

        val first = service.join(STUDENT, invite.code)
        val second = service.join(STUDENT, invite.code)

        assertEquals(first.membership, second.membership)
    }

    private fun service(
        repository: ClassroomRepository,
        instant: Instant,
    ) = ClassroomService(
        repository = repository,
        clock = Clock.fixed(instant, ZoneOffset.UTC),
    )

    private companion object {
        val BASE_TIME: Instant = Instant.parse("2026-10-06T12:00:00Z")

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
