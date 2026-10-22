package org.companerodeescuela.api.admin

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.companerodeescuela.api.auth.InMemoryPlatformAccountRepository
import org.companerodeescuela.api.auth.PlatformAccount
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.AdminCapability
import org.companerodeescuela.shared.contracts.UserRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DirectionAccessPolicyTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC)
    private lateinit var accounts: InMemoryPlatformAccountRepository
    private lateinit var policy: DirectionAccessPolicy

    private val principalAccount = PlatformAccount(
        id = "principal-1",
        displayName = "Director General",
        email = "director@escuela.edu",
        passwordHash = "hash",
        roles = setOf(UserRole.PRINCIPAL),
        institutionId = "inst-01",
        accountStatus = AccountStatus.ACTIVE,
        emailVerifiedAt = Instant.parse("2026-10-20T12:00:00Z"),
        adminCapabilities = setOf(AdminCapability.DIRECTIVE_SUPERVISION, AdminCapability.APPROVE_ACADEMIC_OVERRIDES),
    )

    @BeforeEach
    fun setUp() = runBlocking {
        accounts = InMemoryPlatformAccountRepository()
        accounts.create(principalAccount)
        policy = DirectionAccessPolicy(accounts, clock)
    }

    @Test
    fun `valid principal with matching institution and capability succeeds`() = runBlocking {
        val result = policy.requireDirectionScope(
            actor = principalAccount.toUserSummary(),
            requiredCapability = AdminCapability.DIRECTIVE_SUPERVISION,
            targetInstitutionId = "inst-01",
        )
        assertEquals("principal-1", result.id)
    }

    @Test
    fun `principal attempting cross-tenant access is rejected`() = runBlocking {
        assertThrows<ApiException.Forbidden> {
            policy.requireDirectionScope(
                actor = principalAccount.toUserSummary(),
                requiredCapability = AdminCapability.DIRECTIVE_SUPERVISION,
                targetInstitutionId = "inst-other",
            )
        }
    }

    @Test
    fun `principal missing required capability is rejected`() = runBlocking {
        assertThrows<ApiException.Forbidden> {
            policy.requireDirectionScope(
                actor = principalAccount.toUserSummary(),
                requiredCapability = AdminCapability.MANAGE_USERS,
                targetInstitutionId = "inst-01",
            )
        }
    }

    @Test
    fun `teacher without principal role is rejected`() = runBlocking {
        val teacher = PlatformAccount(
            id = "teacher-1",
            displayName = "Docente",
            email = "teacher@escuela.edu",
            passwordHash = "hash",
            roles = setOf(UserRole.TEACHER),
            institutionId = "inst-01",
            accountStatus = AccountStatus.ACTIVE,
            emailVerifiedAt = Instant.parse("2026-10-20T12:00:00Z"),
        )
        accounts.create(teacher)

        assertThrows<ApiException.Forbidden> {
            policy.requireDirectionScope(
                actor = teacher.toUserSummary(),
                requiredCapability = AdminCapability.DIRECTIVE_SUPERVISION,
                targetInstitutionId = "inst-01",
            )
        }
    }

    @Test
    fun `unverified principal email is rejected`() = runBlocking {
        val unverified = principalAccount.copy(emailVerifiedAt = null)
        accounts.create(unverified)

        assertThrows<ApiException.Forbidden> {
            policy.requireDirectionScope(
                actor = unverified.toUserSummary(),
                requiredCapability = AdminCapability.DIRECTIVE_SUPERVISION,
                targetInstitutionId = "inst-01",
            )
        }
    }
}
