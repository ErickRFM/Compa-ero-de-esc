package org.companerodeescuela.api.integrations.academic.siii

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.companerodeescuela.api.auth.PlatformAccountRepository
import org.companerodeescuela.api.institutions.InstitutionRepository
import org.companerodeescuela.api.integrations.push.InstitutionalAdapterOutcome
import org.companerodeescuela.api.integrations.push.InstitutionalEventAdapter
import org.companerodeescuela.api.integrations.push.InstitutionalEventRecord
import org.companerodeescuela.api.integrations.push.InstitutionalIdentityLinkService
import org.companerodeescuela.api.integrations.push.InstitutionalIdentityReference
import org.companerodeescuela.api.integrations.push.InstitutionalProjectionCommand
import org.companerodeescuela.api.integrations.push.InstitutionalProjectionCommandHandler
import org.companerodeescuela.api.integrations.push.InstitutionalProjectionConsistency
import org.companerodeescuela.api.integrations.push.InstitutionalProjectionPermanentFailure
import org.companerodeescuela.shared.contracts.InstitutionalEventOperation
import java.time.Clock
import java.time.Instant

data class StoredSiiiSection(
    val institutionId: String,
    val sectionId: String,
    val sourceVersion: Long,
    val eventId: String,
    val digest: String,
    val snapshot: SiiiSectionSnapshot?,
    val syncedAt: Instant,
)

interface SiiiSectionSnapshotRepository {
    suspend fun find(
        institutionId: String,
        sectionId: String,
    ): StoredSiiiSection?

    suspend fun apply(value: StoredSiiiSection)
}

class SiiiSectionEventAdapter : InstitutionalEventAdapter {
    override val adapterId = "group:v1"
    override val supportedSchemaVersions = setOf(1)

    override suspend fun adapt(record: InstitutionalEventRecord): InstitutionalAdapterOutcome {
        val event = record.event
        if (event.entityType != "group" || event.schemaVersion != 1) return InstitutionalAdapterOutcome.UnsupportedContract
        if (event.operation == InstitutionalEventOperation.UPSERT &&
            (event.payload["contract"] as? JsonPrimitive)?.content != SiiiSectionSnapshotCodec.CONTRACT
        ) {
            return InstitutionalAdapterOutcome.UnsupportedContract
        }
        val payload =
            try {
                SiiiSectionSnapshotCodec.uuid(event.externalId)
                if (event.operation == InstitutionalEventOperation.DELETE) {
                    require(event.payload.isEmpty())
                    event.payload
                } else {
                    Json.parseToJsonElement(
                        SiiiSectionSnapshotCodec.canonical(SiiiSectionSnapshotCodec.decode(event.payload, event.externalId)),
                    ) as JsonObject
                }
            } catch (_: IllegalArgumentException) {
                return InstitutionalAdapterOutcome.PermanentFailure("INVALID_SIII_SNAPSHOT")
            }
        return InstitutionalAdapterOutcome.Command(
            InstitutionalProjectionCommand(
                record.tenantId,
                event.entityType,
                event.externalId,
                event.eventId,
                event.sourceVersion,
                event.schemaVersion,
                event.operation,
                payload,
            ),
        )
    }
}

class SiiiSectionProjectionHandler(
    private val repository: SiiiSectionSnapshotRepository,
    private val clock: Clock = Clock.systemUTC(),
) : InstitutionalProjectionCommandHandler {
    override suspend fun apply(
        adapterId: String,
        command: InstitutionalProjectionCommand,
        resolvedNativeUserIds: Map<InstitutionalIdentityReference, String>,
    ) {
        if (adapterId != "group:v1" ||
            command.entityType != "group" ||
            command.schemaVersion != 1 ||
            command.identityReferences.isNotEmpty() ||
            resolvedNativeUserIds.isNotEmpty() ||
            command.consistency != InstitutionalProjectionConsistency.SNAPSHOT
        ) {
            throw InstitutionalProjectionPermanentFailure("UNSUPPORTED_SIII_COMMAND")
        }
        val snapshot =
            try {
                SiiiSectionSnapshotCodec.uuid(command.externalId)
                if (command.operation == InstitutionalEventOperation.DELETE) {
                    require(command.payload.isEmpty())
                    null
                } else {
                    SiiiSectionSnapshotCodec.decode(command.payload, command.externalId)
                }
            } catch (_: IllegalArgumentException) {
                throw InstitutionalProjectionPermanentFailure("INVALID_SIII_SNAPSHOT")
            }
        repository.apply(
            StoredSiiiSection(
                command.institutionId,
                command.externalId,
                command.sourceVersion,
                command.eventId,
                snapshot?.let(SiiiSectionSnapshotCodec::digest) ?: TOMBSTONE_DIGEST,
                snapshot,
                clock.instant(),
            ),
        )
    }

    internal companion object {
        val TOMBSTONE_DIGEST = SiiiSectionSnapshotCodec.sha256("siii.academic_section_snapshot.v1:deleted")
    }
}

/** Concrete institutional-only graph. The reader has no HTTP/mobile route in this slice. */
class SiiiSectionProjectionFeature(
    database: MongoDatabase,
    accounts: PlatformAccountRepository,
    institutions: InstitutionRepository,
    identities: InstitutionalIdentityLinkService,
    clock: Clock = Clock.systemUTC(),
) {
    val repository = MongoSiiiSectionSnapshotRepository(database)
    val adapter = SiiiSectionEventAdapter()
    val handler = SiiiSectionProjectionHandler(repository, clock)
    val reader = SiiiSectionReader(accounts, institutions, identities, repository, clock)
}
