package org.companerodeescuela.api.grading

import com.mongodb.MongoWriteException
import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bson.Document
import org.companerodeescuela.shared.contracts.GradebookCategory
import org.companerodeescuela.shared.contracts.GradebookRevision
import org.companerodeescuela.shared.contracts.GradebookRow
import org.companerodeescuela.shared.contracts.GradebookSnapshot
import org.companerodeescuela.shared.contracts.GradebookStatus

interface GradebookRepository {
    suspend fun find(
        classroomId: String,
        gradingPeriod: String,
    ): GradebookSnapshot?

    /** Atomically inserts a new draft or replaces the matching current draft revision. */
    suspend fun saveDraft(
        snapshot: GradebookSnapshot,
        expectedRevision: Long?,
    ): Boolean

    /** Publishes only the expected draft revision and returns the stored published snapshot. */
    suspend fun publish(
        classroomId: String,
        gradingPeriod: String,
        expectedRevision: Long,
        changedBy: String,
        changedAtEpochSeconds: Long,
    ): GradebookSnapshot?
}

class InMemoryGradebookRepository : GradebookRepository {
    private val mutex = Mutex()
    private val records = mutableMapOf<Key, GradebookSnapshot>()

    override suspend fun find(
        classroomId: String,
        gradingPeriod: String,
    ): GradebookSnapshot? = mutex.withLock { records[Key(classroomId, gradingPeriod)] }

    override suspend fun saveDraft(
        snapshot: GradebookSnapshot,
        expectedRevision: Long?,
    ): Boolean =
        mutex.withLock {
            val key = Key(snapshot.classroomId, snapshot.gradingPeriod)
            val current = records[key]
            if (expectedRevision == null) {
                if (current != null) return@withLock false
            } else if (current?.revision != expectedRevision || current.status != GradebookStatus.DRAFT) {
                return@withLock false
            }
            records[key] = snapshot
            true
        }

    override suspend fun publish(
        classroomId: String,
        gradingPeriod: String,
        expectedRevision: Long,
        changedBy: String,
        changedAtEpochSeconds: Long,
    ): GradebookSnapshot? =
        mutex.withLock {
            val key = Key(classroomId, gradingPeriod)
            val current = records[key] ?: return@withLock null
            if (current.revision != expectedRevision || current.status != GradebookStatus.DRAFT) return@withLock null
            current
                .copy(
                    status = GradebookStatus.PUBLISHED,
                    updatedAtEpochSeconds = changedAtEpochSeconds,
                    publishedAtEpochSeconds = changedAtEpochSeconds,
                    history =
                        current.history +
                            GradebookRevision(
                                revision = current.revision,
                                status = GradebookStatus.PUBLISHED,
                                changedBy = changedBy,
                                changedAtEpochSeconds = changedAtEpochSeconds,
                            ),
                ).also { records[key] = it }
        }

    private data class Key(
        val classroomId: String,
        val gradingPeriod: String,
    )
}

class MongoGradebookRepository(
    database: MongoDatabase,
) : GradebookRepository {
    private val collection = database.getCollection<Document>("gradebooks")
    private val indexMutex = Mutex()
    private var indexesReady = false

    override suspend fun find(
        classroomId: String,
        gradingPeriod: String,
    ): GradebookSnapshot? {
        ensureIndexes()
        return collection
            .find(and(eq("classroomId", classroomId), eq("gradingPeriod", gradingPeriod)))
            .firstOrNull()
            ?.toSnapshot()
    }

    override suspend fun saveDraft(
        snapshot: GradebookSnapshot,
        expectedRevision: Long?,
    ): Boolean {
        ensureIndexes()
        val document = snapshot.toDocument()
        if (expectedRevision == null) {
            return try {
                collection.insertOne(document)
                true
            } catch (error: MongoWriteException) {
                if (error.error.code == 11000) false else throw error
            }
        }
        return collection
            .replaceOne(
                and(
                    eq("classroomId", snapshot.classroomId),
                    eq("gradingPeriod", snapshot.gradingPeriod),
                    eq("revision", expectedRevision),
                    eq("status", GradebookStatus.DRAFT.name),
                ),
                document,
                ReplaceOptions().upsert(false),
            ).modifiedCount == 1L
    }

    override suspend fun publish(
        classroomId: String,
        gradingPeriod: String,
        expectedRevision: Long,
        changedBy: String,
        changedAtEpochSeconds: Long,
    ): GradebookSnapshot? {
        ensureIndexes()
        val revision =
            Document()
                .append("revision", expectedRevision)
                .append("status", GradebookStatus.PUBLISHED.name)
                .append("changedBy", changedBy)
                .append("changedAtEpochSeconds", changedAtEpochSeconds)
                .append("categories", emptyList<Document>())
                .append("rows", emptyList<Document>())
        return collection
            .findOneAndUpdate(
                and(
                    eq("classroomId", classroomId),
                    eq("gradingPeriod", gradingPeriod),
                    eq("revision", expectedRevision),
                    eq("status", GradebookStatus.DRAFT.name),
                ),
                Updates.combine(
                    Updates.set("status", GradebookStatus.PUBLISHED.name),
                    Updates.set("updatedAtEpochSeconds", changedAtEpochSeconds),
                    Updates.set("publishedAtEpochSeconds", changedAtEpochSeconds),
                    Updates.push("history", revision),
                ),
                FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
            )?.toSnapshot()
    }

    private suspend fun ensureIndexes() =
        indexMutex.withLock {
            if (indexesReady) return@withLock
            collection.createIndex(
                Indexes.ascending("classroomId", "gradingPeriod"),
                IndexOptions().unique(true).name("ux_gradebook_class_period"),
            )
            collection.createIndex(Indexes.ascending("teacherId"), IndexOptions().name("ix_gradebook_teacher"))
            indexesReady = true
        }
}

