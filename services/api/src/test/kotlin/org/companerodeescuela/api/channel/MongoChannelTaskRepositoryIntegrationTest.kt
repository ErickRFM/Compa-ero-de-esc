package org.companerodeescuela.api.channel

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.runBlocking
import org.companerodeescuela.shared.contracts.ChannelAttachment
import org.companerodeescuela.shared.contracts.ChannelAttachmentType
import org.companerodeescuela.shared.contracts.ChannelFileScope
import org.companerodeescuela.shared.contracts.ChannelFileState
import org.companerodeescuela.shared.contracts.ChannelPrivateFileMetadata
import org.companerodeescuela.shared.contracts.ChannelTask
import org.companerodeescuela.shared.contracts.TaskStatus
import org.companerodeescuela.shared.contracts.TaskSubmission
import org.companerodeescuela.shared.contracts.TaskSubmissionVersion
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(named = "V12_TEST_MONGODB_URI", matches = ".+")
@Timeout(120)
class MongoChannelTaskRepositoryIntegrationTest {
    @Test
    fun `tasks submissions versions and private file bytes persist with compare and set`() =
        database { db ->
            val repository = MongoChannelTaskRepository(db)
            val now = Instant.now().epochSecond
            val task =
                ChannelTask(
                    id = UUID.randomUUID().toString(),
                    channelId = "isolated-channel-a",
                    teacherId = "teacher-a",
                    teacherDisplayName = "Teacher A",
                    title = "Persistence check",
                    instructions = "Test-only data",
                    dueDateEpochSeconds = now + 3_600,
                    attachments = listOf(ChannelAttachment(ChannelAttachmentType.LINK, "Reference", "https://example.test")),
                    createdAtEpochSeconds = now,
                )
            assertTrue(repository.createTask(task))
            assertFalse(repository.createTask(task))
            assertEquals(task, repository.findTask(task.channelId, task.id))
            assertNull(repository.findTask("isolated-channel-b", task.id))

            val editedTask = task.copy(closed = true, version = 2)
            assertTrue(repository.replaceTask(editedTask, expectedVersion = 1))
            assertFalse(repository.replaceTask(task.copy(version = 3), expectedVersion = 1))
            assertEquals(2, repository.findTask(task.channelId, task.id)?.version)

            val firstVersion =
                TaskSubmissionVersion(
                    version = 1,
                    status = TaskStatus.SUBMITTED,
                    comment = "Initial",
                    changedBy = "student-a",
                    changedAtEpochSeconds = now,
                )
            val submission =
                TaskSubmission(
                    id = UUID.randomUUID().toString(),
                    taskId = task.id,
                    channelId = task.channelId,
                    studentId = "student-a",
                    studentDisplayName = "Student A",
                    status = TaskStatus.SUBMITTED,
                    comment = "Initial",
                    submittedAtEpochSeconds = now,
                    history = listOf(firstVersion),
                    idempotencyKeys = setOf("mongo-idempotency-key"),
                )
            assertTrue(repository.createSubmission(submission))
            assertFalse(repository.createSubmission(submission.copy(id = UUID.randomUUID().toString())))
            val returned =
                submission.copy(
                    status = TaskStatus.RETURNED,
                    feedback = "Add a conclusion",
                    version = 2,
                    history =
                        submission.history +
                            TaskSubmissionVersion(
                                version = 2,
                                status = TaskStatus.RETURNED,
                                comment = submission.comment,
                                feedback = "Add a conclusion",
                                changedBy = "teacher-a",
                                changedAtEpochSeconds = now + 10,
                            ),
                )
            assertTrue(repository.replaceSubmission(returned, expectedVersion = 1))
            assertFalse(repository.replaceSubmission(submission.copy(version = 2), expectedVersion = 1))
            val persisted = repository.findSubmission(task.channelId, task.id, submission.id)
            assertEquals(TaskStatus.RETURNED, persisted?.status)
            assertEquals(listOf(1, 2), persisted?.history?.map { it.version })
            assertEquals(setOf("mongo-idempotency-key"), persisted?.idempotencyKeys)
            assertNull(repository.findSubmission("isolated-channel-b", task.id, submission.id))

            val file =
                ChannelPrivateFileMetadata(
                    id = UUID.randomUUID().toString(),
                    channelId = task.channelId,
                    taskId = task.id,
                    submissionId = submission.id,
                    ownerId = "student-a",
                    fileName = "proof.txt",
                    contentType = "text/plain",
                    sizeBytes = 5,
                    uploadedBytes = 0,
                    state = ChannelFileState.UPLOADING,
                    scope = ChannelFileScope.SUBMISSION,
                    createdAtEpochSeconds = now,
                    downloadPath = "/channels/${task.channelId}/tasks/${task.id}/submissions/${submission.id}/files/proof",
                )
            assertTrue(repository.createFile(file))
            assertFalse(repository.completeFile(file.id, "other-student", "proof".toByteArray()))
            assertTrue(repository.completeFile(file.id, "student-a", "proof".toByteArray()))
            val storedFile = repository.findFile(file.id)
            assertEquals(ChannelFileState.AVAILABLE, storedFile?.metadata?.state)
            assertEquals(5, storedFile?.metadata?.uploadedBytes)
            assertContentEquals("proof".toByteArray(), storedFile?.bytes)
            assertEquals(listOf(file.id), repository.filesForSubmission(task.channelId, task.id, submission.id).map { it.id })
            assertTrue(repository.filesForSubmission("isolated-channel-b", task.id, submission.id).isEmpty())
        }

    private fun database(block: suspend (MongoDatabase) -> Unit) =
        runBlocking<Unit> {
            val uri = System.getenv("V12_TEST_MONGODB_URI")
            require(uri == "mongodb://127.0.0.1:27017") {
                "Mongo integration tests require the configured isolated local test server"
            }
            MongoClient.create(uri).use { client ->
                val database = client.getDatabase("channel_task_test_${UUID.randomUUID().toString().replace("-", "")}")
                try {
                    block(database)
                } finally {
                    database.drop()
                }
            }
        }
}
