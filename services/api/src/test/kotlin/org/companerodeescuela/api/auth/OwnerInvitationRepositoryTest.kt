package org.companerodeescuela.api.auth

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OwnerInvitationRepositoryTest {
    @Test
    fun `owner invite delivery is atomically claimed once and does not consume registration`() =
        runBlocking {
            val repository = InMemoryOwnerInvitationRepository()
            val now = Instant.parse("2026-11-07T12:00:00Z")
            val expiresAt = now.plusSeconds(3600)
            val digest = "d".repeat(64)
            val claims =
                coroutineScope {
                    (1..24)
                        .map { index ->
                            async {
                                repository.claimDelivery(
                                    digest,
                                    "erik55509@hotmail.com",
                                    expiresAt,
                                    now,
                                    "delivery-$index",
                                )
                            }
                        }.awaitAll()
                }

            assertEquals(1, claims.count { it })
            assertTrue(repository.consume(digest, "erik55509@hotmail.com", expiresAt, now, "owner-account"))
            assertFalse(repository.claimDelivery(digest, "erik55509@hotmail.com", expiresAt, now, "replay"))
        }

    @Test
    fun `one digest is consumed once across concurrent callers`() =
        runBlocking {
            val repository = InMemoryOwnerInvitationRepository()
            val now = Instant.parse("2026-11-07T12:00:00Z")
            val expiresAt = now.plusSeconds(3600)
            val digest = "a".repeat(64)
            val outcomes =
                coroutineScope {
                    (1..24)
                        .map { index ->
                            async {
                                repository.consume(
                                    digest,
                                    "erik55509@hotmail.com",
                                    expiresAt,
                                    now,
                                    "account-$index",
                                )
                            }
                        }.awaitAll()
                }

            assertEquals(1, outcomes.count { it })
            assertFalse(repository.consume(digest, "erik55509@hotmail.com", expiresAt, now, "replay"))
        }

    @Test
    fun `consumption binds the digest to its original recipient and expiry`() =
        runBlocking {
            val repository = InMemoryOwnerInvitationRepository()
            val now = Instant.parse("2026-11-07T12:00:00Z")
            val expiresAt = now.plusSeconds(3600)
            val digest = "b".repeat(64)

            assertTrue(repository.consume(digest, "erik55509@hotmail.com", expiresAt, now, "owner-account"))
            assertFalse(repository.consume(digest, "other@example.test", expiresAt, now, "other-account"))
            assertFalse(repository.consume(digest, "erik55509@hotmail.com", expiresAt.plusSeconds(1), now, "reconfigured"))
        }

    @Test
    fun `expired and malformed invitation records fail closed`() =
        runBlocking {
            val repository = InMemoryOwnerInvitationRepository()
            val now = Instant.parse("2026-11-07T12:00:00Z")

            assertFalse(repository.consume("not-a-digest", "erik55509@hotmail.com", now.plusSeconds(60), now, "owner"))
            assertFalse(repository.consume("c".repeat(64), "erik55509@hotmail.com", now, now, "owner"))
        }
}