private fun GradebookSnapshot.toDocument(): Document =
    Document("classroomId", classroomId)
        .append("academicPeriodId", academicPeriodId)
        .append("gradingPeriod", gradingPeriod)
        .append("teacherId", teacherId)
        .append("revision", revision)
        .append("status", status.name)
        .append("categories", categories.map { Document("id", it.id).append("name", it.name).append("weightPercent", it.weightPercent) })
        .append(
            "rows",
            rows.map { row ->
                Document("studentId", row.studentId).append("scores", Document(row.scores))
            },
        ).append("updatedAtEpochSeconds", updatedAtEpochSeconds)
        .append("publishedAtEpochSeconds", publishedAtEpochSeconds)
        .append(
            "history",
            history.map {
                Document("revision", it.revision)
                    .append("status", it.status.name)
                    .append("changedBy", it.changedBy)
                    .append("changedAtEpochSeconds", it.changedAtEpochSeconds)
                    .append(
                        "categories",
                        it.categories.map { category ->
                            Document("id", category.id).append("name", category.name).append("weightPercent", category.weightPercent)
                        },
                    ).append(
                        "rows",
                        it.rows.map { row ->
                            Document("studentId", row.studentId).append("scores", Document(row.scores))
                        },
                    )
            },
        )

private fun Document.toSnapshot(): GradebookSnapshot =
    GradebookSnapshot(
        classroomId = getString("classroomId"),
        academicPeriodId = getString("academicPeriodId"),
        gradingPeriod = getString("gradingPeriod"),
        teacherId = getString("teacherId"),
        revision = getLong("revision"),
        status = GradebookStatus.valueOf(getString("status")),
        categories =
            getList("categories", Document::class.java).orEmpty().map {
                GradebookCategory(it.getString("id"), it.getString("name"), it.getDouble("weightPercent"))
            },
        rows =
            getList("rows", Document::class.java).orEmpty().map { row ->
                val scoreDocument = row.get("scores", Document::class.java) ?: Document()
                GradebookRow(
                    row.getString("studentId"),
                    scoreDocument.entries.associate { (key, value) ->
                        key to (value as? Number)?.toDouble()
                    },
                )
            },
        updatedAtEpochSeconds = getLong("updatedAtEpochSeconds"),
        publishedAtEpochSeconds = getLong("publishedAtEpochSeconds"),
        history =
            getList("history", Document::class.java).orEmpty().map {
                GradebookRevision(
                    revision = it.getLong("revision"),
                    status = GradebookStatus.valueOf(it.getString("status")),
                    changedBy = it.getString("changedBy"),
                    changedAtEpochSeconds = it.getLong("changedAtEpochSeconds"),
                    categories =
                        it.getList("categories", Document::class.java).orEmpty().map { category ->
                            GradebookCategory(category.getString("id"), category.getString("name"), category.getDouble("weightPercent"))
                        },
                    rows =
                        it.getList("rows", Document::class.java).orEmpty().map { row ->
                            val scoreDocument = row.get("scores", Document::class.java) ?: Document()
                            GradebookRow(
                                row.getString("studentId"),
                                scoreDocument.entries.associate { (key, value) ->
                                    key to (value as? Number)?.toDouble()
                                },
                            )
                        },
                )
            },
    )
