package org.companerodeescuela.api.channel

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.companerodeescuela.shared.contracts.ChannelFileScope
import org.companerodeescuela.shared.contracts.ChannelPrivateFileMetadata
import org.companerodeescuela.shared.contracts.ChannelTask
import org.companerodeescuela.shared.contracts.TaskSubmission

data class StoredChannelFile(
    val metadata: ChannelPrivateFileMetadata,
    val bytes: ByteArray? = null,
)

interface ChannelTaskRepository {
    suspend fun listTasks(channelId: String): List<ChannelTask>

    suspend fun findTask(
        channelId: String,
        taskId: String,
    ): ChannelTask?

    suspend fun createTask(task: ChannelTask): Boolean

    suspend fun replaceTask(
        task: ChannelTask,
        expectedVersion: Int,
    ): Boolean

    suspend fun listSubmissions(
        channelId: String,
        taskId: String,
    ): List<TaskSubmission>

    suspend fun findSubmission(
        channelId: String,
        taskId: String,
        submissionId: String,
    ): TaskSubmission?

    suspend fun findSubmissionByStudent(
        taskId: String,
        studentId: String,
    ): TaskSubmission?

    suspend fun createSubmission(submission: TaskSubmission): Boolean

    suspend fun replaceSubmission(
        submission: TaskSubmission,
        expectedVersion: Int,
    ): Boolean

    suspend fun createFile(file: ChannelPrivateFileMetadata): Boolean

    suspend fun findFile(fileId: String): StoredChannelFile?

    suspend fun completeFile(
        fileId: String,
        ownerId: String,
        bytes: ByteArray,
    ): Boolean

    suspend fun filesForTask(
        channelId: String,
        taskId: String,
    ): List<ChannelPrivateFileMetadata>

    suspend fun filesForSubmission(
        channelId: String,
        taskId: String,
        submissionId: String,
    ): List<ChannelPrivateFileMetadata>
}

class InMemoryChannelTaskRepository : ChannelTaskRepository {
    private val mutex = Mutex()
    private val tasks = linkedMapOf<String, ChannelTask>()
    private val submissions = linkedMapOf<String, TaskSubmission>()
    private val files = linkedMapOf<String, StoredChannelFile>()

    override suspend fun listTasks(channelId: String): List<ChannelTask> =
        mutex.withLock {
            tasks.values
                .filter { it.channelId == channelId }
                .sortedWith(compareByDescending<ChannelTask> { it.dueDateEpochSeconds }.thenByDescending { it.createdAtEpochSeconds })
        }

    override suspend fun findTask(
        channelId: String,
        taskId: String,
    ): ChannelTask? =
        mutex.withLock {
            tasks[taskId]?.takeIf { it.channelId == channelId }
        }

    override suspend fun createTask(task: ChannelTask): Boolean =
        mutex.withLock {
            if (tasks.containsKey(task.id)) return@withLock false
            tasks[task.id] = task
            true
        }

    override suspend fun replaceTask(
        task: ChannelTask,
        expectedVersion: Int,
    ): Boolean =
        mutex.withLock {
            val current = tasks[task.id] ?: return@withLock false
            if (current.channelId != task.channelId || current.version != expectedVersion) return@withLock false
            tasks[task.id] = task
            true
        }

    override suspend fun listSubmissions(
        channelId: String,
        taskId: String,
    ): List<TaskSubmission> =
        mutex.withLock {
            submissions.values
                .filter { it.channelId == channelId && it.taskId == taskId }
                .sortedByDescending { it.submittedAtEpochSeconds }
        }

    override suspend fun findSubmission(
        channelId: String,
        taskId: String,
        submissionId: String,
    ): TaskSubmission? =
        mutex.withLock {
            submissions[submissionId]?.takeIf { it.channelId == channelId && it.taskId == taskId }
        }

    override suspend fun findSubmissionByStudent(
        taskId: String,
        studentId: String,
    ): TaskSubmission? =
        mutex.withLock {
            submissions.values.firstOrNull { it.taskId == taskId && it.studentId == studentId }
        }

    override suspend fun createSubmission(submission: TaskSubmission): Boolean =
        mutex.withLock {
            if (submissions.containsKey(submission.id) ||
                submissions.values.any { it.taskId == submission.taskId && it.studentId == submission.studentId }
            ) {
                return@withLock false
            }
            submissions[submission.id] = submission
            true
        }

    override suspend fun replaceSubmission(
        submission: TaskSubmission,
        expectedVersion: Int,
    ): Boolean =
        mutex.withLock {
            val current = submissions[submission.id] ?: return@withLock false
            if (current.channelId != submission.channelId ||
                current.taskId != submission.taskId ||
                current.studentId != submission.studentId ||
                current.version != expectedVersion
            ) {
                return@withLock false
            }
            submissions[submission.id] = submission
            true
        }

    override suspend fun createFile(file: ChannelPrivateFileMetadata): Boolean =
        mutex.withLock {
            if (files.containsKey(file.id)) return@withLock false
            files[file.id] = StoredChannelFile(file)
            true
        }

    override suspend fun findFile(fileId: String): StoredChannelFile? = mutex.withLock { files[fileId] }

    override suspend fun completeFile(
        fileId: String,
        ownerId: String,
        bytes: ByteArray,
    ): Boolean =
        mutex.withLock {
            val current = files[fileId] ?: return@withLock false
            if (current.metadata.ownerId != ownerId ||
                current.metadata.state != org.companerodeescuela.shared.contracts.ChannelFileState.UPLOADING
            ) {
                return@withLock false
            }
            if (current.metadata.sizeBytes != bytes.size.toLong()) return@withLock false
            files[fileId] =
                StoredChannelFile(
                    current.metadata.copy(
                        uploadedBytes = bytes.size.toLong(),
                        state = org.companerodeescuela.shared.contracts.ChannelFileState.AVAILABLE,
                    ),
                    bytes.copyOf(),
                )
            true
        }

    override suspend fun filesForTask(
        channelId: String,
        taskId: String,
    ): List<ChannelPrivateFileMetadata> =
        mutex.withLock {
            files.values
                .map { it.metadata }
                .filter { it.channelId == channelId && it.taskId == taskId && it.scope == ChannelFileScope.TASK }
                .sortedBy { it.createdAtEpochSeconds }
        }

    override suspend fun filesForSubmission(
        channelId: String,
        taskId: String,
        submissionId: String,
    ): List<ChannelPrivateFileMetadata> =
        mutex.withLock {
            files.values
                .map { it.metadata }
                .filter {
                    it.channelId == channelId &&
                        it.taskId == taskId &&
                        it.submissionId == submissionId &&
                        it.scope == ChannelFileScope.SUBMISSION
                }.sortedBy { it.createdAtEpochSeconds }
        }
}
