package org.companerodeescuela.api.auth

import java.time.Instant
import java.util.Date
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
