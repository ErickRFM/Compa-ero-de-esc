package org.companerodeescuela.api.integrations

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.companerodeescuela.api.devices.InMemoryPushOutboxRepository
import org.companerodeescuela.api.devices.PushOutboxItem
import org.companerodeescuela.api.integrations.push.InstitutionalAdapterOutcome
import org.companerodeescuela.api.integrations.push.InstitutionalEventAdapter
import org.companerodeescuela.api.integrations.push.InstitutionalEventProjectionWorker
import org.companerodeescuela.api.integrations.push.InstitutionalEventRecord
import org.companerodeescuela.api.integrations.push.InstitutionalIdentityReference
import org.companerodeescuela.api.integrations.push.InstitutionalProjectionCommand
import org.companerodeescuela.api.integrations.push.InstitutionalProjectionCommandHandler
import org.companerodeescuela.api.integrations.push.InstitutionalProjectionIdentityResolver
import org.companerodeescuela.api.integrations.push.InstitutionalProjectionLease
import org.companerodeescuela.api.integrations.push.InstitutionalProjectionRepository
import org.companerodeescuela.api.integrations.push.InstitutionalProjectionScope
import org.companerodeescuela.shared.contracts.InstitutionalEventOperation
import org.companerodeescuela.shared.contracts.InstitutionalIdentityKind
import org.companerodeescuela.shared.contracts.InstitutionalPushEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SiiiToAndroidIntegrationTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC)
    private lateinit var pushOutbox: InMemoryPushOutboxRepository
    private val appliedCommands = mutableListOf<InstitutionalProjectionCommand>()

    private val sampleAdapter =
        object : InstitutionalEventAdapter {
            override val adapterId: String = "siii.classroom.v1"
            override val supportedSchemaVersions: Set<Int> = setOf(1)

            override suspend fun adapt(record: InstitutionalEventRecord): InstitutionalAdapterOutcome {
                val payload = record.event.payload
                val command =
                    InstitutionalProjectionCommand(
                        institutionId = record.tenantId,
                        entityType = record.event.entityType,
                        externalId = record.event.externalId,
                        eventId = record.event.eventId,
                        sourceVersion = record.event.sourceVersion,
                        schemaVersion = record.event.schemaVersion,
                        operation = record.event.operation,
                        payload = payload,
                        identityReferences =
                            listOf(
                                InstitutionalIdentityReference(InstitutionalIdentityKind.STUDENT, "student-ext-101"),
                            ),
                    )
                return InstitutionalAdapterOutcome.Command(command)
            }
        }

    private class FakeProjectionRepository(
        private var currentLease: InstitutionalProjectionLease? = null,
    ) : InstitutionalProjectionRepository {
        var completions = 0
        var supersedes = 0
        val appliedVersions = mutableMapOf<String, Long>()

        override suspend fun claimNext(
            tenantId: String,
            adapterId: String,
            now: Instant,
            leaseDurationSeconds: Long,
        ): InstitutionalProjectionLease? = currentLease

        override suspend fun renew(
            lease: InstitutionalProjectionLease,
            now: Instant,
            leaseDurationSeconds: Long,
        ): Boolean = true

        override suspend fun findAppliedVersion(
            tenantId: String,
            entityType: String,
            externalId: String,
        ): Long? = appliedVersions["$tenantId:$entityType:$externalId"]

        override suspend fun advanceAppliedVersion(
            tenantId: String,
            entityType: String,
            externalId: String,
            sourceVersion: Long,
            now: Instant,
        ): Boolean {
            appliedVersions["$tenantId:$entityType:$externalId"] = sourceVersion
            return true
        }

        override suspend fun reconcile(
            tenantId: String,
            adapterId: String,
            now: Instant,
        ): Long = 0L

        override suspend fun complete(
            lease: InstitutionalProjectionLease,
            now: Instant,
        ): Boolean {
            completions++
            appliedVersions["${lease.record.tenantId}:${lease.record.event.entityType}:${lease.record.event.externalId}"] =
                lease.record.event.sourceVersion
            return true
        }

        override suspend fun supersede(
            lease: InstitutionalProjectionLease,
            now: Instant,
        ): Boolean {
            supersedes++
            return true
        }

        override suspend fun fail(
            lease: InstitutionalProjectionLease,
            now: Instant,
            failureCode: String,
        ): Boolean = true

        override suspend fun retry(
            lease: InstitutionalProjectionLease,
            now: Instant,
            retryAt: Instant,
            failureCode: String,
        ): Boolean = true

        override suspend fun markPendingIdentity(
            lease: InstitutionalProjectionLease,
            now: Instant,
        ): Boolean = true

        override suspend fun markUnsupported(
            lease: InstitutionalProjectionLease,
            now: Instant,
            failureCode: String,
        ): Boolean = true

        override suspend fun nextScope(now: Instant): InstitutionalProjectionScope? =
            currentLease?.let { InstitutionalProjectionScope(it.record.tenantId, it.adapterId) }
    }

    @Test
    fun `e2e flow processes SIII classroom event and enqueues targeted FCM push to Android`() =
        runBlocking {
            pushOutbox = InMemoryPushOutboxRepository()
            appliedCommands.clear()

            val identityResolver =
                InstitutionalProjectionIdentityResolver { _, reference ->
                    if (reference.externalId == "student-ext-101") "NATIVE:native-user-101" else null
                }

            val commandHandler =
                InstitutionalProjectionCommandHandler { _, command, resolvedUsers ->
                    appliedCommands.add(command)
                    val studentNativeId =
                        resolvedUsers[
                            InstitutionalIdentityReference(
                                InstitutionalIdentityKind.STUDENT,
                                "student-ext-101",
                            ),
                        ]
                    if (studentNativeId != null) {
                        runBlocking {
                            pushOutbox.enqueue(
                                PushOutboxItem(
                                    id = "push-" + command.eventId,
                                    owner = studentNativeId,
                                    token = "fcm-token-123",
                                    title = "Cambio de Aula",
                                    body = "Tu clase fue reasignada al aula ${command.payload["newRoom"]}",
                                    data =
                                        mapOf(
                                            "type" to "notice",
                                            "destination" to "channel",
                                            "packageName" to "org.companerodeescuela",
                                        ),
                                ),
                            )
                        }
                    }
                }

            val eventPayload =
                buildJsonObject {
                    put("courseId", "course-9a")
                    put("newRoom", "Aula 204")
                }

            val record =
                InstitutionalEventRecord(
                    tenantId = "inst-01",
                    sourceCredentialId = "credential-01",
                    event =
                        InstitutionalPushEvent(
                            schemaVersion = 1,
                            eventId = "evt-room-change-001",
                            entityType = "section",
                            externalId = "sec-9a",
                            sourceVersion = 1L,
                            occurredAtEpochSeconds = clock.instant().epochSecond,
                            operation = InstitutionalEventOperation.UPSERT,
                            payload = eventPayload,
                        ),
                    payloadSha256 = "sha256-hash",
                    receivedAt = clock.instant(),
                )

            val lease =
                InstitutionalProjectionLease(
                    record = record,
                    adapterId = "siii.classroom.v1",
                    leaseToken = "lease-token-1",
                    leaseExpiresAt = clock.instant().plusSeconds(90),
                    attemptCount = 1,
                )

            val repo = FakeProjectionRepository(lease)
            val worker =
                InstitutionalEventProjectionWorker(
                    repository = repo,
                    identityResolver = identityResolver,
                    commandHandler = commandHandler,
                    adapters = listOf(sampleAdapter),
                    clock = clock,
                )

            val processed = worker.processNext("inst-01", "siii.classroom.v1")
            assertTrue(processed)

            assertEquals(1, appliedCommands.size)
            val applied = appliedCommands.first()
            assertEquals("sec-9a", applied.externalId)
            assertEquals(1L, applied.sourceVersion)

            val claimedPush = pushOutbox.claim(clock.instant())
            assertNotNull(claimedPush)
            assertEquals("NATIVE:native-user-101", claimedPush!!.owner)
            assertEquals("Cambio de Aula", claimedPush.title)
            assertEquals("org.companerodeescuela", claimedPush.data["packageName"])
            assertEquals("channel", claimedPush.data["destination"])
        }

    @Test
    fun `idempotent reprocessing of duplicate SIII event is superseded`() =
        runBlocking {
            pushOutbox = InMemoryPushOutboxRepository()
            appliedCommands.clear()

            val eventPayload =
                buildJsonObject {
                    put("courseId", "course-9a")
                    put("newRoom", "Aula 204")
                }

            val record =
                InstitutionalEventRecord(
                    tenantId = "inst-01",
                    sourceCredentialId = "credential-01",
                    event =
                        InstitutionalPushEvent(
                            schemaVersion = 1,
                            eventId = "evt-room-change-001",
                            entityType = "section",
                            externalId = "sec-9a",
                            sourceVersion = 1L,
                            occurredAtEpochSeconds = clock.instant().epochSecond,
                            operation = InstitutionalEventOperation.UPSERT,
                            payload = eventPayload,
                        ),
                    payloadSha256 = "sha256-hash",
                    receivedAt = clock.instant(),
                )

            val lease =
                InstitutionalProjectionLease(
                    record = record,
                    adapterId = "siii.classroom.v1",
                    leaseToken = "lease-token-1",
                    leaseExpiresAt = clock.instant().plusSeconds(90),
                    attemptCount = 1,
                )

            val repo = FakeProjectionRepository(lease)
            repo.appliedVersions["inst-01:section:sec-9a"] = 1L // already applied v1

            val worker =
                InstitutionalEventProjectionWorker(
                    repository = repo,
                    identityResolver = { _, _ -> null },
                    commandHandler = {
                        _,
                        _,
                        _,
                        ->
                        appliedCommands
                            .add(
                                InstitutionalProjectionCommand(
                                    "inst-01",
                                    "section",
                                    "sec-9a",
                                    "evt-1",
                                    1L,
                                    1,
                                    InstitutionalEventOperation.UPSERT,
                                    buildJsonObject {
                                    },
                                ),
                            )
                    },
                    adapters = listOf(sampleAdapter),
                    clock = clock,
                )

            val processed = worker.processNext("inst-01", "siii.classroom.v1")
            assertTrue(processed)

            // Superseded without calling command handler again
            assertEquals(0, appliedCommands.size)
            assertEquals(1, repo.supersedes)
        }
}
