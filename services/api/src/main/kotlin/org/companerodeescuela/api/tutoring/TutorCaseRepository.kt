package org.companerodeescuela.api.tutoring

import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.FindOneAndReplaceOptions
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.ReturnDocument
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
import org.companerodeescuela.shared.contracts.TutorCaseStatus
import org.companerodeescuela.shared.contracts.TutorNoteVisibility

data class TutorCaseNoteRecord(
    val id: String,
    val body: String,
    val visibility: TutorNoteVisibility,
    val authorId: String,
    val createdAt: Instant,
)

data class TutorCaseRecord(
    val id: String,
    val academicGroupId: String,
    val studentId: String,
    val summary: String,
    val status: TutorCaseStatus,
    val createdBy: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val notes: List<TutorCaseNoteRecord> = emptyList(),
    val version: Long = 0L,
)

interface TutorCaseRepository {
    suspend fun create(value: TutorCaseRecord): TutorCaseRecord
    suspend fun find(id: String): TutorCaseRecord?
    suspend fun listForGroups(ids: Set<String>): List<TutorCaseRecord>
    suspend fun listForStudent(id: String): List<TutorCaseRecord>
    suspend fun listAll(): List<TutorCaseRecord>
    suspend fun compareAndUpdate(expected: TutorCaseRecord, updated: TutorCaseRecord): TutorCaseRecord?
}

class InMemoryTutorCaseRepository : TutorCaseRepository {
    private val data = ConcurrentHashMap<String, TutorCaseRecord>()
    override suspend fun create(value: TutorCaseRecord): TutorCaseRecord {
        data[value.id] = value
        return value
    }

    override suspend fun find(id: String): TutorCaseRecord? = data[id]

    override suspend fun listForGroups(ids: Set<String>): List<TutorCaseRecord> =
        data.values.filter { it.academicGroupId in ids }.sortedByDescending(TutorCaseRecord::updatedAt)

    override suspend fun listForStudent(id: String): List<TutorCaseRecord> =
        data.values.filter { it.studentId == id }.sortedByDescending(TutorCaseRecord::updatedAt)

    override suspend fun listAll(): List<TutorCaseRecord> =
        data.values.sortedByDescending(TutorCaseRecord::updatedAt)

    override suspend fun compareAndUpdate(expected: TutorCaseRecord, updated: TutorCaseRecord): TutorCaseRecord? {
        var changed = false
        data.computeIfPresent(expected.id) { _, previous ->
            if (previous.version == expected.version) {
                changed = true
                updated
            } else previous
        }
        return if (changed) updated else null
    }
}

class MongoTutorCaseRepository(database: MongoDatabase) : TutorCaseRepository {
    private val collection: MongoCollection<Document> = database.getCollection("tutoring_cases")
    private val mutex = Mutex()
    @Volatile private var indexesReady = false

    override suspend fun create(value: TutorCaseRecord): TutorCaseRecord {
        ensureIndexes()
        collection.insertOne(value.toDocument())
        return value
    }

    override suspend fun find(id: String): TutorCaseRecord? {
        ensureIndexes()
        return collection.find(eq("_id", id)).firstOrNull()?.toRecord()
    }

    override suspend fun listForGroups(ids: Set<String>): List<TutorCaseRecord> {
        ensureIndexes()
        if (ids.isEmpty()) return emptyList()
        return collection.find(com.mongodb.client.model.Filters.`in`("academicGroupId", ids)).toList()
            .map { it.toRecord() }.sortedByDescending(TutorCaseRecord::updatedAt)
    }

    override suspend fun listForStudent(id: String): List<TutorCaseRecord> {
        ensureIndexes()
        return collection.find(eq("studentId", id)).toList()
            .map { it.toRecord() }.sortedByDescending(TutorCaseRecord::updatedAt)
    }

    override suspend fun listAll(): List<TutorCaseRecord> {
        ensureIndexes()
        return collection.find().toList()
            .map { it.toRecord() }.sortedByDescending(TutorCaseRecord::updatedAt)
    }

    override suspend fun compareAndUpdate(expected: TutorCaseRecord, updated: TutorCaseRecord): TutorCaseRecord? {
        ensureIndexes()
        return collection.findOneAndReplace(
            and(eq("_id", expected.id), eq("version", expected.version)),
            updated.toDocument(),
            FindOneAndReplaceOptions().returnDocument(ReturnDocument.AFTER),
        )?.toRecord()
    }

    private suspend fun ensureIndexes() {
        if (indexesReady) return
        mutex.withLock {
            if (indexesReady) return@withLock
            collection.createIndex(Indexes.ascending("academicGroupId"),
                IndexOptions().name("ix_tutoring_case_group"))
            collection.createIndex(Indexes.ascending("studentId"),
                IndexOptions().name("ix_tutoring_case_student"))
            indexesReady = true
        }
    }

    private fun TutorCaseRecord.toDocument(): Document = Document()
        .append("_id", id)
        .append("academicGroupId", academicGroupId)
        .append("studentId", studentId)
        .append("summary", summary)
        .append("status", status.name)
        .append("createdBy", createdBy)
        .append("createdAt", Date.from(createdAt))
        .append("updatedAt", Date.from(updatedAt))
        .append("version", version)
        .append("notes", notes.map { note ->
            Document()
                .append("id", note.id)
                .append("body", note.body)
                .append("visibility", note.visibility.name)
                .append("authorId", note.authorId)
                .append("createdAt", Date.from(note.createdAt))
        })

    private fun Document.toRecord(): TutorCaseRecord = TutorCaseRecord(
        id = getString("_id"),
        academicGroupId = getString("academicGroupId"),
        studentId = getString("studentId"),
        summary = getString("summary"),
        status = TutorCaseStatus.valueOf(getString("status")),
        createdBy = getString("createdBy"),
        createdAt = getDate("createdAt").toInstant(),
        updatedAt = getDate("updatedAt").toInstant(),
        version = (get("version") as? Number)?.toLong() ?: 0L,
        notes = (getList("notes", Document::class.java) ?: emptyList()).map { note ->
            TutorCaseNoteRecord(
                id = note.getString("id"),
                body = note.getString("body"),
                visibility = TutorNoteVisibility.valueOf(note.getString("visibility")),
                authorId = note.getString("authorId"),
                createdAt = note.getDate("createdAt").toInstant(),
            )
        },
    )
}
