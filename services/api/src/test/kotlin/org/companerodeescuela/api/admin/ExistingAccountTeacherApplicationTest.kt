package org.companerodeescuela.api.admin

import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.academic.groups.InMemoryAcademicGroupRepository
import org.companerodeescuela.api.auth.InMemoryPlatformAccountRepository
import org.companerodeescuela.api.auth.PlatformAccount
import org.companerodeescuela.api.auth.RegistrationIntent
import org.companerodeescuela.api.institutions.InMemoryInstitutionRepository
import org.companerodeescuela.api.test.QaClock
import org.companerodeescuela.api.test.QaMfaOperator
import org.companerodeescuela.api.tutoring.InMemoryTutorAssignmentRepository
import org.companerodeescuela.api.tutoring.TutorAssignmentService
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.ApproveStaffRequest
import org.companerodeescuela.shared.contracts.InstitutionSummary
import org.companerodeescuela.shared.contracts.PrivilegedCommandDigests
import org.companerodeescuela.shared.contracts.RegistrationAccountType
import org.companerodeescuela.shared.contracts.StaffApplicationStatus
import org.companerodeescuela.shared.contracts.StepUpAction
import org.companerodeescuela.shared.contracts.TeacherApplicationRequest
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ExistingAccountTeacherApplicationTest {
    @Test
    fun `verified student applies without new email and only an MFA operator grants teacher authority`() =
        runTest {
            val clock = QaClock(Instant.parse("2026-11-01T12:00:00Z"))
            val accounts = InMemoryPlatformAccountRepository()
            val groups = InMemoryAcademicGroupRepository()
            val operator =
                QaMfaOperator.create(
                    accounts,
                    UserSummary("admin", "QA Admin", roles = setOf(UserRole.ADMIN), institutionId = "campus"),
                    clock,
                )
            val institutions =
                InMemoryInstitutionRepository(listOf(InstitutionSummary("campus", "QA Campus")))
            val service =
                AccountAdministrationService(
                    accounts,
                    institutions,
                    groups,
                    TutorAssignmentService(InMemoryTutorAssignmentRepository(), groups, clock, accounts),
                    operator.mfa,
                    clock,
                )
            val student =
                PlatformAccount(
                    id = "existing-student",
                    displayName = "Existing Account",
                    email = "existing@example.test",
                    passwordHash = "test-only-hash",
                    roles = setOf(UserRole.STUDENT),
                    emailVerifiedAt = clock.now,
                    registrationIntent = RegistrationIntent(RegistrationAccountType.STUDENT),
                )
            accounts.create(student)
            val request =
                TeacherApplicationRequest(
                    operationId = "teacher-conversion-request",
                    expectedAuthRevision = 0,
                    institutionId = "campus",
                    identityReference = "teacher-reference-qa",
                )
            val application = service.requestTeacher(student, request)
            assertEquals(StaffApplicationStatus.PENDING, application.status)
            val pending = assertNotNull(accounts.findById(student.id))
            assertEquals(setOf(UserRole.STUDENT), pending.roles)
            assertEquals(AccountStatus.ACTIVE, pending.accountStatus)
            assertEquals(student.emailVerifiedAt, pending.emailVerifiedAt)
            assertEquals(application.id, service.requestTeacher(student, request).id)

            val approval =
                ApproveStaffRequest(
                    operationId = "approve-teacher",
                    expectedAuthRevision = 0,
                    applicationId = application.id,
                    institutionId = "campus",
                    proof = "",
                )
            val proof =
                operator.proof(
                StepUpAction.APPROVE_STAFF,
                student.id,
                approval.operationId,
                PrivilegedCommandDigests.staffApproval(student.id, approval),
            )
            val result = service.approveStaff(operator.actor, student.id, approval.copy(proof = proof))
            assertEquals(setOf(UserRole.TEACHER), result.user.roles)
            assertEquals("campus", result.user.institutionId)
            assertEquals(StaffApplicationStatus.APPROVED, result.applications.single().status)
            assertEquals(student.emailVerifiedAt, accounts.findById(student.id)?.emailVerifiedAt)
        }
}
