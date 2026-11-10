package org.companerodeescuela.api.invitations

import kotlinx.coroutines.test.runTest
import org.companerodeescuela.shared.contracts.VerificationDeliveryStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
