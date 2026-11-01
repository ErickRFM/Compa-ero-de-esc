package org.companerodeescuela.api.integrations.academic

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.companerodeescuela.api.integrations.academic.siii.SiiiSectionProjectionHandler
import org.companerodeescuela.api.integrations.academic.siii.SiiiSectionSnapshotRepository
import org.companerodeescuela.api.integrations.academic.siii.StoredSiiiSection
import org.companerodeescuela.api.integrations.push.InstitutionalProjectionCommand
import org.companerodeescuela.shared.contracts.InstitutionalEventOperation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class SiiiChangeNotificationTest {
    @Test fun `intervening revision forces recipient recomputation instead of losing pending owners`() =
        runTest {
            val repository = Repository()

            fun oneStudent(version: Long): InstitutionalProjectionCommand {
                val payload = sectionPayload()
                return command(version).copy(
                    payload =
                        JsonObject(
                            payload + ("enrollments" to JsonArray((payload.getValue("enrollments") as JsonArray).take(1))),
                        ),
                )
            }
            val intervening =
                SiiiSectionProjectionHandler(
                    repository,
                    notificationOwners = { _, snapshot -> snapshot?.enrollments.orEmpty().map { "NATIVE:${it.studentExternalId}" } },
                    notifyChange = { error("Synthetic queue failure") },
                )
            var intervene = false
            val notices = mutableListOf<StoredSiiiSection>()
            val handler =
                SiiiSectionProjectionHandler(
                    repository,
                    notificationOwners = { _, snapshot ->
                        if (intervene) {
                            intervene = false
                            assertFailsWith<IllegalStateException> { intervening.apply("group:v1", command(2), emptyMap()) }
                        }
                        snapshot?.enrollments.orEmpty().map { "NATIVE:${it.studentExternalId}" }
                    },
                    notifyChange = { notices += it },
                )
            handler.apply("group:v1", oneStudent(1), emptyMap())
            intervene = true
            assertFailsWith<org.companerodeescuela.api.integrations.push.InstitutionalProjectionTransientFailure> {
                handler.apply("group:v1", oneStudent(3), emptyMap())
            }
            assertEquals(2L, repository.stored?.sourceVersion)
            handler.apply("group:v1", oneStudent(3), emptyMap())
            assertEquals(setOf("NATIVE:STU-01", "NATIVE:STU-02"), notices.last().notificationOwners.toSet())
        }

    private class Repository : SiiiSectionSnapshotRepository {
        var stored: StoredSiiiSection? = null

        override suspend fun find(
            institutionId: String,
            sectionId: String,
        ) = stored

        override suspend fun apply(value: StoredSiiiSection) {
            if ((stored?.sourceVersion ?: 0) < value.sourceVersion) stored = value
        }

        override suspend fun applyExpected(
            value: StoredSiiiSection,
            expectedPreviousVersion: Long,
        ) {
            if ((stored?.sourceVersion ?: 0) != expectedPreviousVersion) {
                throw org.companerodeescuela.api.integrations.push.InstitutionalProjectionTransientFailure(
                    "SNAPSHOT_RECIPIENT_REVISION_CHANGED",
                )
            }
            apply(value)
        }

        override suspend fun markNotificationsQueued(
            institutionId: String,
            sectionId: String,
            sourceVersion: Long,
            eventId: String,
        ) {
            stored?.takeIf { it.sourceVersion == sourceVersion && it.eventId == eventId }?.let {
                stored =
                    it.copy(notificationsQueued = true)
            }
        }
    }

    private fun command(
        version: Long,
        operation: InstitutionalEventOperation = InstitutionalEventOperation.UPSERT,
    ) = InstitutionalProjectionCommand(
        institutionId = "tenant",
        entityType = "group",
        externalId = SECTION_ID,
        eventId = "change-$version",
        sourceVersion = version,
        schemaVersion = 1,
        operation = operation,
        payload = if (operation == InstitutionalEventOperation.DELETE) JsonObject(emptyMap()) else sectionPayload(),
    )

    @Test fun `deletion and retry retain affected owners after the snapshot becomes a tombstone`() =
        runTest {
            val repository = Repository()
            var fail = false
            val notices = mutableListOf<List<String>>()
            val handler =
                SiiiSectionProjectionHandler(
                    repository,
                    notificationOwners = { _, snapshot -> if (snapshot == null) emptyList() else listOf("NATIVE:student") },
                    notifyChange = {
                        notices += it.notificationOwners
                        if (fail) error("Synthetic outbox outage")
                    },
                )
            handler.apply("group:v1", command(1), emptyMap())
            fail = true
            assertFailsWith<IllegalStateException> { handler.apply("group:v1", command(2, InstitutionalEventOperation.DELETE), emptyMap()) }
            val deleted = assertNotNull(repository.stored)
            assertEquals(null, deleted.snapshot)
            assertEquals(listOf("NATIVE:student"), deleted.notificationOwners)
            fail = false
            handler.apply("group:v1", command(2, InstitutionalEventOperation.DELETE), emptyMap())
            assertEquals(listOf("NATIVE:student"), notices.last())
            assertEquals(2L, repository.stored?.sourceVersion)
        }

    @Test fun `obsolete projected changes never enqueue a new notification`() =
        runTest {
            val repository = Repository()
            val versions = mutableListOf<Long>()
            val handler =
                SiiiSectionProjectionHandler(repository, notificationOwners = { _, _ -> listOf("NATIVE:student") }, notifyChange = {
                    versions +=
                        it.sourceVersion
                })
            handler.apply("group:v1", command(2), emptyMap())
            handler.apply("group:v1", command(1), emptyMap())
            assertEquals(listOf(2L), versions)
        }

    @Test fun `new source versions retain undelivered removal notices until queued`() =
        runTest {
            val repository = Repository()
            val delivered = mutableMapOf<Long, List<String>>()
            val handler =
                SiiiSectionProjectionHandler(
                    repository,
                    notificationOwners = { _, snapshot -> snapshot?.enrollments.orEmpty().map { "NATIVE:${it.studentExternalId}" } },
                    notifyChange = {
                        if (it.sourceVersion ==
                            2L
                        ) {
                            error("Synthetic enqueue failure")
                        } else {
                            delivered[it.sourceVersion] = it.notificationOwners
                        }
                    },
                )
            handler.apply("group:v1", command(1), emptyMap())

            fun afterWithdrawal(version: Long): InstitutionalProjectionCommand {
                val payload = sectionPayload()
                val retained = (payload.getValue("enrollments") as JsonArray).take(1)
                return command(version).copy(payload = JsonObject(payload + ("enrollments" to JsonArray(retained))))
            }
            assertFailsWith<IllegalStateException> { handler.apply("group:v1", afterWithdrawal(2), emptyMap()) }
            assertEquals(false, repository.stored?.notificationsQueued)
            handler.apply("group:v1", afterWithdrawal(3), emptyMap())
            assertEquals(setOf("NATIVE:STU-01", "NATIVE:STU-02"), delivered.getValue(3).toSet())
            assertEquals(true, repository.stored?.notificationsQueued)
            handler.apply("group:v1", afterWithdrawal(4), emptyMap())
            assertEquals(listOf("NATIVE:STU-01"), delivered.getValue(4))
        }
}
