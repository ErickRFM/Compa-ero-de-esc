package org.companerodeescuela.api.channel

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.mock.MockAcademicProvider
import org.companerodeescuela.shared.contracts.ChannelFileScope
import org.companerodeescuela.shared.contracts.ChannelFileState
import org.companerodeescuela.shared.contracts.CreateChannelFileRequest
import org.companerodeescuela.shared.contracts.CreateChannelTaskRequest
import org.companerodeescuela.shared.contracts.ReviewSubmissionRequest
import org.companerodeescuela.shared.contracts.SubmitTaskRequest
import org.companerodeescuela.shared.contracts.TaskStatus
import org.companerodeescuela.shared.contracts.UpdateChannelTaskRequest
import org.companerodeescuela.shared.contracts.UserRole
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChannelTaskServiceTest {
    private val repository = InMemoryChannelTaskRepository()
    private val service = ChannelTaskService(repository, ChannelAccessPolicy(MockAcademicProvider()))
    private val dueAt get() = Instant.now().epochSecond + 3_600

    @Test
    fun `task endpoints enforce channel membership and teacher ownership`() =
        runTest {
            val task =
                service.createTask(
                    userId = TEACHER,
                    displayName = "Elena",
                    roles = setOf(UserRole.TEACHER),
                    channelId = CHANNEL,
                    request = CreateChannelTaskRequest("Informe", "Describe tu experimento", dueAt),
                )
            assertEquals(task.id, service.getTask(STUDENT, setOf(UserRole.STUDENT), CHANNEL, task.id).id)
            assertFailsWith<ApiException.Forbidden> {
                service.getTask("QA-STUDENT", setOf(UserRole.STUDENT), CHANNEL, task.id)
            }
            assertFailsWith<ApiException.Forbidden> {
                service.updateTask(
                    STUDENT,
                    setOf(UserRole.STUDENT),
                    CHANNEL,
                    task.id,
                    UpdateChannelTaskRequest(closed = true, expectedVersion = 1),
                )
            }
            assertFailsWith<ApiException.Conflict> {
                service.updateTask(
                    TEACHER,
                    setOf(UserRole.TEACHER),
                    CHANNEL,
                    task.id,
                    UpdateChannelTaskRequest(closed = true, expectedVersion = 2),
                )
            }
            val closed =
                service.updateTask(
                    TEACHER,
                    setOf(UserRole.TEACHER),
                    CHANNEL,
                    task.id,
                    UpdateChannelTaskRequest(closed = true, expectedVersion = 1),
                )
            assertTrue(closed.closed)
            assertEquals(2, closed.version)
            assertFailsWith<ApiException.Conflict> {
                service.submit(STUDENT, "Ana", setOf(UserRole.STUDENT), CHANNEL, task.id, SubmitTaskRequest())
            }
        }

    @Test
    fun `submission retries are idempotent and returned work is versioned on resubmission`() =
        runTest {
            val task = createTask()
            val initialRequest = SubmitTaskRequest(comment = "Primera entrega", idempotencyKey = "request-0001")
            val first = service.submit(STUDENT, "Ana", setOf(UserRole.STUDENT), CHANNEL, task.id, initialRequest)
            val retry = service.submit(STUDENT, "Ana", setOf(UserRole.STUDENT), CHANNEL, task.id, initialRequest)
            assertEquals(first.id, retry.id)
            assertEquals(1, retry.version)
            assertEquals(1, service.submissionsFor(STUDENT, setOf(UserRole.STUDENT), CHANNEL, task.id).size)
            assertFailsWith<ApiException.Conflict> {
                service.submit(
                    STUDENT,
                    "Ana",
                    setOf(UserRole.STUDENT),
                    CHANNEL,
                    task.id,
                    initialRequest.copy(comment = "Different payload"),
                )
            }

            val returned =
                service.review(
                    TEACHER,
                    setOf(UserRole.TEACHER),
                    CHANNEL,
                    task.id,
                    first.id,
                    ReviewSubmissionRequest(TaskStatus.RETURNED, "Agrega fuentes", expectedVersion = 1),
                )
            assertEquals(TaskStatus.RETURNED, returned.status)
            assertEquals(listOf(TaskStatus.SUBMITTED, TaskStatus.RETURNED), returned.history.map { it.status })
            val resubmitted =
                service.submit(
                    STUDENT,
                    "Ana",
                    setOf(UserRole.STUDENT),
                    CHANNEL,
                    task.id,
                    SubmitTaskRequest(
                        comment = "Entrega corregida",
                        expectedVersion = returned.version,
                        submissionId = returned.id,
                    ),
                )
            assertEquals(TaskStatus.SUBMITTED, resubmitted.status)
            assertEquals(3, resubmitted.version)
            assertEquals(3, resubmitted.history.size)
        }

    @Test
    fun `concurrent reviews with the same version allow exactly one write`() =
        runTest {
            val task = createTask()
            val submission = service.submit(STUDENT, "Ana", setOf(UserRole.STUDENT), CHANNEL, task.id, SubmitTaskRequest())
            val results =
                listOf(
                    async {
                        runCatching {
                            service.review(
                                TEACHER,
                                setOf(UserRole.TEACHER),
                                CHANNEL,
                                task.id,
                                submission.id,
                                ReviewSubmissionRequest(TaskStatus.REVIEWED, "Bien", expectedVersion = 1),
                            )
                        }
                    },
                    async {
                        runCatching {
                            service.review(
                                TEACHER,
                                setOf(UserRole.TEACHER),
                                CHANNEL,
                                task.id,
                                submission.id,
                                ReviewSubmissionRequest(TaskStatus.RETURNED, "Revisar", expectedVersion = 1),
                            )
                        }
                    },
                ).map { it.await() }
            assertEquals(1, results.count { it.isSuccess })
            assertEquals(1, results.count { it.exceptionOrNull() is ApiException.Conflict })
        }

    @Test
    fun `private submission files validate type ownership and authorized download`() =
        runTest {
            val task = createTask()
            val submissionId = UUID.randomUUID().toString()
            val upload =
                service.reserveFile(
                    userId = STUDENT,
                    roles = setOf(UserRole.STUDENT),
                    channelId = CHANNEL,
                    taskId = task.id,
                    request = CreateChannelFileRequest("evidence.txt", "text/plain", 5),
                    scope = ChannelFileScope.SUBMISSION,
                    submissionId = submissionId,
                )
            assertFalse(upload.complete)
            assertEquals(0, upload.uploadedBytes)

            val bytes = "proof".toByteArray()
            val completed =
                service.completeFileUpload(
                    userId = STUDENT,
                    channelId = CHANNEL,
                    taskId = task.id,
                    fileId = upload.fileId,
                    bytes = bytes,
                    roles = setOf(UserRole.STUDENT),
                    identitySource = org.companerodeescuela.api.auth.SessionIdentitySource.INSTITUTIONAL,
                )
            assertTrue(completed.complete)
            assertEquals(5, completed.uploadedBytes)
            val submission =
                service.submit(
                    STUDENT,
                    "Ana",
                    setOf(UserRole.STUDENT),
                    CHANNEL,
                    task.id,
                    SubmitTaskRequest(fileIds = listOf(upload.fileId), submissionId = submissionId),
                )
            assertEquals(ChannelFileState.AVAILABLE, submission.files.single().state)
            assertTrue(
                submission.files
                    .single()
                    .downloadPath
                    .startsWith("/channels/"),
            )
            assertFalse(
                submission.files
                    .single()
                    .downloadPath
                    .startsWith("https://"),
            )

            val downloaded =
                service.downloadFile(
                    TEACHER,
                    setOf(UserRole.TEACHER),
                    CHANNEL,
                    task.id,
                    upload.fileId,
                    submissionId = submissionId,
                )
            assertTrue(bytes.contentEquals(downloaded.bytes))
            assertFailsWith<ApiException.NotFound> {
                service.downloadFile(
                    TEACHER,
                    setOf(UserRole.TEACHER),
                    CHANNEL,
                    task.id,
                    upload.fileId,
                    submissionId = UUID.randomUUID().toString(),
                )
            }
        }

    @Test
    fun `task file upload rejects bad declarations and student upload`() =
        runTest {
            val task = createTask()
            assertFailsWith<ApiException.Validation> {
                service.reserveFile(
                    userId = TEACHER,
                    roles = setOf(UserRole.TEACHER),
                    channelId = CHANNEL,
                    taskId = task.id,
                    request = CreateChannelFileRequest("malware.exe", "application/octet-stream", 4),
                    scope = ChannelFileScope.TASK,
                )
            }
            assertFailsWith<ApiException.Forbidden> {
                service.reserveFile(
                    userId = STUDENT,
                    roles = setOf(UserRole.STUDENT),
                    channelId = CHANNEL,
                    taskId = task.id,
                    request = CreateChannelFileRequest("notes.txt", "text/plain", 4),
                    scope = ChannelFileScope.TASK,
                )
            }
            val upload =
                service.reserveFile(
                    userId = TEACHER,
                    roles = setOf(UserRole.TEACHER),
                    channelId = CHANNEL,
                    taskId = task.id,
                    request = CreateChannelFileRequest("notes.txt", "text/plain", 4),
                    scope = ChannelFileScope.TASK,
                )
            assertFailsWith<ApiException.Validation> {
                service.completeFileUpload(
                    TEACHER,
                    CHANNEL,
                    task.id,
                    upload.fileId,
                    byteArrayOf(0, 0, 0, 0),
                    roles = setOf(UserRole.TEACHER),
                    identitySource = org.companerodeescuela.api.auth.SessionIdentitySource.INSTITUTIONAL,
                )
            }
            assertTrue(upload.fileId.isNotBlank())
        }

    private suspend fun createTask() =
        service.createTask(
            userId = TEACHER,
            displayName = "Elena",
            roles = setOf(UserRole.TEACHER),
            channelId = CHANNEL,
            request = CreateChannelTaskRequest("Informe", "Describe tu experimento", dueAt),
        )

    private companion object {
        const val CHANNEL = "C-9001"
        const val TEACHER = "T-0001"
        const val STUDENT = "2020-10455"
    }
}
