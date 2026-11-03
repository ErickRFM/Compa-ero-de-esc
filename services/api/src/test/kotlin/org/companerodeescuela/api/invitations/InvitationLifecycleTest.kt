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
}
