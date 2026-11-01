package org.companerodeescuela.feature.channel

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.network.SessionRefreshCoordinator
import org.companerodeescuela.core.network.apiCall
import org.companerodeescuela.core.network.requireBody
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.ChannelAcknowledgement
import org.companerodeescuela.shared.contracts.ChannelAcknowledgementRequest
import org.companerodeescuela.shared.contracts.ChannelFileUploadProgress
import org.companerodeescuela.shared.contracts.ChannelPost
import org.companerodeescuela.shared.contracts.ChannelPrivateFileMetadata
import org.companerodeescuela.shared.contracts.ChannelTask
import org.companerodeescuela.shared.contracts.ClassChannelSummary
import org.companerodeescuela.shared.contracts.CreateChannelFileRequest
import org.companerodeescuela.shared.contracts.CreateChannelPostRequest
import org.companerodeescuela.shared.contracts.CreateChannelTaskRequest
import org.companerodeescuela.shared.contracts.ReviewSubmissionRequest
import org.companerodeescuela.shared.contracts.SubmitTaskRequest
import org.companerodeescuela.shared.contracts.TaskSubmission
import org.companerodeescuela.shared.contracts.UpdateChannelTaskRequest

class ChannelRepository(
    private val client: HttpClient,
    private val tokenStore: SessionTokenStore,
    private val refreshCoordinator: SessionRefreshCoordinator =
        SessionRefreshCoordinator(client, tokenStore),
) {
    suspend fun channels(): Outcome<List<ClassChannelSummary>> =
        authorized { token ->
            apiCall {
                client
                    .get("channels") { bearerAuth(token) }
                    .requireBody<ApiResponse<List<ClassChannelSummary>>>()
            }.map { it.data }
        }

    suspend fun posts(channelId: String): Outcome<List<ChannelPost>> =
        authorized { token ->
            apiCall {
                client
                    .get("channels/$channelId/posts") { bearerAuth(token) }
                    .requireBody<ApiResponse<List<ChannelPost>>>()
            }.map { it.data }
        }

    suspend fun publish(
        channelId: String,
        request: CreateChannelPostRequest,
    ): Outcome<ChannelPost> =
        authorized { token ->
            apiCall {
                client
                    .post("channels/$channelId/posts") {
                        bearerAuth(token)
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }.requireBody<ApiResponse<ChannelPost>>()
            }.map { it.data }
        }

    suspend fun acknowledge(
        channelId: String,
        postId: String,
        request: ChannelAcknowledgementRequest,
    ): Outcome<ChannelAcknowledgement> =
        authorized { token ->
            apiCall {
                client
                    .put("channels/$channelId/posts/$postId/acknowledgement") {
                        bearerAuth(token)
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }.requireBody<ApiResponse<ChannelAcknowledgement>>()
            }.map { it.data }
        }

    suspend fun tasks(channelId: String): Outcome<List<ChannelTask>> =
        authorized { token ->
            apiCall {
                client
                    .get("channels/$channelId/tasks") { bearerAuth(token) }
                    .requireBody<ApiResponse<List<ChannelTask>>>()
            }.map { it.data }
        }

    suspend fun createTask(
        channelId: String,
        request: CreateChannelTaskRequest,
    ): Outcome<ChannelTask> =
        authorized { token ->
            apiCall {
                client
                    .post("channels/$channelId/tasks") {
                        bearerAuth(token)
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }.requireBody<ApiResponse<ChannelTask>>()
            }.map { it.data }
        }

    suspend fun updateTask(
        channelId: String,
        taskId: String,
        request: UpdateChannelTaskRequest,
    ): Outcome<ChannelTask> =
        authorized { token ->
            apiCall {
                client
                    .patch("channels/$channelId/tasks/$taskId") {
                        bearerAuth(token)
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }.requireBody<ApiResponse<ChannelTask>>()
            }.map { it.data }
        }

    suspend fun submissions(
        channelId: String,
        taskId: String,
    ): Outcome<List<TaskSubmission>> =
        authorized { token ->
            apiCall {
                client
                    .get("channels/$channelId/tasks/$taskId/submissions") { bearerAuth(token) }
                    .requireBody<ApiResponse<List<TaskSubmission>>>()
            }.map { it.data }
        }

    suspend fun submitTask(
        channelId: String,
        taskId: String,
        request: SubmitTaskRequest,
    ): Outcome<TaskSubmission> =
        authorized { token ->
            apiCall {
                client
                    .post("channels/$channelId/tasks/$taskId/submissions") {
                        bearerAuth(token)
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }.requireBody<ApiResponse<TaskSubmission>>()
            }.map { it.data }
        }

    suspend fun reviewSubmission(
        channelId: String,
        taskId: String,
        submissionId: String,
        request: ReviewSubmissionRequest,
    ): Outcome<TaskSubmission> =
        authorized { token ->
            apiCall {
                client
                    .patch("channels/$channelId/tasks/$taskId/submissions/$submissionId") {
                        bearerAuth(token)
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }.requireBody<ApiResponse<TaskSubmission>>()
            }.map { it.data }
        }

    suspend fun reserveTaskFile(
        channelId: String,
        taskId: String,
        request: CreateChannelFileRequest,
    ): Outcome<ChannelFileUploadProgress> =
        authorized { token ->
            apiCall {
                client
                    .post("channels/$channelId/tasks/$taskId/files") {
                        bearerAuth(token)
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }.requireBody<ApiResponse<ChannelFileUploadProgress>>()
            }.map { it.data }
        }

    suspend fun reserveSubmissionFile(
        channelId: String,
        taskId: String,
        submissionId: String,
        request: CreateChannelFileRequest,
    ): Outcome<ChannelFileUploadProgress> =
        authorized { token ->
            apiCall {
                client
                    .post("channels/$channelId/tasks/$taskId/submissions/$submissionId/files") {
                        bearerAuth(token)
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }.requireBody<ApiResponse<ChannelFileUploadProgress>>()
            }.map { it.data }
        }

    suspend fun uploadTaskFile(
        channelId: String,
        taskId: String,
        fileId: String,
        contentType: String,
        bytes: ByteArray,
    ): Outcome<ChannelFileUploadProgress> = uploadFile("channels/$channelId/tasks/$taskId/files/$fileId", contentType, bytes)

    suspend fun uploadSubmissionFile(
        channelId: String,
        taskId: String,
        submissionId: String,
        fileId: String,
        contentType: String,
        bytes: ByteArray,
    ): Outcome<ChannelFileUploadProgress> =
        uploadFile(
            "channels/$channelId/tasks/$taskId/submissions/$submissionId/files/$fileId",
            contentType,
            bytes,
        )

    suspend fun downloadFile(
        channelId: String,
        taskId: String,
        file: ChannelPrivateFileMetadata,
    ): Outcome<ByteArray> =
        authorized { token ->
            apiCall {
                val response =
                    client.get(
                        if (file.submissionId == null) {
                            "channels/$channelId/tasks/$taskId/files/${file.id}"
                        } else {
                            "channels/$channelId/tasks/$taskId/submissions/${file.submissionId}/files/${file.id}"
                        },
                    ) { bearerAuth(token) }
                if (response.status.value in 200..299) {
                    Outcome.Success(response.bodyAsBytes())
                } else {
                    response.requireBody<ByteArray>()
                }
            }
        }

    private suspend fun uploadFile(
        path: String,
        contentType: String,
        bytes: ByteArray,
    ): Outcome<ChannelFileUploadProgress> =
        authorized { token ->
            apiCall {
                client
                    .put(path) {
                        bearerAuth(token)
                        contentType(ContentType.parse(contentType))
                        setBody(bytes)
                    }.requireBody<ApiResponse<ChannelFileUploadProgress>>()
            }.map { it.data }
        }

    private suspend fun <T> authorized(block: suspend (String) -> Outcome<T>): Outcome<T> {
        val token =
            when (val result = refreshCoordinator.currentAccessToken()) {
                is Outcome.Success -> result.value
                is Outcome.Failure -> return result
            }
        return refreshCoordinator.execute(token, block)
    }
}
