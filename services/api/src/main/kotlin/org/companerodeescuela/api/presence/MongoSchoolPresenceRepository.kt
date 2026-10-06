package org.companerodeescuela.api.presence

import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.gt
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.Sorts.descending
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bson.Document
import org.companerodeescuela.shared.contracts.NetworkVerificationMethod
import org.companerodeescuela.shared.contracts.SchoolPresenceResponse
import org.companerodeescuela.shared.contracts.SchoolPresenceStatus

class MongoSchoolPresenceRepository(
    database: MongoDatabase,
) : SchoolPresenceRepository {
    private val collection: MongoCollection<Document> =
        database.getCollection(COLLECTION)
    private val indexMutex = Mutex()

    @Volatile
    private var indexesReady = false

    override suspend fun save(value: SchoolPresenceResponse): SchoolPresenceResponse {
        ensureIndexes()
        collection.insertOne(value.toDocument())
        return value
    }

    override suspend fun findById(id: String): SchoolPresenceResponse? {
        ensureIndexes()
        return collection.find(eq("_id", id)).firstOrNull()?.toPresence()
    }

    override suspend fun findActiveForStudent(
        studentId: String,
        nowEpochSeconds: Long,
    ): SchoolPresenceResponse? {
        ensureIndexes()
        return collection.find(
            and(
                eq("studentId", studentId),
                eq("status", SchoolPresenceStatus.ACTIVE.name),
                gt("expiresAtEpochSeconds", nowEpochSeconds),
            ),
        )
            .sort(descending("startedAtEpochSeconds"))
            .firstOrNull()
            ?.toPresence()
    }

    override suspend fun replace(value: SchoolPresenceResponse) {
        ensureIndexes()
        collection.replaceOne(
            eq("_id", value.id),
            value.toDocument(),
            ReplaceOptions().upsert(false),
        )
    }

    private suspend fun ensureIndexes() {
        if (indexesReady) return
        indexMutex.withLock {
            if (indexesReady) return@withLock
            collection.createIndex(
                Indexes.compoundIndex(
                    Indexes.ascending("studentId"),
                    Indexes.ascending("status"),
                    Indexes.descending("expiresAtEpochSeconds"),
                ),
                IndexOptions().name("ix_school_presence_student_active"),
            )
            indexesReady = true
        }
    }

    private fun SchoolPresenceResponse.toDocument(): Document = Document()
        .append("_id", id)
        .append("studentId", studentId)
        .append("startedAtEpochSeconds", startedAtEpochSeconds)
        .append("expiresAtEpochSeconds", expiresAtEpochSeconds)
        .append("closedAtEpochSeconds", closedAtEpochSeconds)
        .append("status", status.name)
        .append("qrVerified", qrVerified)
        .append("networkVerified", networkVerified)
        .append("networkVerificationMethod", networkVerificationMethod.name)

    private fun Document.toPresence(): SchoolPresenceResponse =
        SchoolPresenceResponse(
            id = getString("_id"),
            studentId = getString("studentId"),
            startedAtEpochSeconds = requireLong("startedAtEpochSeconds"),
            expiresAtEpochSeconds = requireLong("expiresAtEpochSeconds"),
            closedAtEpochSeconds = numberAsLong("closedAtEpochSeconds"),
            status = SchoolPresenceStatus.valueOf(getString("status")),
            qrVerified = getBoolean("qrVerified", false),
            networkVerified = getBoolean("networkVerified", false),
            networkVerificationMethod = NetworkVerificationMethod.valueOf(
                getString("networkVerificationMethod"),
            ),
        )

    private fun Document.requireLong(name: String): Long =
        numberAsLong(name) ?: error("Missing numeric field: $name")

    private fun Document.numberAsLong(name: String): Long? =
        (get(name) as? Number)?.toLong()

    private companion object {
        const val COLLECTION = "school_presence_sessions"
    }
}
