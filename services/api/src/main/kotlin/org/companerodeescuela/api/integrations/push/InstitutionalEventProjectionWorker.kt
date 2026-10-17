package org.companerodeescuela.api.integrations.push

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import org.companerodeescuela.shared.contracts.InstitutionalEventOperation
import org.companerodeescuela.shared.contracts.InstitutionalIdentityKind
import java.time.Clock
import java.time.Instant

data class InstitutionalIdentityReference(
    val kind: InstitutionalIdentityKind,
    val externalId: String,
) {
    init {
        require(externalId.isNotBlank() && externalId.length <= 256 && externalId.none(Char::isISOControl))
    }
}

enum class InstitutionalProjectionConsistency {
    SNAPSHOT,
    CONTIGUOUS_DELTA,
}

/** Adapter command containing only source identifiers and existing native identity references. */
data class InstitutionalProjectionCommand(
    val institutionId: String,
    val entityType: String,
    val externalId: String,
    val eventId: String,
    val sourceVersion: Long,
    val schemaVersion: Int,
    val operation: InstitutionalEventOperation,
    val payload: JsonObject,
    val identityReferences: List<InstitutionalIdentityReference> = emptyList(),
    val consistency: InstitutionalProjectionConsistency = InstitutionalProjectionConsistency.SNAPSHOT,
) {
    init {
        require(institutionId.isNotBlank() && institutionId.length <= 128)
        require(entityType.isNotBlank() && externalId.isNotBlank() && externalId.length <= 256)
        require(eventId.isNotBlank() && eventId.length <= 128)
        require(sourceVersion > 0 && schemaVersion > 0)
        require(identityReferences.distinct().size == identityReferences.size)
    }

    val idempotencyKey: String
        get() = "$institutionId:$entityType:$externalId:$sourceVersion"
}

sealed interface InstitutionalAdapterOutcome {
    data class Command(
        val value: InstitutionalProjectionCommand,
    ) : InstitutionalAdapterOutcome

    data object PendingIdentity : InstitutionalAdapterOutcome

    data object UnsupportedContract : InstitutionalAdapterOutcome

    data class PermanentFailure(
        val failureCode: String,
    ) : InstitutionalAdapterOutcome
}

interface InstitutionalEventAdapter {
    val adapterId: String
    val supportedSchemaVersions: Set<Int>

    suspend fun adapt(record: InstitutionalEventRecord): InstitutionalAdapterOutcome
}

fun interface InstitutionalProjectionIdentityResolver {
    suspend fun resolve(
        institutionId: String,
        reference: InstitutionalIdentityReference,
    ): String?
}

fun interface InstitutionalProjectionCommandHandler {
    /**
     * Apply the command idempotently using command.idempotencyKey. Implementations must not
     * create user accounts and must prevent a lower sourceVersion from overwriting newer data.
     */
    suspend fun apply(
        adapterId: String,
        command: InstitutionalProjectionCommand,
        resolvedNativeUserIds: Map<InstitutionalIdentityReference, String>,
    )
}

class InstitutionalProjectionTransientFailure(
    val failureCode: String,
) : RuntimeException()

class InstitutionalProjectionPermanentFailure(
    val failureCode: String,
) : RuntimeException()

