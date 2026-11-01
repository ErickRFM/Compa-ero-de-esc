package org.companerodeescuela.api.channel

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.companerodeescuela.api.academic.groups.InMemoryAcademicGroupRepository
import org.companerodeescuela.api.auth.AuthTokenService
import org.companerodeescuela.api.auth.InMemoryRefreshSessionRepository
import org.companerodeescuela.api.auth.PlatformSessionAuthority
import org.companerodeescuela.api.auth.RefreshSession
import org.companerodeescuela.api.auth.SessionIdentitySource
import org.companerodeescuela.api.classroom.ClassroomService
import org.companerodeescuela.api.classroom.InMemoryClassroomRepository
import org.companerodeescuela.api.integrations.academic.AcademicProvider
import org.companerodeescuela.api.integrations.academic.dto.ExternalAcademicLoad
import org.companerodeescuela.api.integrations.mock.MockAcademicProvider
import org.companerodeescuela.api.integrations.mock.MockFixtures
import org.companerodeescuela.api.integrations.mock.MockIdentityProvider
import org.companerodeescuela.api.plugins.configurePlugins
import org.companerodeescuela.api.test.TestFixtures
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.ChannelTask
import org.companerodeescuela.shared.contracts.CreateChannelFileRequest
import org.companerodeescuela.shared.contracts.CreateChannelTaskRequest
import org.companerodeescuela.shared.contracts.ReviewSubmissionRequest
import org.companerodeescuela.shared.contracts.SubmitTaskRequest
import org.companerodeescuela.shared.contracts.TaskStatus
import org.companerodeescuela.shared.contracts.TaskSubmission
import org.companerodeescuela.shared.contracts.UpdateChannelTaskRequest
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChannelTaskRoutesTest {
    @Test
    fun `authenticated HTTP flow scopes tasks submissions reviews and private files`() =
        testApplication {
            val settings =
                TestFixtures.settings().copy(
                    jwtSecret = "0123456789abcdef0123456789abcdef".toCharArray(),
                )
            val tokens = AuthTokenService(settings)
            val sessions = InMemoryRefreshSessionRepository()
            val identity = MockIdentityProvider()
            val academic = scopedAcademicProvider()
            val access =
                ChannelAccessPolicy(
                    academicProvider = academic,
                    classroomService =
                        ClassroomService(
                            InMemoryClassroomRepository(),
                            InMemoryAcademicGroupRepository(),
                        ),
                )
            val channelService = ChannelService(InMemoryChannelRepository(), access)
            val taskService = ChannelTaskService(InMemoryChannelTaskRepository(), access)
            val teacher = user(MockFixtures.TEACHER_ID, UserRole.TEACHER)
            val otherTeacher = user(MockFixtures.QA_TEACHER_ID, UserRole.TEACHER)
            val student = user(MockFixtures.STUDENT_ID, UserRole.STUDENT)
            val otherGroupStudent = user(MockFixtures.QA_STUDENT_ID, UserRole.STUDENT)
            val teacherToken = sessionToken(teacher, tokens, sessions)
            val otherTeacherToken = sessionToken(otherTeacher, tokens, sessions)
            val studentToken = sessionToken(student, tokens, sessions)
            val otherGroupStudentToken = sessionToken(otherGroupStudent, tokens, sessions)

            application {
                configurePlugins(
                    settings,
                    sessions,
                    PlatformSessionAuthority(
                        org.companerodeescuela.api.auth
                            .InMemoryPlatformAccountRepository(),
                        identity,
                    ),
                )
                routing { channelRoutes(settings, channelService, taskService) }
            }

            val taskRequest =
                CreateChannelTaskRequest(
                    title = "Lab report",
                    instructions = "Upload your evidence",
                    dueDateEpochSeconds = Instant.now().epochSecond + 3_600,
                )
            val created =
                client.post("/channels/C-9001/tasks") {
                    bearerAuth(teacherToken)
                    contentType(ContentType.Application.Json)
                    setBody(Json.encodeToString(taskRequest))
                }
            assertEquals(HttpStatusCode.OK, created.status)
            val task = Json.decodeFromString<ApiResponse<ChannelTask>>(created.bodyAsText()).data
            assertEquals("T-0001", task.teacherId)

            val foreignTaskResponse =
                client.post("/channels/C-9002/tasks") {
                    bearerAuth(otherTeacherToken)
                    contentType(ContentType.Application.Json)
                    setBody(Json.encodeToString(taskRequest.copy(title = "Other group task")))
                }
            assertEquals(HttpStatusCode.OK, foreignTaskResponse.status)
            val foreignTask = Json.decodeFromString<ApiResponse<ChannelTask>>(foreignTaskResponse.bodyAsText()).data

            assertEquals(
                HttpStatusCode.Forbidden,
                client
                    .get("/channels/C-9001/tasks") {
                        bearerAuth(otherTeacherToken)
                    }.status,
                "teacher assigned to C-9002 cannot enter C-9001",
            )
            assertEquals(
                HttpStatusCode.Forbidden,
                client
                    .get("/channels/C-9002/tasks") {
                        bearerAuth(otherGroupStudentToken)
                    }.status,
                "student outside C-9002 cannot enumerate its tasks",
            )
            assertEquals(
                HttpStatusCode.Forbidden,
                client
                    .get("/channels/C-9002/tasks/${foreignTask.id}") {
                        bearerAuth(studentToken)
                    }.status,
                "student enrolled only in C-9001 cannot read C-9002",
            )

            val visibleTasks = client.get("/channels/C-9001/tasks") { bearerAuth(studentToken) }
            assertEquals(HttpStatusCode.OK, visibleTasks.status)
            assertEquals(
                listOf(task.id),
                Json.decodeFromString<ApiResponse<List<ChannelTask>>>(visibleTasks.bodyAsText()).data.map { it.id },
            )

            val submissionId = UUID.randomUUID().toString()
            val staged =
                client.post("/channels/C-9001/tasks/${task.id}/submissions/$submissionId/files") {
                    bearerAuth(studentToken)
                    contentType(ContentType.Application.Json)
                    setBody(Json.encodeToString(CreateChannelFileRequest("evidence.txt", "text/plain", 5)))
                }
            assertEquals(HttpStatusCode.OK, staged.status)
            val upload =
                Json
                    .decodeFromString<ApiResponse<org.companerodeescuela.shared.contracts.ChannelFileUploadProgress>>(
                        staged.bodyAsText(),
                    ).data
            assertFalse(upload.complete)

            val firstBytes = "proof".toByteArray()
            val uploadResponse =
                client.put(
                    "/channels/C-9001/tasks/${task.id}/submissions/$submissionId/files/${upload.fileId}",
                ) {
                    bearerAuth(studentToken)
                    contentType(ContentType.Text.Plain)
                    setBody(firstBytes)
                }
            assertEquals(HttpStatusCode.OK, uploadResponse.status)
            assertTrue(
                Json
                    .decodeFromString<ApiResponse<org.companerodeescuela.shared.contracts.ChannelFileUploadProgress>>(
                        uploadResponse.bodyAsText(),
                    ).data.complete,
            )

            val submittedResponse =
                client.post("/channels/C-9001/tasks/${task.id}/submissions") {
                    bearerAuth(studentToken)
                    contentType(ContentType.Application.Json)
                    setBody(
                        Json.encodeToString(
                            SubmitTaskRequest(
                                comment = "First version",
                                fileIds = listOf(upload.fileId),
                                idempotencyKey = "route-submit-001",
                                submissionId = submissionId,
                            ),
                        ),
                    )
                }
            assertEquals(HttpStatusCode.OK, submittedResponse.status)
            val submitted = Json.decodeFromString<ApiResponse<TaskSubmission>>(submittedResponse.bodyAsText()).data
            assertEquals(TaskStatus.SUBMITTED, submitted.status)

            val studentDownload =
                client.get(
                    "/channels/C-9001/tasks/${task.id}/submissions/$submissionId/files/${upload.fileId}",
                ) { bearerAuth(studentToken) }
            assertEquals(HttpStatusCode.OK, studentDownload.status)
            assertContentEquals(firstBytes, studentDownload.bodyAsBytes())
            val teacherDownload =
                client.get(
                    "/channels/C-9001/tasks/${task.id}/submissions/$submissionId/files/${upload.fileId}",
                ) { bearerAuth(teacherToken) }
            assertEquals(HttpStatusCode.OK, teacherDownload.status)
            assertContentEquals(firstBytes, teacherDownload.bodyAsBytes())
            assertEquals(
                HttpStatusCode.Unauthorized,
                client
                    .get(
                        "/channels/C-9001/tasks/${task.id}/submissions/$submissionId/files/${upload.fileId}",
                    ).status,
                "file download is not public",
            )
            assertEquals(
                HttpStatusCode.Forbidden,
                client
                    .get(
                        "/channels/C-9001/tasks/${task.id}/submissions/$submissionId/files/${upload.fileId}",
                    ) { bearerAuth(otherTeacherToken) }
                    .status,
                "an unrelated teacher cannot download private work",
            )

            val teacherSubmissions =
                client.get("/channels/C-9001/tasks/${task.id}/submissions") {
                    bearerAuth(teacherToken)
                }
            assertEquals(HttpStatusCode.OK, teacherSubmissions.status)
            assertEquals(
                submitted.id,
                Json
                    .decodeFromString<ApiResponse<List<TaskSubmission>>>(teacherSubmissions.bodyAsText())
                    .data
                    .single()
                    .id,
            )
            val returnedResponse =
                client.patch(
                    "/channels/C-9001/tasks/${task.id}/submissions/${submitted.id}",
                ) {
                    bearerAuth(teacherToken)
                    contentType(ContentType.Application.Json)
                    setBody(
                        Json.encodeToString(
                            ReviewSubmissionRequest(TaskStatus.RETURNED, "Add a conclusion", expectedVersion = 1),
                        ),
                    )
                }
            assertEquals(HttpStatusCode.OK, returnedResponse.status)
            val returned = Json.decodeFromString<ApiResponse<TaskSubmission>>(returnedResponse.bodyAsText()).data
            assertEquals(TaskStatus.RETURNED, returned.status)
            assertEquals(2, returned.version)

            val corrected =
                client.post("/channels/C-9001/tasks/${task.id}/submissions/$submissionId/files") {
                    bearerAuth(studentToken)
                    contentType(ContentType.Application.Json)
                    setBody(Json.encodeToString(CreateChannelFileRequest("correction.txt", "text/plain", 8)))
                }
            assertEquals(HttpStatusCode.OK, corrected.status)
            val correctionUpload =
                Json
                    .decodeFromString<ApiResponse<org.companerodeescuela.shared.contracts.ChannelFileUploadProgress>>(
                        corrected.bodyAsText(),
                    ).data
            val correctedBytes = "revised!".toByteArray()
            assertEquals(
                HttpStatusCode.OK,
                client
                    .put(
                        "/channels/C-9001/tasks/${task.id}/submissions/$submissionId/files/${correctionUpload.fileId}",
                    ) {
                        bearerAuth(studentToken)
                        contentType(ContentType.Text.Plain)
                        setBody(correctedBytes)
                    }.status,
            )
            val correctionResponse =
                client.post("/channels/C-9001/tasks/${task.id}/submissions") {
                    bearerAuth(studentToken)
                    contentType(ContentType.Application.Json)
                    setBody(
                        Json.encodeToString(
                            SubmitTaskRequest(
                                comment = "Corrected version",
                                fileIds = listOf(upload.fileId, correctionUpload.fileId),
                                expectedVersion = returned.version,
                                idempotencyKey = "route-submit-002",
                                submissionId = submissionId,
                            ),
                        ),
                    )
                }
            assertEquals(HttpStatusCode.OK, correctionResponse.status)
            val resubmitted = Json.decodeFromString<ApiResponse<TaskSubmission>>(correctionResponse.bodyAsText()).data
            assertEquals(TaskStatus.SUBMITTED, resubmitted.status)
            assertEquals(3, resubmitted.version)
            assertEquals(listOf(1, 2, 3), resubmitted.history.map { it.version })

            assertEquals(
                HttpStatusCode.Forbidden,
                client
                    .patch(
                        "/channels/C-9001/tasks/${task.id}",
                    ) {
                        bearerAuth(otherTeacherToken)
                        contentType(ContentType.Application.Json)
                        setBody(Json.encodeToString(UpdateChannelTaskRequest(closed = true, expectedVersion = 1)))
                    }.status,
                "unassigned teacher cannot mutate a task",
            )
        }

    private fun scopedAcademicProvider(): AcademicProvider {
        val mock = MockAcademicProvider()
        return object : AcademicProvider by mock {
            override suspend fun listCourses() =
                mock.listCourses().map { course ->
                    if (course.externalId == "C-9002") {
                        course.copy(teacher = course.teacher.copy(externalId = MockFixtures.QA_TEACHER_ID))
                    } else {
                        course
                    }
                }

            override suspend fun getAcademicLoad(externalId: String): ExternalAcademicLoad {
                val load = mock.getAcademicLoad(externalId)
                return if (externalId == MockFixtures.STUDENT_ID) {
                    load.copy(enrollments = load.enrollments.filter { it.course.externalId == "C-9001" })
                } else {
                    load
                }
            }
        }
    }

    private fun user(
        id: String,
        role: UserRole,
    ) = UserSummary(id, "Test $id", roles = setOf(role))

    private suspend fun sessionToken(
        user: UserSummary,
        tokens: AuthTokenService,
        sessions: InMemoryRefreshSessionRepository,
    ): String {
        val sessionId = "test-session-${user.id}"
        sessions.create(
            RefreshSession(
                id = sessionId,
                tokenHash = "test-only-hash-${user.id}",
                user = user,
                createdAt = Instant.now(),
                expiresAt = Instant.now().plusSeconds(3_600),
                identitySource = SessionIdentitySource.INSTITUTIONAL,
            ),
        )
        return tokens.issue(user, sessionId).value
    }
}
