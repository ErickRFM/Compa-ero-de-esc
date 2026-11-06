package org.companerodeescuela.api.invitations

import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.auth.AccountIdentityChange
import org.companerodeescuela.api.auth.InMemoryPlatformAccountRepository
import org.companerodeescuela.api.auth.PlatformAccount
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.UserRole
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InvitationAcceptanceServiceTest {
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test fun `invalid, expired, or revoked invitations never reach acceptance`() =
        runTest {
            val accounts = InMemoryPlatformAccountRepository()
            val actor = PlatformAccount("user", "User", "user@example.test", "hash", setOf(UserRole.STUDENT), emailVerifiedAt = now)
            assertTrue(accounts.create(actor))
            val invitations = InMemoryInvitationRepository()
            val token = InvitationToken.generate()
            assertTrue(
                invitations.create(
                    InvitationRecord(
                        "invite",
                        actor.email,
                        "campus",
                        InvitationProfile.STUDENT,
                        "issuer",
                        now,
                        now.plusSeconds(60),
                        InvitationStatus.SENT,
                        token.hash,
                    ),
                ),
            )
            val service = InvitationAcceptanceService(accounts, invitations, clock)
            assertFailsWith<ApiException.Validation> { service.accept(actor, "invite", "bad", "op") }
            val expired = InvitationAcceptanceService(accounts, invitations, Clock.fixed(now.plusSeconds(60), ZoneOffset.UTC))
            assertFailsWith<ApiException.Validation> { expired.accept(actor, "invite", token.value, "op") }
            assertTrue(invitations.revoke("invite", now))
            assertFailsWith<ApiException.Validation> { service.accept(actor, "invite", token.value, "op") }
        }

    @Test fun `server hashes raw code and acceptance never grants staff authority`() =
        runTest {
            val accounts = InMemoryPlatformAccountRepository()
            val actor =
                PlatformAccount("student", "Student", "student@example.test", "hash", setOf(UserRole.STUDENT), emailVerifiedAt = now)
            assertTrue(accounts.create(actor))
            val invitations = InMemoryInvitationRepository()
            val token = InvitationToken.generate()
            assertTrue(
                invitations.create(
                    InvitationRecord(
                        "invite",
                        actor.email,
                        "campus",
                        InvitationProfile.TEACHER,
                        "issuer",
                        now,
                        now.plusSeconds(600),
                        InvitationStatus.SENT,
                        token.hash,
                    ),
                ),
            )
            val service = InvitationAcceptanceService(accounts, invitations, clock)
            assertFailsWith<ApiException.Validation> { service.accept(actor, "invite", token.hash, "op") }
            val result = service.accept(actor, "invite", token.value, "op")
            assertEquals(InvitationStatus.ACCEPTED, result.status)
            assertEquals(setOf(UserRole.STUDENT), accounts.findById(actor.id)?.roles)
            assertTrue(!result.toString().contains(token.value) && !result.toString().contains(token.hash))
        }

    @Test fun `stale, suspended, unverified, or different recipient cannot accept`() =
        runTest {
            val accounts = InMemoryPlatformAccountRepository()
            val owner = PlatformAccount("owner", "Owner", "owner@example.test", "hash", setOf(UserRole.STUDENT), emailVerifiedAt = now)
            val other = PlatformAccount("other", "Other", "other@example.test", "hash", setOf(UserRole.STUDENT), emailVerifiedAt = now)
            val unverified = PlatformAccount("unverified", "User", "unverified@example.test", "hash", setOf(UserRole.STUDENT))
            assertTrue(accounts.create(owner))
            assertTrue(accounts.create(other))
            assertTrue(accounts.create(unverified))
            val invites = InMemoryInvitationRepository()
            val token = InvitationToken.generate()
            assertTrue(
                invites.create(
                    InvitationRecord(
                        "invite",
                        owner.email,
                        "campus",
                        InvitationProfile.INSTITUTION_ADMIN,
                        "issuer",
                        now,
                        now.plusSeconds(600),
                        InvitationStatus.SENT,
                        token.hash,
                    ),
                ),
            )
            val service = InvitationAcceptanceService(accounts, invites, clock)
            assertFailsWith<ApiException.Validation> { service.accept(other, "invite", token.value, "other-op") }
            assertFailsWith<ApiException.Forbidden> { service.accept(unverified, "invite", token.value, "unverified-op") }
            accounts.changeIdentity(owner.id, 0, AccountIdentityChange("suspend", "admin", AccountStatus.SUSPENDED, owner.roles, null, now))
            assertFailsWith<ApiException.Unauthorized> { service.accept(owner, "invite", token.value, "stale-op") }
        }

    @Test fun `verified pending teacher can accept but still has no staff authority`() =
        runTest {
            val accounts = InMemoryPlatformAccountRepository()
            val candidate =
                PlatformAccount(
                    "pending",
                    "Teacher",
                    "teacher@example.test",
                    "hash",
                    emptySet(),
                    accountStatus = AccountStatus.PENDING_APPROVAL,
                    emailVerifiedAt = now,
                )
            assertTrue(accounts.create(candidate))
            val invites = InMemoryInvitationRepository()
            val token = InvitationToken.generate()
            assertTrue(
                invites.create(
                    InvitationRecord(
                        "pending-invite",
                        candidate.email,
                        "campus",
                        InvitationProfile.TEACHER,
                        "issuer",
                        now,
                        now.plusSeconds(600),
                        InvitationStatus.SENT,
                        token.hash,
                    ),
                ),
            )
            val receipt = InvitationAcceptanceService(accounts, invites, clock).accept(candidate, "pending-invite", token.value, "op")
            assertEquals(InvitationStatus.ACCEPTED, receipt.status)
            assertEquals(AccountStatus.PENDING_APPROVAL, accounts.findById(candidate.id)?.accountStatus)
            assertEquals(emptySet(), accounts.findById(candidate.id)?.roles)
        }
}
