package org.companerodeescuela.api.audit

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
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
}
