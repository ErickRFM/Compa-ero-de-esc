package org.companerodeescuela.api.admin

import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.auth.InMemoryPlatformAccountRepository
import org.companerodeescuela.api.auth.PlatformAccount
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.UserRole
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CareerAdministrationScopePolicyTest {
    private val now = Instant.parse("2026-11-03T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private fun account(id: String = "career-admin") =
        PlatformAccount(
            id = id,
            displayName = "Coordinación de TI",
            email = "$id@example.test",
            passwordHash = "test-hash",
            roles = setOf(UserRole.COORDINATOR),
            institutionId = "uptlax",
            emailVerifiedAt = now,
        )

    private fun grant(
        accountId: String = "career-admin",
        institutionId: String = "uptlax",
        programId: String = "technology",
        validUntil: Instant? = null,
        revokedAt: Instant? = null,
        authorityFreshUntil: Instant = now.plusSeconds(180),
    ) =
        VerifiedCareerGrant(
            accountId,
            institutionId,
            programId,
            validUntil,
            revokedAt,
            authorityFreshUntil,
        )

    @Test
    fun `scoped administrator is limited to verified live careers in same institution`() =
        runTest {
            val accounts = InMemoryPlatformAccountRepository()
            val manager = account()
            accounts.create(manager)
            val grants = CareerGrantReader { listOf(grant(), grant(programId = "industrial")) }
            val policy = CareerAdministrationScopePolicy(accounts, grants, clock)
            val actor = manager.toUserSummary()

            assertEquals(manager.id, policy.require(actor, "uptlax", "technology").id)
            assertEquals(manager.id, policy.require(actor, "uptlax", "industrial").id)
            assertFailsWith<ApiException.Forbidden> { policy.require(actor, "uptlax", "business") }
            assertFailsWith<ApiException.Forbidden> { policy.require(actor, "other-university", "technology") }
        }

    @Test
    fun `expired revoked stale or cross-account projected grants deny access`() =
        runTest {
            val accounts = InMemoryPlatformAccountRepository()
            val manager = account()
            accounts.create(manager)
            val rejected = listOf(
                grant(accountId = "someone-else"),
                grant(institutionId = "other-university"),
                grant(programId = "business"),
                grant(validUntil = now),
                grant(revokedAt = now),
                grant(authorityFreshUntil = now),
            )
            val policy = CareerAdministrationScopePolicy(accounts, CareerGrantReader { rejected }, clock)
            assertFailsWith<ApiException.Forbidden> { policy.require(manager.toUserSummary(), "uptlax", "technology") }
        }

    @Test
    fun `an empty integration never upgrades students or ordinary institution administrators`() =
        runTest {
            val accounts = InMemoryPlatformAccountRepository()
            val manager = account()
            val student = account("student").copy(roles = setOf(UserRole.STUDENT))
            val admin = account("admin").copy(roles = setOf(UserRole.ADMIN))
            listOf(manager, student, admin).forEach { accounts.create(it) }
            val policy = CareerAdministrationScopePolicy(accounts, CareerGrantReader { emptyList() }, clock)
            for (current in listOf(manager, student, admin)) {
                assertFailsWith<ApiException.Forbidden> {
                    policy.require(current.toUserSummary(), "uptlax", "technology")
                }
            }
        }

    @Test
    fun `forged summary roles stale session or unverified identity cannot get a career`() =
        runTest {
            val accounts = InMemoryPlatformAccountRepository()
            val manager = account()
            accounts.create(manager)
            val policy = CareerAdministrationScopePolicy(accounts, CareerGrantReader { listOf(grant()) }, clock)
            assertFailsWith<ApiException.Unauthorized> {
                policy.require(manager.toUserSummary().copy(roles = setOf(UserRole.SUPER_ADMIN)), "uptlax", "technology")
            }
            assertFailsWith<ApiException.Unauthorized> {
                policy.require(manager.toUserSummary().copy(authRevision = 1), "uptlax", "technology")
            }
            assertFailsWith<ApiException.Forbidden> {
                policy.require(manager.toUserSummary(), "uptlax", "technology\n")
            }
        }

    @Test
    fun `verified provisioned super-admin may administer an institution without fabricated program grants`() =
        runTest {
            val accounts = InMemoryPlatformAccountRepository()
            val owner = account("owner").copy(roles = setOf(UserRole.SUPER_ADMIN), institutionId = null)
            accounts.create(owner)
            val policy = CareerAdministrationScopePolicy(accounts, CareerGrantReader { emptyList() }, clock)
            assertEquals(owner.id, policy.require(owner.toUserSummary(), "other-university", "technology").id)
        }
}
