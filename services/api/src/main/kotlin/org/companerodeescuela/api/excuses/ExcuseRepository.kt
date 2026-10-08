package org.companerodeescuela.api.excuses

import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.exists
import com.mongodb.client.model.Filters.or
import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.`in`
import com.mongodb.client.model.FindOneAndReplaceOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import java.time.Instant
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.toList
import org.bson.Document
import org.companerodeescuela.shared.contracts.ExcuseStatus

data class ExcuseRecord(
    val id: String,
    val studentId: String,
    val academicGroupId: String,
    val attendanceDateIso: String,
    val reason: String,
    val attachmentRefs: List<String>,
    val status: ExcuseStatus,
    val submittedAt: Instant,
    val reviewedAt: Instant? = null,
    val reviewedBy: String? = null,
    val reviewComment: String? = null,
    val version: Long = 0L,
)

interface ExcuseRepository {
    suspend fun create(record: ExcuseRecord): ExcuseRecord
    suspend fun find(id: String): ExcuseRecord?
    suspend fun update(record: ExcuseRecord): ExcuseRecord
    suspend fun compareAndUpdate(expected: ExcuseRecord, updated: ExcuseRecord): ExcuseRecord?
    suspend fun listForGroups(groupIds: Set<String>): List<ExcuseRecord>
    suspend fun listForStudent(studentId: String): List<ExcuseRecord>
    suspend fun listAll(): List<ExcuseRecord>
}

class InMemoryExcuseRepository : ExcuseRepository {
    private val records = ConcurrentHashMap<String, ExcuseRecord>()

    override suspend fun create(record: ExcuseRecord): ExcuseRecord {
        records[record.id] = record
        return record
    }

    override suspend fun find(id: String): ExcuseRecord? = records[id]

    override suspend fun update(record: ExcuseRecord): ExcuseRecord {
        records[record.id] = record
        return record
    }

    override suspend fun compareAndUpdate(expected: ExcuseRecord, updated: ExcuseRecord): ExcuseRecord? {
        var changed = false
        records.computeIfPresent(expected.id) { _, current ->
            if (current.version == expected.version && current.status == expected.status) {
                changed = true
                updated
            } else current
        }
        return if (changed) updated else null
    }

    override suspend fun listForGroups(groupIds: Set<String>): List<ExcuseRecord> =
        records.values.filter { it.academicGroupId in groupIds }
            .sortedByDescending(ExcuseRecord::submittedAt)

    override suspend fun listForStudent(studentId: String): List<ExcuseRecord> =
        records.values.filter { it.studentId == studentId }.sortedByDescending(ExcuseRecord::submittedAt)

    override suspend fun listAll(): List<ExcuseRecord> =
        records.values.sortedByDescending(ExcuseRecord::submittedAt)
}

class MongoExcuseRepository(database: MongoDatabase) : ExcuseRepository {
    private val collection: MongoCollection<Document> = database.getCollection("excuse_requests")

    override suspend fun create(record: ExcuseRecord): ExcuseRecord {
        collection.insertOne(record.toDocument())
        return record
    }

    override suspend fun find(id: String): ExcuseRecord? =
        collection.find(eq("_id", id)).toList().firstOrNull()?.toRecord()

    override suspend fun update(record: ExcuseRecord): ExcuseRecord {
        collection.replaceOne(eq("_id", record.id), record.toDocument())
        return record
    }

    override suspend fun compareAndUpdate(expected: ExcuseRecord, updated: ExcuseRecord): ExcuseRecord? {
        val versionGate = if (expected.version == 0L) {
            or(eq("version", 0L), exists("version", false))
        } else {
            eq("version", expected.version)
        }
        return collection.findOneAndReplace(
            and(eq("_id", expected.id), eq("status", expected.status.name), versionGate),
            updated.toDocument(),
            FindOneAndReplaceOptions().returnDocument(ReturnDocument.AFTER),
        )?.toRecord()
    }

    override suspend fun listForGroups(groupIds: Set<String>): List<ExcuseRecord> =
        if (groupIds.isEmpty()) emptyList() else collection.find(`in`("academicGroupId", groupIds))
            .toList().map { it.toRecord() }.sortedByDescending(ExcuseRecord::submittedAt)

    override suspend fun listForStudent(studentId: String): List<ExcuseRecord> =
        collection.find(eq("studentId", studentId)).toList()
            .map { it.toRecord() }
            .sortedByDescending(ExcuseRecord::submittedAt)

    override suspend fun listAll(): List<ExcuseRecord> =
        collection.find().toList()
            .map { it.toRecord() }
            .sortedByDescending(ExcuseRecord::submittedAt)

    private fun ExcuseRecord.toDocument(): Document =
        Document()
            .append("_id", id)
            .append("studentId", studentId)
            .append("academicGroupId", academicGroupId)
            .append("attendanceDateIso", attendanceDateIso)
            .append("reason", reason)
            .append("attachmentRefs", attachmentRefs)
            .append("status", status.name)
            .append("submittedAt", Date.from(submittedAt))
            .append("reviewedAt", reviewedAt?.let(Date::from))
            .append("reviewedBy", reviewedBy)
            .append("reviewComment", reviewComment)
            .append("version", version)

    private fun Document.toRecord(): ExcuseRecord =
        ExcuseRecord(
            id = getString("_id"),
            studentId = getString("studentId"),
            academicGroupId = getString("academicGroupId"),
            attendanceDateIso = getString("attendanceDateIso"),
            reason = getString("reason"),
            attachmentRefs = getList("attachmentRefs", String::class.java) ?: emptyList(),
            status = runCatching { ExcuseStatus.valueOf(getString("status")) }.getOrDefault(ExcuseStatus.PENDING),
            submittedAt = getDate("submittedAt")?.toInstant() ?: Instant.EPOCH,
            reviewedAt = getDate("reviewedAt")?.toInstant(),
            reviewedBy = getString("reviewedBy"),
            reviewComment = getString("reviewComment"),
            version = (get("version") as? Number)?.toLong() ?: 0L,
        )
}
