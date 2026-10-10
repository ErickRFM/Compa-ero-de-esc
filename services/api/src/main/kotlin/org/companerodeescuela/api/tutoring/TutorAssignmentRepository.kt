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
import org.companerodeescuela.api.errors.ApiException

data class TutorAssignmentRecord(
    val id: String,
    val tutorUserId: String,
    val academicGroupId: String,
    val active: Boolean,
    val assignedBy: String,
    val assignedAt: Instant,
    val revokedAt: Instant? = null,
    val revokedBy: String? = null,
    val institutionId: String? = null,
    val periodId: String? = null,
    val startsAt: Instant? = null,
    val endsAt: Instant? = null,
    val tutorRoleGeneration: Long = -1,
    val revocationOperationId: String? = null,
    val revocationDigest: String? = null,
)

interface TutorAssignmentRepository {
    suspend fun create(record: TutorAssignmentRecord): TutorAssignmentRecord
    suspend fun listForTutor(tutorUserId: String): List<TutorAssignmentRecord>
    suspend fun listAll(): List<TutorAssignmentRecord>
    suspend fun findActive(tutorUserId: String, academicGroupId: String): TutorAssignmentRecord?
    suspend fun findById(id: String): TutorAssignmentRecord?
    suspend fun revoke(id: String, actorId: String, now: Instant, operationId: String? = null, scopeDigest: String? = null): TutorAssignmentRecord?
}

class InMemoryTutorAssignmentRepository : TutorAssignmentRepository {
    private val records = ConcurrentHashMap<String, TutorAssignmentRecord>()

    private val mutationMutex = Mutex()

    override suspend fun create(record: TutorAssignmentRecord): TutorAssignmentRecord = mutationMutex.withLock {
        records[record.id]?.let { return@withLock it.exactRetry(record) }
        records.values.firstOrNull { it.tutorUserId == record.tutorUserId &&
            it.academicGroupId == record.academicGroupId && it.active }?.let { return@withLock it.exactRetry(record) }
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

    override suspend fun revoke(id: String, actorId: String, now: Instant, operationId: String?, scopeDigest: String?): TutorAssignmentRecord? =
        mutationMutex.withLock {
            val previous = records[id] ?: return@withLock null
            val updated = if (previous.active) previous.copy(
                active = false, revokedBy = actorId, revokedAt = now, revocationOperationId = operationId, revocationDigest = scopeDigest,
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
        findById(record.id)?.let { return it.exactRetry(record) }
        findActive(record.tutorUserId, record.academicGroupId)?.let { return it.exactRetry(record) }
        return try {
            collection.insertOne(record.toDocument())
            record
        } catch (error: com.mongodb.MongoWriteException) {
            if(error.error.code!=11000) throw error
            val current=findById(record.id) ?: findActive(record.tutorUserId,record.academicGroupId) ?: throw error
            current.exactRetry(record)
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

    override suspend fun revoke(id: String, actorId: String, now: Instant, operationId: String?, scopeDigest: String?): TutorAssignmentRecord? {
        ensureIndexes()
        return collection.findOneAndUpdate(
            and(eq("_id", id), eq("active", true)),
            Updates.combine(
                Updates.set("active", false),
                Updates.set("revokedAt", Date.from(now)),
                Updates.set("revokedBy", actorId),
                Updates.set("revocationOperationId", operationId), Updates.set("revocationDigest", scopeDigest),
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
            .append("institutionId", institutionId).append("periodId", periodId)
            .append("startsAt", startsAt?.let(Date::from)).append("endsAt", endsAt?.let(Date::from))
            .append("tutorRoleGeneration", tutorRoleGeneration)
            .append("revocationOperationId", revocationOperationId).append("revocationDigest", revocationDigest)

    private fun Document.toRecord(): TutorAssignmentRecord =
        TutorAssignmentRecord(
            id = getString("_id"),
            tutorUserId = getString("tutorUserId"),
            academicGroupId = getString("academicGroupId"),
            active = get("active") == true,
            assignedBy = getString("assignedBy"),
            assignedAt = getDate("assignedAt")?.toInstant() ?: Instant.EPOCH,
            revokedAt = getDate("revokedAt")?.toInstant(),
            revokedBy = getString("revokedBy"),
            institutionId = getString("institutionId"), periodId = getString("periodId"),
            startsAt = (get("startsAt") as? Date)?.toInstant(), endsAt = (get("endsAt") as? Date)?.toInstant(),
            tutorRoleGeneration = when(val generation=get("tutorRoleGeneration")) { is Long->generation;is Int->generation.toLong();else-> -1L },
            revocationOperationId = getString("revocationOperationId"), revocationDigest = getString("revocationDigest"),
        )
}

/** Idempotency belongs to an exact operation; an old period or revoked ID cannot be reused. */
private fun TutorAssignmentRecord.exactRetry(request:TutorAssignmentRecord):TutorAssignmentRecord {
    if(id!=request.id || tutorUserId!=request.tutorUserId || academicGroupId!=request.academicGroupId ||
        !active || !request.active || revokedAt!=null || request.revokedAt!=null || revokedBy!=null || request.revokedBy!=null ||
        assignedBy!=request.assignedBy || institutionId!=request.institutionId || periodId!=request.periodId ||
        startsAt!=request.startsAt || endsAt!=request.endsAt || tutorRoleGeneration!=request.tutorRoleGeneration)
        throw ApiException.Conflict("Existing tutor assignment belongs to another operation or scope; revoke it explicitly before reassignment")
    return this
}
