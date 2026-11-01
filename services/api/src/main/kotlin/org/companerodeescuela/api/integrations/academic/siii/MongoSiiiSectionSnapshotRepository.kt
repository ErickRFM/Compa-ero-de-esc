package org.companerodeescuela.api.integrations.academic.siii

import com.mongodb.MongoException
import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.bson.Document
import org.companerodeescuela.api.integrations.push.InstitutionalProjectionPermanentFailure
import org.companerodeescuela.api.integrations.push.InstitutionalProjectionTransientFailure
import java.util.Date

class MongoSiiiSectionSnapshotRepository(
    database: MongoDatabase,
) : SiiiSectionSnapshotRepository {
    private val sections = database.getCollection<Document>("siii_institutional_section_snapshots")

    override suspend fun find(
        institutionId: String,
        sectionId: String,
    ): StoredSiiiSection? = sections.find(identity(institutionId, sectionId)).firstOrNull()?.stored()

    override suspend fun apply(value: StoredSiiiSection) = applyFenced(value, null)

    override suspend fun applyExpected(
        value: StoredSiiiSection,
        expectedPreviousVersion: Long,
    ) = applyFenced(value, expectedPreviousVersion)

    private suspend fun applyFenced(
        value: StoredSiiiSection,
        expectedPreviousVersion: Long?,
    ) {
        require(
            value.institutionId.isNotBlank() &&
                value.institutionId.length <= 128 &&
                value.institutionId == value.institutionId.trim() &&
                value.institutionId.none(Char::isISOControl),
        )
        SiiiSectionSnapshotCodec.uuid(value.sectionId)
        require(value.sourceVersion > 0 && value.eventId.isNotBlank() && value.eventId.length <= 128)
        val canonical =
            value.snapshot?.let { snapshot ->
                val payload = Json.parseToJsonElement(SiiiSectionSnapshotCodec.canonical(snapshot)) as JsonObject
                SiiiSectionSnapshotCodec.canonical(SiiiSectionSnapshotCodec.decode(payload, value.sectionId))
            }
        val expectedDigest = canonical?.let(SiiiSectionSnapshotCodec::sha256) ?: SiiiSectionProjectionHandler.TOMBSTONE_DIGEST
        require(value.digest == expectedDigest)
        val replacement =
            Document("_id", key(value.institutionId, value.sectionId))
                .append("institutionId", value.institutionId)
                .append("source", "siii")
                .append("sectionId", value.sectionId)
                .append("schemaVersion", 1)
                .append("sourceVersion", value.sourceVersion)
                .append("eventId", value.eventId)
                .append("digest", expectedDigest)
                .append("snapshotJson", canonical)
                .append("tombstone", canonical == null)
                .append("syncedAt", Date.from(value.syncedAt))
                .append("notificationOwners", value.notificationOwners)
                .append("notificationsQueued", value.notificationsQueued)

        // The target CAS also fences workers whose inbox lease expired after validation.
        repeat(32) {
            val current = sections.find(identity(value.institutionId, value.sectionId)).firstOrNull()
            if (current != null) {
                val currentVersion = current.getLong("sourceVersion")
                if (currentVersion > value.sourceVersion) return
                if (currentVersion == value.sourceVersion) {
                    if (current.getString("digest") !=
                        expectedDigest
                    ) {
                        throw InstitutionalProjectionPermanentFailure("SOURCE_VERSION_CONFLICT")
                    }
                    return
                }
                if (expectedPreviousVersion != null && currentVersion != expectedPreviousVersion) {
                    throw InstitutionalProjectionTransientFailure("SNAPSHOT_RECIPIENT_REVISION_CHANGED")
                }
                val result =
                    sections.replaceOne(
                        and(identity(value.institutionId, value.sectionId), eq("sourceVersion", currentVersion)),
                        replacement,
                    )
                if (result.matchedCount == 1L) return
            } else {
                if (expectedPreviousVersion != null && expectedPreviousVersion != 0L) {
                    throw InstitutionalProjectionTransientFailure("SNAPSHOT_RECIPIENT_REVISION_CHANGED")
                }
                try {
                    sections.insertOne(replacement)
                    return
                } catch (error: MongoException) {
                    if (error.code != 11000) throw error
                }
            }
        }
        throw InstitutionalProjectionTransientFailure("SNAPSHOT_CAS_CONTENTION")
    }

    override suspend fun markNotificationsQueued(
        institutionId: String,
        sectionId: String,
        sourceVersion: Long,
        eventId: String,
    ) {
        sections.updateOne(
            and(identity(institutionId, sectionId), eq("sourceVersion", sourceVersion), eq("eventId", eventId)),
            com.mongodb.client.model.Updates
                .set("notificationsQueued", true),
        )
    }

    private fun identity(
        institutionId: String,
        sectionId: String,
    ) = and(eq("_id", key(institutionId, sectionId)), eq("institutionId", institutionId), eq("source", "siii"), eq("sectionId", sectionId))

    private fun key(
        institutionId: String,
        sectionId: String,
    ): String = JsonArray(listOf(institutionId, "siii", sectionId).map(::JsonPrimitive)).toString()

    private fun Document.stored(): StoredSiiiSection {
        check(getInteger("schemaVersion") == 1)
        val sectionId = getString("sectionId")
        val payload = getString("snapshotJson")
        check(getBoolean("tombstone") == (payload == null))
        val snapshot = payload?.let { SiiiSectionSnapshotCodec.decode(Json.parseToJsonElement(it) as JsonObject, sectionId) }
        val digest = getString("digest")
        check(digest == (snapshot?.let(SiiiSectionSnapshotCodec::digest) ?: SiiiSectionProjectionHandler.TOMBSTONE_DIGEST))
        return StoredSiiiSection(
            getString("institutionId"),
            sectionId,
            getLong("sourceVersion"),
            getString("eventId"),
            digest,
            snapshot,
            getDate("syncedAt").toInstant(),
            getList("notificationOwners", String::class.java).orEmpty(),
            getBoolean("notificationsQueued", false),
        )
    }
}
