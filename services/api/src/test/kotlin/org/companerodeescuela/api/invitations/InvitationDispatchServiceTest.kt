package org.companerodeescuela.api.invitations

import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.auth.InMemoryPlatformAccountRepository
import org.companerodeescuela.api.auth.PlatformAccount
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.institutions.InMemoryInstitutionRepository
import org.companerodeescuela.api.security.InMemoryMfaRepository
import org.companerodeescuela.api.security.MfaService
import org.companerodeescuela.shared.contracts.InstitutionSummary
import org.companerodeescuela.shared.contracts.UserRole
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InvitationDispatchServiceTest {
    private val now = Instant.parse("2026-11-03T20:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val command =
        InvitationDispatchCommand("student@example.test", "UPTLAX", InvitationProfile.STUDENT, "test-operation-123")

    @Test
    fun `invitation command rejects invalid recipients and is normalized before digest`() {
        assertTrue(!command.copy(recipient = "not-an-email").valid())
        assertTrue(!command.copy(operationId = "x").valid())
        assertEquals(command.digest("owner"), command.copy(recipient = "STUDENT@example.test").canonical().digest("owner"))
    }

    @Test
    fun `only verified owner at enabled institution can prepare sending`() =
        runTest {
            val accounts = InMemoryPlatformAccountRepository()
            val owner =
                PlatformAccount(
                    "owner",
                    "Owner",
                    "owner@example.test",
                    "hash",
                    setOf(UserRole.SUPER_ADMIN),
                    emailVerifiedAt = now.minusSeconds(60),
                )
            val student =
                PlatformAccount("student", "Student", "student@example.test", "hash", setOf(UserRole.STUDENT))
            accounts.create(owner)
            accounts.create(student)
            val service =
                InvitationDispatchService(
                    accounts,
                    MfaService(accounts, InMemoryMfaRepository(), null, clock),
                    InMemoryInvitationRepository(),
                    UnavailableInvitationEmailGateway,
                    InMemoryInstitutionRepository(listOf(InstitutionSummary("UPTLAX", "Universidad Politécnica"))),
                    null,
                    clock,
                )
            assertFailsWith<ApiException.Forbidden> { service.prepare(student, command) }
            assertFailsWith<ApiException.Validation> { service.prepare(owner, command.copy(institutionId = "UNKNOWN")) }
            val prepared = service.prepare(owner, command)
            assertEquals(command.digest(owner.id), prepared.payloadDigest)
            assertEquals(InvitationDispatchService.RESOURCE_ID, prepared.resourceId)
        }

    @Test
    fun `durable retry replays only the bound invitation token and retires it on provider acceptance`() =
        runTest {
            val accounts = InMemoryPlatformAccountRepository()
            val repository = InMemoryInvitationRepository()
            val outbox = InMemoryInvitationOutbox(clock)
            val token = InvitationToken.generate()
            val invitationId = InvitationToken.generate().value
            val createdAt = now.minusSeconds(3600)
            val expiry = now.plusSeconds(3600)
            assertTrue(
                repository.create(
                    InvitationRecord(
                        invitationId,
                        "student@example.test",
                        "UPTLAX",
                        InvitationProfile.STUDENT,
                        "owner",
                        createdAt,
                        expiry,
                        InvitationStatus.SENT,
                        token.hash,
                    ),
                ),
            )
            assertTrue(
                outbox.enqueue(
                    OutboxEntry(
                        invitationId,
                        "student@example.test",
                        token.value,
                        expiry,
                        nextAttemptAt = now.minusSeconds(60),
                        createdAt = createdAt,
                    ),
                ),
            )
            var sends = 0
            val service =
                InvitationDispatchService(
                    accounts,
                    MfaService(accounts, InMemoryMfaRepository(), null, clock),
                    repository,
                    InvitationEmailGateway { recipient, code, id, _ ->
                        assertEquals("student@example.test", recipient)
                        assertEquals(token.value, code)
                        assertEquals(invitationId, id)
                        sends++
                        org.companerodeescuela.shared.contracts.VerificationDeliveryStatus.ACCEPTED
                    },
                    InMemoryInstitutionRepository(),
                    outbox,
                    clock,
                )
            assertEquals(1, service.retryPendingOutbox())
            assertEquals(0, service.retryPendingOutbox())
            assertEquals(1, sends)
            assertTrue(outbox.pending(now).isEmpty())
        }

    @Test
    fun `sending without scoped MFA proof cannot dispatch email`() =
        runTest {
            val accounts = InMemoryPlatformAccountRepository()
            val owner =
                PlatformAccount(
                    "owner",
                    "Owner",
                    "owner@example.test",
                    "hash",
                    setOf(UserRole.SUPER_ADMIN),
                    emailVerifiedAt = now.minusSeconds(60),
                )
            accounts.create(owner)
            var sent = false
            val service =
                InvitationDispatchService(
                    accounts,
                    MfaService(accounts, InMemoryMfaRepository(), null, clock),
                    InMemoryInvitationRepository(),
                    InvitationEmailGateway { _, _, _, _ ->
                        sent = true
                        org.companerodeescuela.shared.contracts.VerificationDeliveryStatus.ACCEPTED
                    },
                    InMemoryInstitutionRepository(listOf(InstitutionSummary("UPTLAX", "UPTLAX"))),
                    null,
                    clock,
                )
            assertFailsWith<ApiException.Forbidden> { service.dispatch(owner, command, "invalid") }
            assertTrue(!sent)
        }
}
