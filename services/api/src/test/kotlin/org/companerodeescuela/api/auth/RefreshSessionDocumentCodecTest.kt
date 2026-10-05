package org.companerodeescuela.api.auth

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.bson.Document
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary

class RefreshSessionDocumentCodecTest {
    @Test
    fun `mongo document round trips session metadata and never stores raw refresh token`() {
        val rawRefreshToken = "raw-refresh-secret-that-must-not-persist"
        val session = RefreshSession(
            id = "session-1",
            tokenHash = "sha256-hash",
            user = UserSummary(
                id = "student-1",
                displayName = "Ana López",
                email = "ana@example.edu",
                roles = setOf(UserRole.STUDENT),
            ),
            createdAt = Instant.parse("2026-10-02T12:00:00Z"),
            expiresAt = Instant.parse("2026-11-01T12:00:00Z"),
            generation = 2,
            previousTokenHashes = setOf("old-hash-1", "old-hash-2"),
        )

        val document = RefreshSessionDocumentCodec.encode(session)
        val persistedJson = document.toJson()

        assertEquals(session, RefreshSessionDocumentCodec.decode(Document.parse(persistedJson)))
        assertTrue("sha256-hash" in persistedJson)
        assertFalse(rawRefreshToken in persistedJson)
    }
}