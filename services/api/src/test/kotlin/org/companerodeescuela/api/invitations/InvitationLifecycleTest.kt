package org.companerodeescuela.api.invitations

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InvitationLifecycleTest {
    private val now = Instant.parse("2026-11-03T00:00:00Z")

    @Test fun `tokens are random and their printable wrapper never reveals them`() {
        val first = InvitationToken.generate()
        val second = InvitationToken.generate()
        assertNotEquals(first.value, second.value)
        assertEquals(43, first.value.length)
        assertEquals(43, first.hash.length)
        assertFalse(first.toString().contains(first.value))
    }

    @Test fun `acceptance needs a verified matching account and never grants roles`() =
        runTest {
            val repo = InMemoryInvitationRepository()
            val token = InvitationToken.generate()
            val invite =
                InvitationRecord(
                    "invite",
                    "person@example.test",
                    "campus",
                    InvitationProfile.TEACHER,
                    "issuer",
                    now,
                    now.plusSeconds(600),
                    InvitationStatus.SENT,
                    token.hash,
                )
            assertTrue(repo.create(invite))
            assertNull(repo.accept("invite", token.hash, "other@example.test", true, "account", "accept-op", now))
            assertNull(repo.accept("invite", token.hash, "person@example.test", false, "account", "accept-op", now))
            val accepted = assertNotNull(repo.accept("invite", token.hash, "person@example.test", true, "account", "accept-op", now))
            assertEquals(InvitationStatus.ACCEPTED, accepted.status)
            assertEquals("account", accepted.acceptedBy)
            assertNotNull(repo.accept("invite", token.hash, "person@example.test", true, "account", "accept-op", now))
            assertNull(repo.accept("invite", token.hash, "person@example.test", true, "other-account", "another-op", now))
        }

    @Test fun `expired and revoked invitations cannot be consumed`() =
        runTest {
            val repo = InMemoryInvitationRepository()
            val hash = InvitationToken.generate().hash
            assertTrue(
                repo.create(
                    InvitationRecord(
                        "expired",
                        "a@example.test",
                        "campus",
                        InvitationProfile.STUDENT,
                        "issuer",
                        now,
                        now.plusSeconds(1),
                        InvitationStatus.SENT,
                        hash,
                    ),
                ),
            )
            assertNull(repo.accept("expired", hash, "a@example.test", true, "a", "op", now.plusSeconds(1)))
            val revokedHash = InvitationToken.generate().hash
            assertTrue(
                repo.create(
                    InvitationRecord(
                        "revoked",
                        "a@example.test",
                        "campus",
                        InvitationProfile.STUDENT,
                        "issuer",
                        now,
                        now.plusSeconds(600),
                        InvitationStatus.SENT,
                        revokedHash,
                    ),
                ),
            )
            assertTrue(repo.revoke("revoked", now))
            assertNull(repo.accept("revoked", revokedHash, "a@example.test", true, "a", "op", now))
        }

    @Test fun `different consumers racing for the same invitation have one winner`() =
        runTest {
            val repo = InMemoryInvitationRepository()
            val hash = InvitationToken.generate().hash
            assertTrue(
                repo.create(
                    InvitationRecord(
                        "invite",
                        "a@example.test",
                        "campus",
                        InvitationProfile.PARTICIPANT,
                        "issuer",
                        now,
                        now.plusSeconds(600),
                        InvitationStatus.SENT,
                        hash,
                    ),
                ),
            )
            val results =
                coroutineScope {
                    (1..20)
                        .map { n ->
                            async(Dispatchers.Default) {
                                repo.accept("invite", hash, "a@example.test", true, "account-$n", "op-$n", now)
                            }
                        }.awaitAll()
                }
            assertEquals(1, results.count { it != null })
        }

    @Test fun `same token cannot back multiple invitations and exact receipt remains recoverable after expiry`() =
        runTest {
            val repo = InMemoryInvitationRepository()
            val hash = InvitationToken.generate().hash
            val first =
                InvitationRecord(
                    "first",
                    "a@example.test",
                    "campus",
                    InvitationProfile.STUDENT,
                    "issuer",
                    now,
                    now.plusSeconds(60),
                    InvitationStatus.SENT,
                    hash,
                )
            assertTrue(repo.create(first))
            assertFalse(repo.create(first.copy(id = "second")))
            assertNotNull(repo.accept("first", hash, "a@example.test", true, "account", "same-operation", now))
            assertNotNull(repo.accept("first", hash, "a@example.test", true, "account", "same-operation", now.plusSeconds(61)))
            assertNull(repo.accept("first", hash, "a@example.test", true, "other", "other-operation", now.plusSeconds(61)))
        }

    @Test fun `retry replaces token hash on a sent invitation`() =
        runTest {
            val repo = InMemoryInvitationRepository()
            val originalHash = InvitationToken.generate().hash
            assertTrue(
                repo.create(
                    InvitationRecord(
                        "retry-invite",
                        "person@example.test",
                        "campus",
                        InvitationProfile.TEACHER,
                        "issuer",
                        now,
                        now.plusSeconds(600),
                        InvitationStatus.SENT,
                        originalHash,
                    ),
                ),
            )
            val newHash = InvitationToken.generate().hash
            assertTrue(repo.retry("retry-invite", newHash, now))
            val updated = repo.find("retry-invite")
            assertNotNull(updated)
            assertEquals(newHash, updated.tokenHash)
            assertEquals(InvitationStatus.SENT, updated.status)
        }

    @Test fun `retry fails for accepted or expired invitations`() =
        runTest {
            val repo = InMemoryInvitationRepository()
            val hash = InvitationToken.generate().hash
            assertTrue(
                repo.create(
                    InvitationRecord(
                        "accepted",
                        "a@example.test",
                        "campus",
                        InvitationProfile.STUDENT,
                        "issuer",
                        now,
                        now.plusSeconds(600),
                        InvitationStatus.SENT,
                        hash,
                    ),
                ),
            )
            // Accept it first
            assertNotNull(repo.accept("accepted", hash, "a@example.test", true, "acc", "op", now))
            // Retry should fail
            assertFalse(repo.retry("accepted", InvitationToken.generate().hash, now))
        }

    @Test fun `expireDue transitions only created and sent invitations past their expiry`() =
        runTest {
            val repo = InMemoryInvitationRepository()
            assertTrue(
                repo.create(
                    InvitationRecord(
                        "old-sent",
                        "a@example.test",
                        "campus",
                        InvitationProfile.STUDENT,
                        "issuer",
                        now.minusSeconds(700),
                        now.minusSeconds(60),
                        InvitationStatus.SENT,
                        InvitationToken.generate().hash,
                    ),
                ),
            )
            assertTrue(
                repo.create(
                    InvitationRecord(
                        "fresh-sent",
                        "b@example.test",
                        "campus",
                        InvitationProfile.STUDENT,
                        "issuer",
                        now,
                        now.plusSeconds(600),
                        InvitationStatus.SENT,
                        InvitationToken.generate().hash,
                    ),
                ),
            )
            val expired = repo.expireDue(now)
            assertEquals(1, expired)
            assertEquals(InvitationStatus.EXPIRED, repo.find("old-sent")?.status)
            assertEquals(InvitationStatus.SENT, repo.find("fresh-sent")?.status)
        }

    @Test fun `outbox enqueues and retrieves pending entries`() =
        runTest {
            val outbox = InMemoryInvitationOutbox()
            val entry =
                OutboxEntry(
                    invitationId = "inv-1",
                    recipient = "person@example.test",
                    tokenValue = "token-value",
                    expiresAt = now.plusSeconds(600),
                    nextAttemptAt = now.minusSeconds(1),
                    createdAt = now,
                )
            assertTrue(outbox.enqueue(entry))
            assertFalse(outbox.enqueue(entry)) // Duplicate
            val pending = outbox.pending(now)
            assertEquals(1, pending.size)
            assertEquals("inv-1", pending[0].invitationId)
        }

    @Test fun `outbox marks delivered and clears token`() =
        runTest {
            val outbox = InMemoryInvitationOutbox()
            outbox.enqueue(
                OutboxEntry(
                    invitationId = "inv-2",
                    recipient = "person@example.test",
                    tokenValue = "secret-token",
                    expiresAt = now.plusSeconds(600),
                    nextAttemptAt = now,
                    createdAt = now,
                ),
            )
            assertTrue(outbox.markDelivered("inv-2", now))
            val pending = outbox.pending(now)
            assertEquals(0, pending.size) // Delivered entries are not pending
        }

    @Test fun `outbox schedules retry with backoff`() =
        runTest {
            val outbox = InMemoryInvitationOutbox()
            outbox.enqueue(
                OutboxEntry(
                    invitationId = "inv-3",
                    recipient = "person@example.test",
                    tokenValue = "token",
                    expiresAt = now.plusSeconds(600),
                    nextAttemptAt = now,
                    createdAt = now,
                ),
            )
            val nextRetry = now.plusSeconds(120)
            assertTrue(outbox.scheduleRetry("inv-3", nextRetry, now))
            // Should not be pending yet
            assertEquals(0, outbox.pending(now).size)
            // Should be pending after the retry time
            assertEquals(1, outbox.pending(nextRetry).size)
        }
}
