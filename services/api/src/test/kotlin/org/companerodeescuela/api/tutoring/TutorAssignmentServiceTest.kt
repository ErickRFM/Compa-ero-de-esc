package org.companerodeescuela.api.tutoring

import org.companerodeescuela.api.test.ScopedAcademicFixtures
import org.companerodeescuela.api.test.QaMfaOperator
import org.companerodeescuela.api.test.QaClock
import org.companerodeescuela.shared.contracts.RevokeTutorAssignmentRequest
import org.companerodeescuela.shared.contracts.PrivilegedCommandDigests
import org.companerodeescuela.shared.contracts.StepUpAction
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.academic.groups.AcademicGroupRecord
import org.companerodeescuela.api.academic.groups.AcademicGroupMembershipRecord
import org.companerodeescuela.api.academic.groups.InMemoryAcademicGroupRepository
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.auth.InMemoryPlatformAccountRepository
import org.companerodeescuela.api.auth.PlatformAccount
import org.companerodeescuela.shared.contracts.CreateTutorAssignmentRequest
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary

class TutorAssignmentServiceTest {
    @Test
    fun adminAssignsTutorAndTutorSeesOnlyAssignedScope() = runTest {
        val groups = InMemoryAcademicGroupRepository()
        groups.create(ScopedAcademicFixtures.group("6A"))
        groups.create(ScopedAcademicFixtures.group("6B"))
        val accounts = InMemoryPlatformAccountRepository()
        accounts.create(ScopedAcademicFixtures.account(TUTOR))
        accounts.create(PlatformAccount("student-1", "Ana", "ana@example.edu", "test-only", setOf(UserRole.STUDENT)))
        groups.assign(AcademicGroupMembershipRecord("6A", "student-1", Instant.EPOCH))
        val operator=QaMfaOperator.create(accounts,ADMIN,QaClock(Instant.parse("2026-10-06T18:30:00Z")))
        val service = TutorAssignmentService(
            repository = InMemoryTutorAssignmentRepository(),
            groupRepository = groups,
            clock = operator.clock,
            accounts = accounts, mfa = operator.mfa,
        )

        val command=assignmentRequest(TUTOR.id,"6A","assign")
        service.create(ADMIN,command.copy(proof=operator.proof(StepUpAction.MANAGE_ACADEMICS,"6A","assign",PrivilegedCommandDigests.tutorAssignment("6A",command))))

        assertEquals(listOf("6A"), service.scopeFor(TUTOR).groups.map { it.id })
        assertEquals(1, service.listFor(TUTOR).size)
        val roster = service.studentsForGroup(TUTOR, "6A")
        assertEquals(listOf("Ana"), roster.map { it.displayName })
        assertFailsWith<ApiException.Forbidden> { service.studentsForGroup(TUTOR, "6B") }
        service.requireCanAccessGroup(TUTOR, "6A")
        assertFailsWith<ApiException.Forbidden> {
            service.requireCanAccessGroup(TUTOR, "6B")
        }
        val assignment = service.listFor(TUTOR).single()
        var revoke=RevokeTutorAssignmentRequest("revoke","")
        revoke=revoke.copy(proof=operator.proof(StepUpAction.MANAGE_ACADEMICS,assignment.id,revoke.operationId,PrivilegedCommandDigests.tutorRevocation(assignment.id,revoke)))
        val revoked = service.revoke(ADMIN, assignment.id,revoke)
        assertEquals(false, revoked.active)
        assertEquals(ADMIN.id, revoked.revokedBy)
        assertEquals(emptyList(), service.scopeFor(TUTOR).groups)
        assertFailsWith<ApiException.Forbidden> {
            service.requireCanAccessGroup(TUTOR, "6A")
        }
        assertEquals(revoked, service.revoke(ADMIN, assignment.id,revoke))
    }

    @Test
    fun adminRejectsUnknownOrNonTutorIdentity() = runTest {
        val groups = InMemoryAcademicGroupRepository()
        groups.create(ScopedAcademicFixtures.group("6A"))
        val accounts = InMemoryPlatformAccountRepository()
        accounts.create(ScopedAcademicFixtures.account(TEACHER))
        val operator=QaMfaOperator.create(accounts,ADMIN)
        val service = TutorAssignmentService(InMemoryTutorAssignmentRepository(), groups,operator.clock,accounts,operator.mfa)
        assertFailsWith<ApiException.NotFound> {
            val command=assignmentRequest("unknown","6A","unknown-target")
            service.create(ADMIN,command.copy(proof=operator.proof(StepUpAction.MANAGE_ACADEMICS,"6A","unknown-target",PrivilegedCommandDigests.tutorAssignment("6A",command))))
        }
        assertFailsWith<ApiException.Validation> {
            val command=assignmentRequest(TEACHER.id,"6A","non-tutor-target")
            service.create(ADMIN,command.copy(proof=operator.proof(StepUpAction.MANAGE_ACADEMICS,"6A","non-tutor-target",PrivilegedCommandDigests.tutorAssignment("6A",command))))
        }
    }

    @Test
    fun teacherWithoutTutorRoleCannotReadTutorScope() = runTest {
        val service = TutorAssignmentService(
            repository = InMemoryTutorAssignmentRepository(),
            groupRepository = InMemoryAcademicGroupRepository(),
        )

        assertFailsWith<ApiException.Forbidden> {
            service.scopeFor(TEACHER)
        }
    }

    @Test
    fun nonAdminCannotAssignTutor() = runTest {
        val groups = InMemoryAcademicGroupRepository()
        groups.create(ScopedAcademicFixtures.group("6A"))
        val service = TutorAssignmentService(
            repository = InMemoryTutorAssignmentRepository(),
            groupRepository = groups,
        )

        assertFailsWith<ApiException.Forbidden> {
            service.create(TUTOR, CreateTutorAssignmentRequest(TUTOR.id, "6A"))
        }
    }

    private fun assignmentRequest(tutor:String,group:String,operation:String)=CreateTutorAssignmentRequest(tutor,group,
        ScopedAcademicFixtures.INSTITUTION,ScopedAcademicFixtures.PERIOD,0,ScopedAcademicFixtures.END.epochSecond,operation,null)

    private companion object {
        val ADMIN = UserSummary(
            id = "admin-1",
            displayName = "Control escolar",
            roles = setOf(UserRole.ADMIN),
            institutionId = ScopedAcademicFixtures.INSTITUTION,
        )
        val TUTOR = UserSummary(
            id = "teacher-tutor-1",
            displayName = "Mtra. Elena",
            roles = setOf(UserRole.TEACHER, UserRole.TUTOR),
            institutionId = ScopedAcademicFixtures.INSTITUTION,
        )
        val TEACHER = UserSummary(
            id = "teacher-1",
            displayName = "Mtro. Luis",
            roles = setOf(UserRole.TEACHER),
            institutionId = ScopedAcademicFixtures.INSTITUTION,
        )
    }
}
