package org.companerodeescuela.api.academic.groups

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.AssignAcademicGroupMemberRequest
import org.companerodeescuela.shared.contracts.CreateAcademicGroupRequest
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary

class AcademicGroupServiceTest {
    @Test
    fun adminCreatesGroupAndAssignsStudent() = runTest {
        val repository = InMemoryAcademicGroupRepository()
        val service = AcademicGroupService(
            repository,
            Clock.fixed(Instant.parse("2026-10-06T17:00:00Z"), ZoneOffset.UTC),
        )

        val group = service.create(ADMIN, CreateAcademicGroupRequest("9a", "9A"))
        val membership = service.assignMember(
            ADMIN,
            group.id,
            AssignAcademicGroupMemberRequest(STUDENT.id),
        )

        assertEquals("9A", group.id)
        assertEquals(STUDENT.id, membership.userId)
        assertEquals(listOf("9A"), service.listFor(STUDENT).map { it.id })
    }

    @Test
    fun teacherCannotCreateOrAssignGroups() = runTest {
        val repository = InMemoryAcademicGroupRepository()
        val service = AcademicGroupService(repository)
        repository.create(
            AcademicGroupRecord(
                id = "9A",
                name = "9A",
                active = true,
                createdAt = Instant.EPOCH,
            ),
        )

        assertFailsWith<ApiException.Forbidden> {
            service.create(TEACHER, CreateAcademicGroupRequest("9B", "9B"))
        }
        assertFailsWith<ApiException.Forbidden> {
            service.assignMember(
                TEACHER,
                "9A",
                AssignAcademicGroupMemberRequest(STUDENT.id),
            )
        }
    }

    private companion object {
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
        val STUDENT = UserSummary(
            id = "student-1",
            displayName = "Ana",
            roles = setOf(UserRole.STUDENT),
        )
    }
}
