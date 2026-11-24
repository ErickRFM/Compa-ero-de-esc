package org.companerodeescuela.api.invitations

import kotlinx.coroutines.runBlocking
import org.companerodeescuela.api.auth.InMemoryOwnerInvitationRepository
import org.companerodeescuela.shared.contracts.VerificationDeliveryStatus
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OwnerInviteEmailDispatchTest {
    private val now = Instant.parse("2026-11-11T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val code = "A".repeat(43)
    private val token = "B".repeat(43)
    private val digest =
        MessageDigest
            .getInstance("SHA-256")
            .digest(code.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    @Test
    fun `valid one time owner invitation is sent through owner email and remains consumable`() =
        runBlocking {
            var sentCode: String? = null
            var ownerTemplateUsed = false
            val email =
                object : InvitationEmailGateway {
                    override suspend fun send(
                        recipient: String,
                        code: String,
                        invitationId: String,
                        expiresAt: Instant,
                    ) = VerificationDeliveryStatus.ACCEPTED

                    override suspend fun sendOwnerBootstrap(
                        recipient: String,
                        code: String,
                        invitationId: String,
                        expiresAt: Instant,
                    ): VerificationDeliveryStatus {
                        sentCode = code
                        ownerTemplateUsed = true
                        assertEquals("erik55509@hotmail.com", recipient)
                        assertTrue(invitationId.matches(Regex("[A-Za-z0-9_-]{43}")))
                        return VerificationDeliveryStatus.ACCEPTED
                    }
                }
            val repository = InMemoryOwnerInvitationRepository()
            val service = OwnerInviteEmailDispatchService(repository, email, clock)

            assertEquals(OwnerInviteDispatchOutcome.ACCEPTED, service.dispatch(token, config()))
            assertEquals(code, sentCode)
            assertTrue(ownerTemplateUsed)
            assertEquals(
                OwnerInviteDispatchOutcome.ALREADY_ATTEMPTED,
                service.dispatch(token, config()),
            )
            assertTrue(repository.consume(digest, "erik55509@hotmail.com", now.plusSeconds(3600), now, "account"))
        }

    @Test
    fun `wrong token digest or expired invitation never sends`() =
        runBlocking {
            var sends = 0
            val service =
                OwnerInviteEmailDispatchService(
                    InMemoryOwnerInvitationRepository(),
                    InvitationEmailGateway { _, _, _, _ ->
                        sends += 1
                        VerificationDeliveryStatus.ACCEPTED
                    },
                    clock,
                )

            assertEquals(OwnerInviteDispatchOutcome.NOT_CONFIGURED, service.dispatch("C".repeat(43), config()))
            assertEquals(
                OwnerInviteDispatchOutcome.NOT_CONFIGURED,
                service.dispatch(token, config().copy(codeDigest = "f".repeat(64))),
            )
            assertEquals(
                OwnerInviteDispatchOutcome.NOT_CONFIGURED,
                service.dispatch(token, config().copy(expiresAt = now)),
            )
            assertEquals(0, sends)
            assertFalse(sends > 0)
        }

    private fun config() =
        OwnerInviteDispatchConfig(
            recipient = "erik55509@hotmail.com",
            code = code,
            codeDigest = digest,
            expiresAt = now.plusSeconds(3600),
            dispatchToken = token,
        )
}
