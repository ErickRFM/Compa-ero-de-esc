package org.companerodeescuela.api.invitations

import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.auth.InMemoryPlatformAccountRepository
import org.companerodeescuela.api.auth.PasswordHasher
import org.companerodeescuela.api.auth.PlatformAccount
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.institutions.InMemoryInstitutionRepository
import org.companerodeescuela.api.security.InMemoryMfaRepository
import org.companerodeescuela.api.security.MfaSecretCipher
import org.companerodeescuela.api.security.MfaService
import org.companerodeescuela.api.security.Totp
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.InstitutionSummary
import org.companerodeescuela.shared.contracts.StepUpAction
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.VerificationDeliveryStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InvitationOutboxTest {
    @Test
    fun `manual retry replaces stale pending capability`() =
        runTest {
            val now = Instant.parse("2026-11-09T22:00:00Z")
            val outbox = InMemoryInvitationOutbox()
            val initial =
                OutboxEntry(
                    invitationId = "invitation-123",
                    recipient = "student@example.test",
                    tokenValue = "old-token-value",
                    expiresAt = now.plusSeconds(3_600),
                    nextAttemptAt = now.plusSeconds(60),
                    lastStatus = VerificationDeliveryStatus.PENDING,
                    createdAt = now,
                )
            val retried = initial.copy(tokenValue = "new-token-value", attempts = 0)

            assertTrue(outbox.enqueue(initial))
            assertTrue(outbox.replace(retried))

            val pending = outbox.pending(now.plusSeconds(60))
            assertEquals(1, pending.size)
            assertEquals("new-token-value", pending.single().tokenValue)

            assertTrue(outbox.clearToken("invitation-123"))
            assertTrue(outbox.pending(now.plusSeconds(60)).isEmpty())
        }

    @Test
    fun `outbox enqueue exception revokes created invitation instead of stranding un-emailed record`() =
        runTest {
            val now = Instant.parse("2026-11-26T18:00:00Z")
            val clock = Clock.fixed(now, ZoneOffset.UTC)
            val accounts = InMemoryPlatformAccountRepository()
            val invitations = InMemoryInvitationRepository()
            val institutions = InMemoryInstitutionRepository(listOf(InstitutionSummary("UPTLAX", "UPTLAX")))
            val secretPass = "owner-outbox-secret-123"
            val owner =
                PlatformAccount(
                    id = "owner-outbox",
                    displayName = "Owner",
                    email = "owner@example.test",
                    passwordHash = PasswordHasher().hash(secretPass),
                    roles = setOf(UserRole.SUPER_ADMIN),
                    accountStatus = AccountStatus.ACTIVE,
                    emailVerifiedAt = now.minusSeconds(60),
                    institutionId = "UPTLAX",
                )
            accounts.create(owner)
            val sharedMfaRepo = InMemoryMfaRepository()
            val cipherKey = ByteArray(32) { (it + 1).toByte() }
            val sharedMfaEnroll = MfaService(accounts, sharedMfaRepo, MfaSecretCipher(cipherKey), clock)
            val sharedEnr = sharedMfaEnroll.beginEnrollment(owner, secretPass)
            val sharedSec = Totp.decodeSecret(sharedEnr.secret)
            sharedMfaEnroll.confirmEnrollment(owner, sharedEnr.enrollmentId, Totp.code(sharedSec, now.epochSecond))

            val brokenOutbox =
                object : InvitationOutbox {
                    override suspend fun enqueue(entry: OutboxEntry): Boolean = error("Simulated Mongo outbox failure")

                    override suspend fun pending(now: Instant): List<OutboxEntry> = emptyList()

                    override suspend fun markDelivered(
                        id: String,
                        now: Instant,
                    ): Boolean = false

                    override suspend fun markFailed(
                        id: String,
                        now: Instant,
                    ): Boolean = false

                    override suspend fun scheduleRetry(
                        id: String,
                        nextAttemptAt: Instant,
                        now: Instant,
                    ): Boolean = false

                    override suspend fun clearToken(id: String): Boolean = false
                }

            val finalClock = Clock.fixed(now.plusSeconds(45), ZoneOffset.UTC)
            val sharedMfaStep = MfaService(accounts, sharedMfaRepo, MfaSecretCipher(cipherKey), finalClock)

            val service =
                InvitationDispatchService(
                    accounts = accounts,
                    mfa = sharedMfaStep,
                    repository = invitations,
                    email = InvitationEmailGateway { _, _, _, _ -> VerificationDeliveryStatus.ACCEPTED },
                    institutions = institutions,
                    outbox = brokenOutbox,
                    clock = finalClock,
                )
            val cmd = InvitationDispatchCommand("e5@x.test", "UPTLAX", InvitationProfile.TEACHER, "op-rollback-12345678")
            val prep = service.prepare(owner, cmd)
            val proof =
                sharedMfaStep.issueStepUp(
                    owner,
                    secretPass,
                    Totp.code(sharedSec, finalClock.instant().epochSecond),
                    StepUpAction.ADMIN_INVITE,
                    prep.resourceId,
                    prep.operationId,
                    prep.payloadDigest,
                )

            assertFailsWith<ApiException.DependencyUnavailable> {
                service.dispatch(owner, cmd, proof.proof)
            }
            val saved = invitations.listRecent(limit = 10)
            assertEquals(1, saved.size)
            assertEquals(InvitationStatus.REVOKED, saved.single().status)
        }
}
