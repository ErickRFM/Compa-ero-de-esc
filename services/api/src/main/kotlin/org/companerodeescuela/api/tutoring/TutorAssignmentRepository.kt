package org.companerodeescuela.api.tutoring

import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
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
)

interface TutorAssignmentRepository {
    suspend fun create(record: TutorAssignmentRecord): TutorAssignmentRecord
    suspend fun listForTutor(tutorUserId: String): List<TutorAssignmentRecord>
    suspend fun listAll(): List<TutorAssignmentRecord>
    suspend fun findActive(tutorUserId: String, academicGroupId: String): TutorAssignmentRecord?
}

class InMemoryTutorAssignmentRepository : TutorAssignmentRepository {
    private val records = ConcurrentHashMap<String, TutorAssignmentRecord>()

    override suspend fun create(record: TutorAssignmentRecord): TutorAssignmentRecord {
        val existing = findActive(record.tutorUserId, record.academicGroupId)
        if (existing != null) return existing
        records[record.id] = record
        return record
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
}

class MongoTutorAssignmentRepository(database: MongoDatabase) : TutorAssignmentRepository {
    private val collection: MongoCollection<Document> = database.getCollection("tutor_assignments")
    private val indexMutex = Mutex()

    @Volatile
    private var indexesReady = false

    override suspend fun create(record: TutorAssignmentRecord): TutorAssignmentRecord {
        ensureIndexes()
        findActive(record.tutorUserId, record.academicGroupId)?.let { return it }
        collection.insertOne(record.toDocument())
        return record
    }

    override suspend fun listForTutor(tutorUserId: String): List<TutorAssignmentRecord> {
        ensureIndexes()
        return collection.find(and(eq("tutorUserId", tutorUserId), eq("active", true)))
            .toList().map(Document::toRecord)
            .sortedByDescending(TutorAssignmentRecord::assignedAt)
    }

    override suspend fun listAll(): List<TutorAssignmentRecord> {
        ensureIndexes()
        return collection.find().toList().map(Document::toRecord)
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

    private fun Document.toRecord(): TutorAssignmentRecord =
        TutorAssignmentRecord(
            id = getString("_id"),
            tutorUserId = getString("tutorUserId"),
            academicGroupId = getString("academicGroupId"),
            active = getBoolean("active", true),
            assignedBy = getString("assignedBy"),
            assignedAt = getDate("assignedAt")?.toInstant() ?: Instant.EPOCH,
        )
}
