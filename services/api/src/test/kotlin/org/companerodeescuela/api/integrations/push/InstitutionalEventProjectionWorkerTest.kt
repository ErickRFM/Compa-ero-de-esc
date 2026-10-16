package org.companerodeescuela.api.integrations.push

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.companerodeescuela.shared.contracts.InstitutionalEventOperation
import org.companerodeescuela.shared.contracts.InstitutionalIdentityKind
import org.companerodeescuela.shared.contracts.InstitutionalPushEvent

class InstitutionalEventProjectionWorkerTest {
    private val now = Instant.parse("2026-10-15T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `unsupported schema remains in the receipt ledger for reconciliation`() =
        runBlocking {
            val repository = FakeProjectionRepository(lease("schedule:v2", schemaVersion = 2))
            val worker = worker(repository, emptyList())

            assertTrue(worker.processNext("tenant-a", "schedule:v2"))
            assertEquals(InstitutionalProjectionStatus.UNSUPPORTED, repository.status)
            assertEquals("ADAPTER_UNAVAILABLE", repository.failureCode)
            assertEquals(0, repository.completions)
        }

    @Test
    fun `missing identity remains pending without invoking the domain handler`() =
        runBlocking {
            val reference = InstitutionalIdentityReference(InstitutionalIdentityKind.STUDENT, "student-1")
            val adapter =
                adapter("student:v1") {
                    InstitutionalAdapterOutcome.Command(command("student", identityReferences = listOf(reference)))
                }
            val repository = FakeProjectionRepository(lease("student:v1", entityType = "student"))
            var handlerCalls = 0
            val worker =
                worker(
                    repository,
                    listOf(adapter),
                    identityResolver = InstitutionalProjectionIdentityResolver { _, _ -> null },
                    commandHandler =
                        InstitutionalProjectionCommandHandler { _, _, _ ->
                            handlerCalls += 1
                        },
                )

            worker.processNext("tenant-a", "student:v1")

            assertEquals(InstitutionalProjectionStatus.PENDING_IDENTITY, repository.status)
            assertEquals(0, handlerCalls)
        }

    @Test
    fun `tenant mismatch is rejected before the handler`() =
        runBlocking {
            val adapter =
                adapter("schedule:v1") {
                    InstitutionalAdapterOutcome.Command(command("schedule", institutionId = "other-tenant"))
                }
            val repository = FakeProjectionRepository(lease("schedule:v1"))
            var handlerCalls = 0
            val worker =
                worker(
                    repository,
                    listOf(adapter),
                    commandHandler =
                        InstitutionalProjectionCommandHandler { _, _, _ ->
                            handlerCalls += 1
                        },
                )

            worker.processNext("tenant-a", "schedule:v1")

            assertEquals(InstitutionalProjectionStatus.FAILED, repository.status)
            assertEquals("COMMAND_IDENTITY_MISMATCH", repository.failureCode)
            assertEquals(0, handlerCalls)
        }

    @Test
    fun `transient handler error uses bounded retry delay`() =
        runBlocking {
            val adapter = adapter("schedule:v1") { InstitutionalAdapterOutcome.Command(command("schedule")) }
            val repository = FakeProjectionRepository(lease("schedule:v1"))
            val worker =
                worker(
                    repository,
                    listOf(adapter),
                    commandHandler =
                        InstitutionalProjectionCommandHandler { _, _, _ ->
                            throw InstitutionalProjectionTransientFailure("TEMPORARY")
                        },
                )

            worker.processNext("tenant-a", "schedule:v1")

            assertEquals(1, repository.retries)
            assertEquals("TEMPORARY", repository.failureCode)
            assertEquals(now.plusSeconds(5), repository.retryAt)
            assertEquals(InstitutionalProjectionStatus.RETRYABLE, repository.status)
        }

    @Test
    fun `permanent conflict is not retried`() =
        runBlocking {
            val adapter =
                adapter("schedule:v1") {
                    InstitutionalAdapterOutcome.PermanentFailure("ENTITY_CONFLICT")
                }
            val repository = FakeProjectionRepository(lease("schedule:v1"))
            val worker = worker(repository, listOf(adapter))

            worker.processNext("tenant-a", "schedule:v1")

            assertEquals(0, repository.retries)
            assertEquals(InstitutionalProjectionStatus.FAILED, repository.status)
            assertEquals("ENTITY_CONFLICT", repository.failureCode)
        }

    @Test
    fun `delta with a version gap is not applied`() =
        runBlocking {
            val adapter =
                adapter("schedule:v1") {
                    InstitutionalAdapterOutcome.Command(
                        command(
                            entityType = "schedule",
                            sourceVersion = 3,
                            consistency = InstitutionalProjectionConsistency.CONTIGUOUS_DELTA,
                        ),
                    )
                }
            val repository = FakeProjectionRepository(lease("schedule:v1", sourceVersion = 3))
            repository.appliedVersions["tenant-a:schedule:class-1"] = 1
            var handlerCalls = 0
            val worker =
                worker(
                    repository,
                    listOf(adapter),
                    commandHandler =
                        InstitutionalProjectionCommandHandler { _, _, _ ->
                            handlerCalls += 1
                        },
                )

            worker.processNext("tenant-a", "schedule:v1")

            assertEquals(0, handlerCalls)
            assertEquals(1, repository.retries)
            assertEquals("VERSION_GAP", repository.failureCode)
        }

    @Test
    fun `linked user authority is revalidated before each command attempt`() =
        runBlocking {
            val reference = InstitutionalIdentityReference(InstitutionalIdentityKind.STUDENT, "student-1")
            val record = record(entityType = "student")
            val leases =
                listOf(
                    lease("student:v1", record = record, token = "lease-1"),
                    lease("student:v1", record = record, token = "lease-2"),
                )
            val adapter =
                adapter("student:v1") {
                    InstitutionalAdapterOutcome.Command(command("student", identityReferences = listOf(reference)))
                }
            val repository = FakeProjectionRepository(leases)
            var resolutions = 0
            var calls = 0
            val worker =
                worker(
                    repository,
                    listOf(adapter),
                    identityResolver =
                        InstitutionalProjectionIdentityResolver { _, _ ->
                            resolutions += 1
                            "native-student"
                        },
                    commandHandler =
                        InstitutionalProjectionCommandHandler { _, _, _ ->
                            calls += 1
                            if (calls == 1) throw InstitutionalProjectionTransientFailure("TEMPORARY")
                        },
                )

            worker.processNext("tenant-a", "student:v1")
            worker.processNext("tenant-a", "student:v1")

            assertEquals(2, resolutions)
            assertEquals(2, calls)
            assertEquals(InstitutionalProjectionStatus.PROJECTED, repository.status)
        }

    @Test
    fun `successful domain write is safe to replay with the same idempotency key`() =
        runBlocking {
            val source = record()
            val leases =
                listOf(
                    lease("schedule:v1", record = source, token = "lease-1"),
                    lease("schedule:v1", record = source, token = "lease-2"),
                )
            val adapter = adapter("schedule:v1") { InstitutionalAdapterOutcome.Command(command("schedule")) }
            val repository = FakeProjectionRepository(leases)
            val appliedKeys = mutableSetOf<String>()
            var handlerCalls = 0
            val worker =
                worker(
                    repository,
                    listOf(adapter),
                    commandHandler =
                        InstitutionalProjectionCommandHandler { _, applied, _ ->
                            handlerCalls += 1
                            appliedKeys += applied.idempotencyKey
                            repository.completeResults.add(handlerCalls > 1)
                        },
                )

            worker.processNext("tenant-a", "schedule:v1")
            worker.processNext("tenant-a", "schedule:v1")

            assertEquals(2, handlerCalls)
            assertEquals(1, appliedKeys.size)
            assertEquals("tenant-a:schedule:class-1:1", appliedKeys.single())
            assertEquals(InstitutionalProjectionStatus.PROJECTED, repository.status)
        }

    private fun worker(
        repository: FakeProjectionRepository,
        adapters: Collection<InstitutionalEventAdapter>,
        identityResolver: InstitutionalProjectionIdentityResolver = InstitutionalProjectionIdentityResolver { _, _ -> null },
        commandHandler: InstitutionalProjectionCommandHandler = InstitutionalProjectionCommandHandler { _, _, _ -> },
    ) = InstitutionalEventProjectionWorker(
        repository = repository,
        identityResolver = identityResolver,
        commandHandler = commandHandler,
        adapters = adapters,
        clock = clock,
    )

    private fun adapter(
        adapterId: String,
        transform: suspend (InstitutionalEventRecord) -> InstitutionalAdapterOutcome,
    ) = object : InstitutionalEventAdapter {
        override val adapterId: String = adapterId
        override val supportedSchemaVersions: Set<Int> = setOf(1)

        override suspend fun adapt(record: InstitutionalEventRecord): InstitutionalAdapterOutcome = transform(record)
    }

    private fun command(
        entityType: String,
        institutionId: String = "tenant-a",
        sourceVersion: Long = 1,
        identityReferences: List<InstitutionalIdentityReference> = emptyList(),
        consistency: InstitutionalProjectionConsistency = InstitutionalProjectionConsistency.SNAPSHOT,
    ) = InstitutionalProjectionCommand(
        institutionId = institutionId,
        entityType = entityType,
        externalId = if (entityType in setOf("student", "teacher")) "student-1" else "class-1",
        eventId = "event-1",
        sourceVersion = sourceVersion,
        schemaVersion = 1,
        operation = InstitutionalEventOperation.UPSERT,
        payload = buildJsonObject { put("name", JsonPrimitive("Math")) },
        identityReferences = identityReferences,
        consistency = consistency,
    )

    private fun record(
        entityType: String = "schedule",
        schemaVersion: Int = 1,
        sourceVersion: Long = 1,
    ) = InstitutionalEventRecord(
        tenantId = "tenant-a",
        sourceCredentialId = "credential-a",
        event =
            InstitutionalPushEvent(
                schemaVersion = schemaVersion,
                eventId = "event-1",
                entityType = entityType,
                externalId = if (entityType in setOf("student", "teacher")) "student-1" else "class-1",
                sourceVersion = sourceVersion,
                occurredAtEpochSeconds = now.epochSecond,
                operation = InstitutionalEventOperation.UPSERT,
                payload = buildJsonObject { put("name", JsonPrimitive("Math")) },
            ),
        payloadSha256 = "a".repeat(64),
        receivedAt = now,
    )

    private fun lease(
        adapterId: String,
        entityType: String = "schedule",
        schemaVersion: Int = 1,
        sourceVersion: Long = 1,
        token: String = "lease-token",
        record: InstitutionalEventRecord = record(entityType, schemaVersion, sourceVersion),
    ) = InstitutionalProjectionLease(
        record = record,
        adapterId = adapterId,
        leaseToken = token,
        leaseExpiresAt = now.plusSeconds(90),
        attemptCount = 1,
    )

    private class FakeProjectionRepository(
        vararg leases: InstitutionalProjectionLease,
    ) : InstitutionalProjectionRepository {
        private val queue = ArrayDeque(leases.toList())
        val appliedVersions = mutableMapOf<String, Long>()
        val completeResults = ArrayDeque<Boolean>()
        var status: InstitutionalProjectionStatus? = InstitutionalProjectionStatus.PROCESSING
        var failureCode: String? = null
        var retryAt: Instant? = null
        var retries = 0
        var completions = 0

        override suspend fun claimNext(
            tenantId: String,
            adapterId: String,
            now: Instant,
            leaseDurationSeconds: Long,
        ): InstitutionalProjectionLease? {
            return queue.removeFirstOrNull()?.takeIf { it.record.tenantId == tenantId && it.adapterId == adapterId }
        }

        override suspend fun renew(
            lease: InstitutionalProjectionLease,
            now: Instant,
            leaseDurationSeconds: Long,
        ) = true

        override suspend fun complete(lease: InstitutionalProjectionLease, now: Instant): Boolean {
            completions += 1
            val result = completeResults.removeFirstOrNull() ?: true
            if (result) {
                status = InstitutionalProjectionStatus.PROJECTED
                appliedVersions[key(lease.record)] = lease.record.event.sourceVersion
            }
            return result
        }

        override suspend fun advanceAppliedVersion(
            tenantId: String,
            entityType: String,
            externalId: String,
            sourceVersion: Long,
            now: Instant,
        ): Boolean {
            val key = "$tenantId:$entityType:$externalId"
            if ((appliedVersions[key] ?: 0L) >= sourceVersion) return false
            appliedVersions[key] = sourceVersion
            return true
        }

        override suspend fun findAppliedVersion(
            tenantId: String,
            entityType: String,
            externalId: String,
        ): Long? {
            return appliedVersions["$tenantId:$entityType:$externalId"]
        }

        override suspend fun reconcile(
            tenantId: String,
            adapterId: String,
            now: Instant,
        ) = 0L

        override suspend fun retry(
            lease: InstitutionalProjectionLease,
            now: Instant,
            retryAt: Instant,
            failureCode: String,
        ): Boolean {
            retries += 1
            this.retryAt = retryAt
            this.failureCode = failureCode
            status = InstitutionalProjectionStatus.RETRYABLE
            return true
        }

        override suspend fun fail(
            lease: InstitutionalProjectionLease,
            now: Instant,
            failureCode: String,
        ): Boolean {
            this.failureCode = failureCode
            status = InstitutionalProjectionStatus.FAILED
            return true
        }

        override suspend fun markPendingIdentity(
            lease: InstitutionalProjectionLease,
            now: Instant,
        ): Boolean {
            status = InstitutionalProjectionStatus.PENDING_IDENTITY
            return true
        }

        override suspend fun markUnsupported(
            lease: InstitutionalProjectionLease,
            now: Instant,
            failureCode: String,
        ): Boolean {
            this.failureCode = failureCode
            status = InstitutionalProjectionStatus.UNSUPPORTED
            return true
        }

        override suspend fun supersede(
            lease: InstitutionalProjectionLease,
            now: Instant,
        ): Boolean {
            status = InstitutionalProjectionStatus.SUPERSEDED
            return true
        }

        private fun key(record: InstitutionalEventRecord): String {
            return "${record.tenantId}:${record.event.entityType}:${record.event.externalId}"
        }
    }
}
