package org.companerodeescuela.api.audit

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PlatformAuditLogTest {
    @Test
    @DisplayName("Audit lifecycle events are distinct and duplicate writes are idempotent")
    fun lifecycleEventsAreDistinctAndIdempotent() =
        runBlocking {
            val log = InMemoryPlatformAuditLog()
            val createdAt = Instant.parse("2026-11-09T22:00:00Z")
            val started =
                PlatformAuditEvent.create(
                    actorId = "account-123",
                    action = "INVITATION_DISPATCH",
                    resourceType = "INVITATION_OPERATION",
                    resourceId = "operation-123",
                    operationId = "operation-123",
                    outcome = "STARTED",
                    createdAt = createdAt,
                )
            val completed =
                PlatformAuditEvent.create(
                    actorId = "account-123",
                    action = "INVITATION_DISPATCH",
                    resourceType = "INVITATION_OPERATION",
                    resourceId = "operation-123",
                    operationId = "operation-123",
                    outcome = "SUCCEEDED",
                    createdAt = createdAt,
                )

            assertNotEquals(started.id, completed.id)
            assertTrue(log.record(started))
            assertTrue(log.record(completed))
            assertFalse(log.record(started))
        }

    @Test
    @DisplayName("Audit log lists recent events descending with action and actor filters")
    fun listRecentReturnsOrderedFilteredEvents() =
        runBlocking {
            val log = InMemoryPlatformAuditLog()
            val older =
                PlatformAuditEvent.create(
                    actorId = "owner-1",
                    action = "INVITATION_SEND",
                    resourceType = "INVITATION",
                    resourceId = "inv-1",
                    operationId = "op-1",
                    outcome = "SUCCEEDED",
                    createdAt = Instant.parse("2026-11-19T10:00:00Z"),
                )
            val newer =
                PlatformAuditEvent.create(
                    actorId = "owner-2",
                    action = "ACCOUNT_LIFECYCLE",
                    resourceType = "ACCOUNT",
                    resourceId = "acc-1",
                    operationId = "op-2",
                    outcome = "SUCCEEDED",
                    createdAt = Instant.parse("2026-11-19T11:00:00Z"),
                )
            log.record(older)
            log.record(newer)

            assertEquals(listOf(newer, older), log.listRecent())
            assertEquals(listOf(older), log.listRecent(action = "INVITATION_SEND"))
            assertEquals(listOf(newer), log.listRecent(actorId = "owner-2"))
        }
}
