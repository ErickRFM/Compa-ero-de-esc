package org.companerodeescuela.api.tutoring

import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Updates
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import java.time.Instant
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bson.Document

data class TutorAssignmentRecord(
    val id: String,
    val tutorUserId: String,
    val academicGroupId: String,
    val active: Boolean,
    val assignedBy: String,
    val assignedAt: Instant,
    val revokedAt: Instant? = null,
    val revokedBy: String? = null,
)

interface TutorAssignmentRepository {
    suspend fun create(record: TutorAssignmentRecord): TutorAssignmentRecord
    suspend fun listForTutor(tutorUserId: String): List<TutorAssignmentRecord>
    suspend fun listAll(): List<TutorAssignmentRecord>
    suspend fun findActive(tutorUserId: String, academicGroupId: String): TutorAssignmentRecord?
    suspend fun findById(id: String): TutorAssignmentRecord?
    suspend fun revoke(id: String, actorId: String, now: Instant): TutorAssignmentRecord?
}

class InMemoryTutorAssignmentRepository : TutorAssignmentRepository {
    private val records = ConcurrentHashMap<String, TutorAssignmentRecord>()

    private val mutationMutex = Mutex()

    override suspend fun create(record: TutorAssignmentRecord): TutorAssignmentRecord = mutationMutex.withLock {
        records.values.firstOrNull { it.tutorUserId == record.tutorUserId &&
            it.academicGroupId == record.academicGroupId && it.active }?.let { return@withLock it }
        records[record.id] = record
        record
    }

    override suspend fun listForTutor(tutorUserId: String): List<TutorAssignmentRecord> =
        records.values.filter { it.tutorUserId == tutorUserId && it.active }
            .sortedByDescending(TutorAssignmentRecord::assignedAt)

    override suspend fun listAll(): List<TutorAssignmentRecord> =
        records.values.sortedByDescending(TutorAssignmentRecord::assignedAt)

    override suspend fun findActive(tutorUserId: String, academicGroupId: String): TutorAssignmentRecord? =
        records.values.firstOrNull {
            it.tutorUserId == tutorUserId && it.academicGroupId == academicGroupId && it.active
        }

    override suspend fun findById(id: String): TutorAssignmentRecord? = records[id]

    override suspend fun revoke(id: String, actorId: String, now: Instant): TutorAssignmentRecord? =
        mutationMutex.withLock {
            val previous = records[id] ?: return@withLock null
            val updated = if (previous.active) previous.copy(
                active = false, revokedBy = actorId, revokedAt = now,
            ) else previous
            records[id] = updated
            updated
        }
}

class MongoTutorAssignmentRepository(database: MongoDatabase) : TutorAssignmentRepository {
    private val collection: MongoCollection<Document> = database.getCollection("tutor_assignments")
    private val indexMutex = Mutex()

    @Volatile
    private var indexesReady = false

    override suspend fun create(record: TutorAssignmentRecord): TutorAssignmentRecord {
        ensureIndexes()
        findActive(record.tutorUserId, record.academicGroupId)?.let { return it }
        return try {
            collection.insertOne(record.toDocument())
            record
        } catch (error: com.mongodb.MongoWriteException) {
            findActive(record.tutorUserId, record.academicGroupId) ?: throw error
        }
    }

    override suspend fun listForTutor(tutorUserId: String): List<TutorAssignmentRecord> {
        ensureIndexes()
        return collection.find(and(eq("tutorUserId", tutorUserId), eq("active", true)))
            .toList().map { it.toRecord() }
            .sortedByDescending(TutorAssignmentRecord::assignedAt)
    }

    override suspend fun listAll(): List<TutorAssignmentRecord> {
        ensureIndexes()
        return collection.find().toList().map { it.toRecord() }
            .sortedByDescending(TutorAssignmentRecord::assignedAt)
    }

    override suspend fun findActive(tutorUserId: String, academicGroupId: String): TutorAssignmentRecord? {
        ensureIndexes()
        return collection.find(
            and(
                eq("tutorUserId", tutorUserId),
                eq("academicGroupId", academicGroupId),
                eq("active", true),
            ),
        ).firstOrNull()?.toRecord()
    }

    override suspend fun findById(id: String): TutorAssignmentRecord? {
        ensureIndexes()
        return collection.find(eq("_id", id)).firstOrNull()?.toRecord()
    }

    override suspend fun revoke(id: String, actorId: String, now: Instant): TutorAssignmentRecord? {
        ensureIndexes()
        return collection.findOneAndUpdate(
            and(eq("_id", id), eq("active", true)),
            Updates.combine(
                Updates.set("active", false),
                Updates.set("revokedAt", Date.from(now)),
                Updates.set("revokedBy", actorId),
            ),
            FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
        )?.toRecord() ?: findById(id)
    }

    private suspend fun ensureIndexes() {
        if (indexesReady) return
        indexMutex.withLock {
            if (indexesReady) return@withLock
            collection.createIndex(
                Indexes.compoundIndex(
                    Indexes.ascending("tutorUserId"),
                    Indexes.ascending("academicGroupId"),
                    Indexes.ascending("active"),
                ),
                IndexOptions().name("ix_tutor_assignment_scope"),
            )
            collection.createIndex(
                Indexes.compoundIndex(
                    Indexes.ascending("tutorUserId"), Indexes.ascending("academicGroupId"),
                ),
                IndexOptions().unique(true).partialFilterExpression(eq("active", true))
                    .name("uq_tutor_assignment_active"),
            )
            indexesReady = true
        }
    }

    private fun TutorAssignmentRecord.toDocument(): Document =
        Document()
            .append("_id", id)
            .append("tutorUserId", tutorUserId)
            .append("academicGroupId", academicGroupId)
            .append("active", active)
            .append("assignedBy", assignedBy)
            .append("assignedAt", Date.from(assignedAt))
            .append("revokedAt", revokedAt?.let(Date::from))
            .append("revokedBy", revokedBy)

    private fun Document.toRecord(): TutorAssignmentRecord =
        TutorAssignmentRecord(
            id = getString("_id"),
            tutorUserId = getString("tutorUserId"),
            academicGroupId = getString("academicGroupId"),
            active = getBoolean("active", true),
            assignedBy = getString("assignedBy"),
            assignedAt = getDate("assignedAt")?.toInstant() ?: Instant.EPOCH,
            revokedAt = getDate("revokedAt")?.toInstant(),
            revokedBy = getString("revokedBy"),
        )
}
