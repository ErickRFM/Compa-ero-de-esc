package org.companerodeescuela.api.channel

import com.mongodb.MongoWriteException
import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bson.Document
import org.bson.types.Binary
import org.companerodeescuela.shared.contracts.ChannelAttachment
import org.companerodeescuela.shared.contracts.ChannelAttachmentType
import org.companerodeescuela.shared.contracts.ChannelFileScope
import org.companerodeescuela.shared.contracts.ChannelFileState
import org.companerodeescuela.shared.contracts.ChannelPrivateFileMetadata
import org.companerodeescuela.shared.contracts.ChannelTask
import org.companerodeescuela.shared.contracts.TaskStatus
import org.companerodeescuela.shared.contracts.TaskSubmission
import org.companerodeescuela.shared.contracts.TaskSubmissionVersion

class MongoChannelTaskRepository(
    database: MongoDatabase,
) : ChannelTaskRepository {
    private val tasks: MongoCollection<Document> = database.getCollection(TASKS)
    private val submissions: MongoCollection<Document> = database.getCollection(SUBMISSIONS)
    private val files: MongoCollection<Document> = database.getCollection(FILES)
    private val indexMutex = Mutex()

    @Volatile
    private var indexesReady = false

    override suspend fun listTasks(channelId: String): List<ChannelTask> {
        ensureIndexes()
        return tasks
            .find(eq("channelId", channelId))
            .toList()
            .map { it.toTask() }
            .sortedWith(compareByDescending<ChannelTask> { it.dueDateEpochSeconds }.thenByDescending { it.createdAtEpochSeconds })
    }

    override suspend fun findTask(
        channelId: String,
        taskId: String,
    ): ChannelTask? {
        ensureIndexes()
        return tasks.find(and(eq("_id", taskId), eq("channelId", channelId))).firstOrNull()?.toTask()
    }

    override suspend fun createTask(task: ChannelTask): Boolean {
        ensureIndexes()
        return try {
            tasks.insertOne(task.toDocument())
            true
        } catch (_: MongoWriteException) {
            false
        }
    }

    override suspend fun replaceTask(
        task: ChannelTask,
        expectedVersion: Int,
    ): Boolean {
        ensureIndexes()
        val result =
            tasks.replaceOne(
                and(eq("_id", task.id), eq("channelId", task.channelId), eq("version", expectedVersion)),
                task.toDocument(),
            )
        return result.matchedCount == 1L
    }

    override suspend fun listSubmissions(
        channelId: String,
        taskId: String,
    ): List<TaskSubmission> {
        ensureIndexes()
        return submissions
            .find(and(eq("channelId", channelId), eq("taskId", taskId)))
            .toList()
            .map { it.toSubmission() }
    }

    override suspend fun findSubmission(
        channelId: String,
        taskId: String,
        submissionId: String,
    ): TaskSubmission? {
        ensureIndexes()
        return submissions
            .find(
                and(eq("_id", submissionId), eq("channelId", channelId), eq("taskId", taskId)),
            ).firstOrNull()
            ?.toSubmission()
    }

    override suspend fun findSubmissionByStudent(
        taskId: String,
        studentId: String,
    ): TaskSubmission? {
        ensureIndexes()
        return submissions
            .find(and(eq("taskId", taskId), eq("studentId", studentId)))
            .firstOrNull()
            ?.toSubmission()
    }

    override suspend fun createSubmission(submission: TaskSubmission): Boolean {
        ensureIndexes()
        return try {
            submissions.insertOne(submission.toDocument())
            true
        } catch (_: MongoWriteException) {
            false
        }
    }

    override suspend fun replaceSubmission(
        submission: TaskSubmission,
        expectedVersion: Int,
    ): Boolean {
        ensureIndexes()
        val result =
            submissions.replaceOne(
                and(
                    eq("_id", submission.id),
                    eq("channelId", submission.channelId),
                    eq("taskId", submission.taskId),
                    eq("studentId", submission.studentId),
                    eq("version", expectedVersion),
                ),
                submission.toDocument(),
            )
        return result.matchedCount == 1L
    }

    override suspend fun createFile(file: ChannelPrivateFileMetadata): Boolean {
        ensureIndexes()
        return try {
            files.insertOne(file.toDocument())
            true
        } catch (_: MongoWriteException) {
            false
        }
    }

    override suspend fun findFile(fileId: String): StoredChannelFile? {
        ensureIndexes()
        val document = files.find(eq("_id", fileId)).firstOrNull() ?: return null
        val binary = document.get("content") as? Binary
        return StoredChannelFile(document.toFileMetadata(), binary?.data)
    }

    override suspend fun completeFile(
        fileId: String,
        ownerId: String,
        bytes: ByteArray,
    ): Boolean {
        ensureIndexes()
        val result =
            files.updateOne(
                and(eq("_id", fileId), eq("ownerId", ownerId), eq("state", ChannelFileState.UPLOADING.name)),
                Updates.combine(
                    Updates.set("uploadedBytes", bytes.size.toLong()),
                    Updates.set("state", ChannelFileState.AVAILABLE.name),
                    Updates.set("content", Binary(bytes)),
                ),
            )
        return result.matchedCount == 1L
    }

    override suspend fun filesForTask(
        channelId: String,
        taskId: String,
    ): List<ChannelPrivateFileMetadata> {
        ensureIndexes()
        return files
            .find(
                and(eq("channelId", channelId), eq("taskId", taskId), eq("scope", ChannelFileScope.TASK.name)),
            ).toList()
            .map { it.toFileMetadata() }
            .sortedBy { it.createdAtEpochSeconds }
    }

    override suspend fun filesForSubmission(
        channelId: String,
        taskId: String,
        submissionId: String,
    ): List<ChannelPrivateFileMetadata> {
        ensureIndexes()
        return files
            .find(
                and(
                    eq("channelId", channelId),
                    eq("taskId", taskId),
                    eq("submissionId", submissionId),
                    eq("scope", ChannelFileScope.SUBMISSION.name),
                ),
            ).toList()
            .map { it.toFileMetadata() }
            .sortedBy { it.createdAtEpochSeconds }
    }

    private suspend fun ensureIndexes() {
        if (indexesReady) return
        indexMutex.withLock {
            if (indexesReady) return@withLock
            tasks.createIndex(
                Indexes.compoundIndex(Indexes.ascending("channelId"), Indexes.descending("dueDateEpochSeconds")),
                IndexOptions().name("ix_channel_tasks_feed"),
            )
            submissions.createIndex(
                Indexes.compoundIndex(Indexes.ascending("taskId"), Indexes.ascending("studentId")),
                IndexOptions().unique(true).name("uq_channel_task_student_submission"),
            )
            submissions.createIndex(
                Indexes.compoundIndex(Indexes.ascending("channelId"), Indexes.ascending("taskId")),
                IndexOptions().name("ix_channel_task_submissions"),
            )
            files.createIndex(
                Indexes.compoundIndex(
                    Indexes.ascending("channelId"),
                    Indexes.ascending("taskId"),
                    Indexes.ascending("scope"),
                    Indexes.ascending("submissionId"),
                ),
                IndexOptions().name("ix_channel_private_file_scope"),
            )
            files.createIndex(Indexes.ascending("ownerId"), IndexOptions().name("ix_channel_private_file_owner"))
            indexesReady = true
        }
    }

    private fun ChannelTask.toDocument() =
        Document()
            .append("_id", id)
            .append("channelId", channelId)
            .append("teacherId", teacherId)
            .append("teacherDisplayName", teacherDisplayName)
            .append("title", title)
            .append("instructions", instructions)
            .append("dueDateEpochSeconds", dueDateEpochSeconds)
            .append("attachments", attachments.map { it.toDocument() })
            .append("createdAtEpochSeconds", createdAtEpochSeconds)
            .append("closed", closed)
            .append("version", version)

    private fun Document.toTask() =
        ChannelTask(
            id = getString("_id"),
            channelId = getString("channelId"),
            teacherId = getString("teacherId"),
            teacherDisplayName = getString("teacherDisplayName"),
            title = getString("title"),
            instructions = getString("instructions"),
            dueDateEpochSeconds = requireLong("dueDateEpochSeconds"),
            attachments = getList("attachments", Document::class.java).orEmpty().map { it.toAttachment() },
            createdAtEpochSeconds = requireLong("createdAtEpochSeconds"),
            closed = getBoolean("closed", false),
            version = getInteger("version", 1),
        )

    private fun TaskSubmission.toDocument() =
        Document()
            .append("_id", id)
            .append("taskId", taskId)
            .append("channelId", channelId)
            .append("studentId", studentId)
            .append("studentDisplayName", studentDisplayName)
            .append("status", status.name)
            .append("comment", comment)
            .append("attachments", attachments.map { it.toDocument() })
            .append("fileIds", fileIds)
            .append("submittedAtEpochSeconds", submittedAtEpochSeconds)
            .append("feedback", feedback)
            .append("reviewedAtEpochSeconds", reviewedAtEpochSeconds)
            .append("version", version)
            .append(
                "history",
                history.map {
                    Document()
                        .append("version", it.version)
                        .append("status", it.status.name)
                        .append("comment", it.comment)
                        .append("fileIds", it.fileIds)
                        .append("feedback", it.feedback)
                        .append("changedBy", it.changedBy)
                        .append("changedAtEpochSeconds", it.changedAtEpochSeconds)
                },
            ).append("idempotencyKeys", idempotencyKeys.toList())

    private fun Document.toSubmission() =
        TaskSubmission(
            id = getString("_id"),
            taskId = getString("taskId"),
            channelId = getString("channelId"),
            studentId = getString("studentId"),
            studentDisplayName = getString("studentDisplayName"),
            status = TaskStatus.valueOf(getString("status")),
            comment = getString("comment"),
            attachments = getList("attachments", Document::class.java).orEmpty().map { it.toAttachment() },
            submittedAtEpochSeconds = requireLong("submittedAtEpochSeconds"),
            feedback = getString("feedback"),
            reviewedAtEpochSeconds = numberAsLong("reviewedAtEpochSeconds"),
            version = getInteger("version", 1),
            fileIds = getList("fileIds", String::class.java).orEmpty(),
            history =
                getList("history", Document::class.java).orEmpty().map {
                    TaskSubmissionVersion(
                        version = it.getInteger("version"),
                        status = TaskStatus.valueOf(it.getString("status")),
                        comment = it.getString("comment"),
                        fileIds = it.getList("fileIds", String::class.java).orEmpty(),
                        feedback = it.getString("feedback"),
                        changedBy = it.getString("changedBy"),
                        changedAtEpochSeconds = it.requireLong("changedAtEpochSeconds"),
                    )
                },
            idempotencyKeys = getList("idempotencyKeys", String::class.java).orEmpty().toSet(),
        )

    private fun ChannelAttachment.toDocument() =
        Document()
            .append("type", type.name)
            .append("label", label)
            .append("url", url)

    private fun Document.toAttachment() =
        ChannelAttachment(
            type = ChannelAttachmentType.valueOf(getString("type")),
            label = getString("label"),
            url = getString("url"),
        )

    private fun ChannelPrivateFileMetadata.toDocument() =
        Document()
            .append("_id", id)
            .append("channelId", channelId)
            .append("taskId", taskId)
            .append("submissionId", submissionId)
            .append("ownerId", ownerId)
            .append("fileName", fileName)
            .append("contentType", contentType)
            .append("sizeBytes", sizeBytes)
            .append("uploadedBytes", uploadedBytes)
            .append("state", state.name)
            .append("scope", scope.name)
            .append("createdAtEpochSeconds", createdAtEpochSeconds)
            .append("downloadPath", downloadPath)

    private fun Document.toFileMetadata() =
        ChannelPrivateFileMetadata(
            id = getString("_id"),
            channelId = getString("channelId"),
            taskId = getString("taskId"),
            submissionId = getString("submissionId"),
            ownerId = getString("ownerId"),
            fileName = getString("fileName"),
            contentType = getString("contentType"),
            sizeBytes = requireLong("sizeBytes"),
            uploadedBytes = requireLong("uploadedBytes"),
            state = ChannelFileState.valueOf(getString("state")),
            scope = ChannelFileScope.valueOf(getString("scope")),
            createdAtEpochSeconds = requireLong("createdAtEpochSeconds"),
            downloadPath = getString("downloadPath"),
        )

    private fun Document.requireLong(name: String): Long =
        numberAsLong(name)
            ?: error("Missing numeric field: $name")

    private fun Document.numberAsLong(name: String): Long? = (get(name) as? Number)?.toLong()

    private companion object {
        const val TASKS = "class_channel_tasks"
        const val SUBMISSIONS = "class_channel_task_submissions"
        const val FILES = "class_channel_private_files"
    }
}