/** One claimed event per invocation keeps lease ownership and retries independently recoverable. */
class InstitutionalEventProjectionWorker(
    private val repository: InstitutionalProjectionRepository,
    private val identityResolver: InstitutionalProjectionIdentityResolver,
    private val commandHandler: InstitutionalProjectionCommandHandler,
    adapters: Collection<InstitutionalEventAdapter>,
    private val clock: Clock = Clock.systemUTC(),
    private val leaseDurationSeconds: Long = DEFAULT_LEASE_SECONDS,
    private val leaseRenewalIntervalMillis: Long = leaseDurationSeconds * 1_000 / 3,
) {
    private val adaptersById = adapters.associateBy(InstitutionalEventAdapter::adapterId)

    init {
        require(leaseDurationSeconds in MIN_LEASE_SECONDS..MAX_LEASE_SECONDS)
        require(leaseRenewalIntervalMillis in 1 until leaseDurationSeconds * 1_000)
        require(adaptersById.size == adapters.size) { "Adapter IDs must be unique" }
        adapters.forEach { adapter ->
            require(adapter.adapterId.isNotBlank())
            require(adapter.supportedSchemaVersions.isNotEmpty() && adapter.supportedSchemaVersions.all { it > 0 })
        }
    }

    suspend fun processNext(
        tenantId: String,
        adapterId: String,
    ): Boolean {
        val claimedAt = clock.instant()
        val lease = repository.claimNext(tenantId, adapterId, claimedAt, leaseDurationSeconds) ?: return false
        return coroutineScope {
            val workerContext = currentCoroutineContext()
            val renewalJob =
                launch {
                    while (isActive) {
                        delay(leaseRenewalIntervalMillis)
                        if (!repository.renew(lease, clock.instant(), leaseDurationSeconds)) {
                            workerContext.cancel(CancellationException("Institutional projection lease was lost"))
                            return@launch
                        }
                    }
                }
            try {
                processClaimedLease(lease, adapterId)
            } finally {
                renewalJob.cancel()
            }
        }
    }

    suspend fun processNextAvailable(): Boolean {
        val scope = repository.nextScope(clock.instant()) ?: return false
        return processNext(scope.tenantId, scope.adapterId)
    }

    suspend fun runForever(pollIntervalMillis: Long = DEFAULT_POLL_INTERVAL_MILLIS) {
        require(pollIntervalMillis > 0)
        while (currentCoroutineContext().isActive) {
            try {
                if (!processNextAvailable()) delay(pollIntervalMillis)
            } catch (cancelled: CancellationException) {
                if (!currentCoroutineContext().isActive) throw cancelled
                delay(pollIntervalMillis)
            } catch (_: Exception) {
                delay(pollIntervalMillis)
            }
        }
    }

    private suspend fun processClaimedLease(
        lease: InstitutionalProjectionLease,
        adapterId: String,
    ): Boolean {
        val event = lease.record.event
        try {
            val appliedVersion =
                repository.findAppliedVersion(
                    tenantId = lease.record.tenantId,
                    entityType = event.entityType,
                    externalId = event.externalId,
                )
            if (appliedVersion != null && appliedVersion >= event.sourceVersion) {
                repository.supersede(lease, clock.instant())
                return true
            }

            val adapter = adaptersById[adapterId]
            if (adapter == null) {
                repository.markUnsupported(lease, clock.instant(), ADAPTER_UNAVAILABLE)
                return true
            }
            if (adapter.adapterId != lease.adapterId || event.schemaVersion !in adapter.supportedSchemaVersions) {
                repository.markUnsupported(lease, clock.instant(), UNSUPPORTED_SCHEMA)
                return true
            }

            when (val outcome = adapter.adapt(lease.record)) {
                InstitutionalAdapterOutcome.PendingIdentity -> {
                    repository.markPendingIdentity(lease, clock.instant())
                    return true
                }
                InstitutionalAdapterOutcome.UnsupportedContract -> {
                    repository.markUnsupported(lease, clock.instant(), UNSUPPORTED_CONTRACT)
                    return true
                }
                is InstitutionalAdapterOutcome.PermanentFailure -> {
                    repository.fail(lease, clock.instant(), safeFailureCode(outcome.failureCode))
                    return true
                }
                is InstitutionalAdapterOutcome.Command ->
                    applyCommand(
                        lease = lease,
                        adapter = adapter,
                        command = outcome.value,
                    )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: InstitutionalProjectionPermanentFailure) {
            repository.fail(lease, clock.instant(), safeFailureCode(failure.failureCode))
        } catch (failure: InstitutionalProjectionTransientFailure) {
            repository.retry(
                lease,
                clock.instant(),
                retryAt(clock.instant(), lease.attemptCount),
                safeFailureCode(failure.failureCode),
            )
        } catch (_: Exception) {
            repository.retry(
                lease,
                clock.instant(),
                retryAt(clock.instant(), lease.attemptCount),
                ADAPTER_EXECUTION_FAILED,
            )
        }
        return true
    }

    private suspend fun applyCommand(
        lease: InstitutionalProjectionLease,
        adapter: InstitutionalEventAdapter,
        command: InstitutionalProjectionCommand,
    ) {
        val event = lease.record.event
        if (command.institutionId != lease.record.tenantId ||
            command.entityType != event.entityType ||
            command.externalId != event.externalId ||
            command.eventId != event.eventId ||
            command.sourceVersion != event.sourceVersion ||
            command.schemaVersion != event.schemaVersion ||
            command.operation != event.operation
        ) {
            repository.fail(lease, clock.instant(), COMMAND_IDENTITY_MISMATCH)
            return
        }

        val currentVersion =
            repository.findAppliedVersion(
                tenantId = lease.record.tenantId,
                entityType = event.entityType,
                externalId = event.externalId,
            )
        if (currentVersion != null && currentVersion >= event.sourceVersion) {
            repository.supersede(lease, clock.instant())
            return
        }
        val expectedPreviousVersion = currentVersion ?: 0L
        if (command.consistency == InstitutionalProjectionConsistency.CONTIGUOUS_DELTA &&
            event.sourceVersion != expectedPreviousVersion + 1
        ) {
            val now = clock.instant()
            repository.retry(lease, now, retryAt(now, lease.attemptCount), VERSION_GAP)
            return
        }

        val requiredIdentity =
            when (event.entityType) {
                "student" -> InstitutionalIdentityReference(InstitutionalIdentityKind.STUDENT, event.externalId)
                "teacher" -> InstitutionalIdentityReference(InstitutionalIdentityKind.TEACHER, event.externalId)
                else -> null
            }
        if (requiredIdentity != null && requiredIdentity !in command.identityReferences) {
            if (command.identityReferences.any { it.kind == requiredIdentity.kind }) {
                repository.fail(lease, clock.instant(), COMMAND_IDENTITY_MISMATCH)
            } else {
                repository.markPendingIdentity(lease, clock.instant())
            }
            return
        }

        val resolved = linkedMapOf<InstitutionalIdentityReference, String>()
        for (reference in command.identityReferences) {
            val nativeUserId = identityResolver.resolve(lease.record.tenantId, reference)
            if (nativeUserId == null) {
                repository.markPendingIdentity(lease, clock.instant())
                return
            }
            resolved[reference] = nativeUserId
        }
        if (event.entityType in LINKED_USER_ENTITY_TYPES && command.identityReferences.isEmpty()) {
            repository.markPendingIdentity(lease, clock.instant())
            return
        }

        commandHandler.apply(adapter.adapterId, command, resolved)
        repository.complete(lease, clock.instant())
    }

    private fun retryAt(
        now: Instant,
        attemptCount: Int,
    ): Instant {
        val exponent = (attemptCount - 1).coerceIn(0, MAX_BACKOFF_EXPONENT)
        val delaySeconds = (INITIAL_RETRY_SECONDS * (1L shl exponent)).coerceAtMost(MAX_RETRY_SECONDS)
        return now.plusSeconds(delaySeconds)
    }

    private fun safeFailureCode(value: String): String = value.takeIf { SAFE_FAILURE_CODE.matches(it) } ?: ADAPTER_EXECUTION_FAILED

    private companion object {
        const val DEFAULT_LEASE_SECONDS = 90L
        const val MIN_LEASE_SECONDS = 1L
        const val MAX_LEASE_SECONDS = 900L
        const val INITIAL_RETRY_SECONDS = 5L
        const val MAX_RETRY_SECONDS = 900L
        const val MAX_BACKOFF_EXPONENT = 7
        const val DEFAULT_POLL_INTERVAL_MILLIS = 5_000L
        const val ADAPTER_UNAVAILABLE = "ADAPTER_UNAVAILABLE"
        const val UNSUPPORTED_SCHEMA = "UNSUPPORTED_SCHEMA"
        const val UNSUPPORTED_CONTRACT = "UNSUPPORTED_CONTRACT"
        const val ADAPTER_EXECUTION_FAILED = "ADAPTER_EXECUTION_FAILED"
        const val COMMAND_IDENTITY_MISMATCH = "COMMAND_IDENTITY_MISMATCH"
        const val VERSION_GAP = "VERSION_GAP"
        val SAFE_FAILURE_CODE = Regex("[A-Z0-9_]{1,64}")
        val LINKED_USER_ENTITY_TYPES = setOf("student", "teacher")
    }
}
