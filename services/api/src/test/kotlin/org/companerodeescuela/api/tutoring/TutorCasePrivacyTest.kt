package org.companerodeescuela.api.tutoring

import java.time.Instant
import org.companerodeescuela.api.test.ScopedAcademicFixtures
import org.companerodeescuela.api.auth.*
import org.companerodeescuela.shared.contracts.AccountStatus
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.companerodeescuela.api.academic.groups.AcademicGroupMembershipRecord
import org.companerodeescuela.api.academic.groups.AcademicGroupRecord
import org.companerodeescuela.api.academic.groups.InMemoryAcademicGroupRepository
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.AddTutorCaseNoteRequest
import org.companerodeescuela.shared.contracts.CreateTutorCaseRequest
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary

class TutorCasePrivacyTest {
    private val tutor = UserSummary("assigned-tutor", "Tutor", roles = setOf(UserRole.TUTOR), institutionId = ScopedAcademicFixtures.INSTITUTION)
    private val request = CreateTutorCaseRequest("9A", "student-1", "Academic support")

    @Test
    fun `administrative role alone cannot read or write private cases`() = runTest {
        val fixture = fixture()
        val record = fixture.service.create(tutor, request)
        fixture.service.addNote(tutor, record.id, AddTutorCaseNoteRequest("Private observation"))
        for (roles in listOf(setOf(UserRole.ADMIN), setOf(UserRole.SUPER_ADMIN), setOf(UserRole.ADMIN, UserRole.TUTOR))) {
            val actor = UserSummary("unassigned-admin-"+roles.joinToString("-") { it.name.lowercase() }, "Admin", roles = roles, institutionId = ScopedAcademicFixtures.INSTITUTION)
            fixture.accounts.create(ScopedAcademicFixtures.account(actor))
            assertFailsWith<ApiException.Forbidden> { fixture.service.detail(actor, record.id) }
            assertFailsWith<ApiException.Forbidden> { fixture.service.addNote(actor, record.id, AddTutorCaseNoteRequest("Unauthorized observation")) }
            assertFailsWith<ApiException.Forbidden> { fixture.service.create(actor, request) }
            if (UserRole.TUTOR in roles) assertEquals(emptyList(), fixture.service.list(actor))
            else assertFailsWith<ApiException.Forbidden> { fixture.service.list(actor) }
        }
        assertEquals(1, fixture.service.detail(tutor, record.id).notes.size)
    }

    @Test
    fun `assignment revocation denies all case operations and preserves evidence`() = runTest {
        val fixture = fixture()
        val record = fixture.service.create(tutor, request)
        fixture.service.addNote(tutor, record.id, AddTutorCaseNoteRequest("Existing observation"))
        val dualRole = assertNotNull(fixture.accounts.changeIdentity(tutor.id, 0, AccountIdentityChange("grant-test-dual-role", "qa-owner", AccountStatus.ACTIVE, setOf(UserRole.TUTOR, UserRole.SUPER_ADMIN), ScopedAcademicFixtures.INSTITUTION, Instant.now()))).toUserSummary()
        assertEquals(record.id, fixture.service.list(dualRole).single().id)
        fixture.assignments.revoke("assignment-1", "admin", Instant.now())
        assertEquals(emptyList(), fixture.service.list(dualRole))
        assertFailsWith<ApiException.Forbidden> { fixture.service.detail(dualRole, record.id) }
        assertFailsWith<ApiException.Forbidden> { fixture.service.addNote(dualRole, record.id, AddTutorCaseNoteRequest("After revocation")) }
        assertFailsWith<ApiException.Forbidden> { fixture.service.create(dualRole, request) }
        assertEquals("Existing observation", fixture.cases.find(record.id)?.notes?.single()?.body)
    }

    @Test
    fun `inactive identity cannot use retained tutor assignment`() = runTest {
        val fixture = fixture()
        val record = fixture.service.create(tutor, request)
        val inactive = tutor.copy(active = false)
        assertFailsWith<ApiException.Unauthorized> { fixture.service.list(inactive) }
        assertFailsWith<ApiException.Unauthorized> { fixture.service.detail(inactive, record.id) }
    }

    private data class Fixture(val service: TutorCaseService, val assignments: InMemoryTutorAssignmentRepository, val cases: InMemoryTutorCaseRepository, val accounts: InMemoryPlatformAccountRepository)

    private suspend fun fixture(): Fixture {
        val groups = InMemoryAcademicGroupRepository()
        groups.create(ScopedAcademicFixtures.group("9A", "9 A"))
        groups.assign(AcademicGroupMembershipRecord("9A", "student-1", Instant.EPOCH))
        val tutors = InMemoryTutorAssignmentRepository()
        tutors.create(ScopedAcademicFixtures.assignment("assignment-1", tutor.id, "9A"))
        val cases = InMemoryTutorCaseRepository()
        val accounts = ScopedAcademicFixtures.accounts(tutor,UserSummary("student-1","Student",roles=setOf(UserRole.STUDENT),institutionId=ScopedAcademicFixtures.INSTITUTION))
        return Fixture(TutorCaseService(cases, TutorAssignmentService(tutors, groups, accounts = accounts), groups), tutors, cases, accounts)
    }
}
