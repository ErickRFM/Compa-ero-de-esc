package org.companerodeescuela.api.auth

import java.time.Instant
import java.util.Date
import java.time.Duration
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary

class MongoRefreshReplayTest {
    private val now = Instant.parse("2026-10-09T06:00:00Z")
    private val user = UserSummary("native-1", "QA", roles = setOf(UserRole.STUDENT))

    private fun fixture(): MongoAuthFixture = MongoAuthFixture(RefreshSessionDocumentCodec.encode(
        RefreshSession("session-1", "T1", user, now.minusSeconds(60), now.plusSeconds(3600),
            generation = 1, identitySource = SessionIdentitySource.NATIVE, previousTokenHashes = setOf("T0")),
    ))

    @Test
    fun `recognized replay revokes family even when another rotation wins before update`() = runTest {
        val storage = fixture()
        storage.beforeNextUpdate = {
            storage.document["tokenHash"] = "T2"
            storage.document["generation"] = 2L
            storage.document["previousTokenHashes"] = listOf("T0", "T1")
        }
        val repository = MongoRefreshSessionRepository(storage.database)
        assertNull(repository.rotate("session-1", "T0", "attacker-next", user, now, now.plusSeconds(3600)))
        assertEquals(Date.from(now), storage.document.getDate("revokedAt"))
        assertNull(repository.rotate("session-1", "T2", "T3", user, now, now.plusSeconds(3600)))
    }

    @Test
    fun `unknown hash cannot revoke valid family`() = runTest {
        val storage = fixture()
        val repository = MongoRefreshSessionRepository(storage.database)
        assertNull(repository.rotate("session-1", "unknown", "next", user, now, now.plusSeconds(3600)))
        assertNull(storage.document.getDate("revokedAt"))
        assertEquals("T2", repository.rotate("session-1", "T1", "T2", user, now, now.plusSeconds(3600))?.tokenHash)
    }

    @Test
    fun `immediately previous token can rotate again within grace`() = runTest {
        val session = RefreshSession(
            "session-1", "T1", user, now.minusSeconds(60), now.plusSeconds(3600), generation = 1,
            previousTokenHashes = linkedSetOf("T0"), immediatelyPreviousTokenHash = "T0",
            lastRotatedAt = now.minusSeconds(30), identitySource = SessionIdentitySource.NATIVE,
        )
        val storage = MongoAuthFixture(RefreshSessionDocumentCodec.encode(session))
        val repository = MongoRefreshSessionRepository(storage.database)

        val rotated = repository.rotate("session-1", "T0", "T2", user, now, now.plusSeconds(3600), Duration.ofSeconds(60))

        assertEquals("T2", rotated?.tokenHash)
        assertEquals("T1", rotated?.immediatelyPreviousTokenHash)
        assertNull(storage.document.getDate("revokedAt"))
    }

    @Test
    fun `immediately previous token outside grace revokes the family`() = runTest {
        val session = RefreshSession(
            "session-1", "T1", user, now.minusSeconds(60), now.plusSeconds(3600), generation = 1,
            previousTokenHashes = linkedSetOf("T0"), immediatelyPreviousTokenHash = "T0",
            lastRotatedAt = now.minusSeconds(61), identitySource = SessionIdentitySource.NATIVE,
        )
        val storage = MongoAuthFixture(RefreshSessionDocumentCodec.encode(session))
        val repository = MongoRefreshSessionRepository(storage.database)

        assertNull(repository.rotate("session-1", "T0", "T2", user, now, now.plusSeconds(3600), Duration.ofSeconds(60)))

        assertEquals(Date.from(now), storage.document.getDate("revokedAt"))
    }

    @Test
    fun `mongo token history is sliced to the newest five hashes`() = runTest {
        val session = RefreshSession(
            "session-1", "T8", user, now.minusSeconds(60), now.plusSeconds(3600), generation = 8,
            previousTokenHashes = linkedSetOf("T3", "T4", "T5", "T6", "T7"),
            immediatelyPreviousTokenHash = "T7", lastRotatedAt = now.minusSeconds(5),
            identitySource = SessionIdentitySource.NATIVE,
        )
        val storage = MongoAuthFixture(RefreshSessionDocumentCodec.encode(session))
        val repository = MongoRefreshSessionRepository(storage.database)

        val rotated = repository.rotate("session-1", "T8", "T9", user, now, now.plusSeconds(3600))

        assertEquals(listOf("T4", "T5", "T6", "T7", "T8"), storage.document.getList("previousTokenHashes", String::class.java))
        assertEquals(5, rotated?.previousTokenHashes?.size)
    }

    @Test
    fun `replay cannot revoke another session or overwrite existing revocation`() = runTest {
        val storage = fixture()
        val repository = MongoRefreshSessionRepository(storage.database)
        assertNull(repository.rotate("other-session", "T0", "next", user, now, now.plusSeconds(3600)))
        assertNull(storage.document.getDate("revokedAt"))
        val original = Date.from(now.minusSeconds(10))
        storage.document["revokedAt"] = original
        assertNull(repository.rotate("session-1", "T0", "next", user, now, now.plusSeconds(3600)))
        assertEquals(original, storage.document.getDate("revokedAt"))
    }
}
