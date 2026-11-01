package org.companerodeescuela.api.channel

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.auth.authenticate
import io.ktor.server.request.contentLength
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.utils.io.readAvailable
import org.companerodeescuela.api.auth.AuthTokenService
import org.companerodeescuela.api.auth.boundedBody
import org.companerodeescuela.api.auth.displayNameOr
import org.companerodeescuela.api.auth.platformRoles
import org.companerodeescuela.api.auth.requireActor
import org.companerodeescuela.api.auth.requirePlatformPrincipal
import org.companerodeescuela.api.auth.subjectId
import org.companerodeescuela.api.auth.verifiedSessionSource
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.plugins.requestId
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.ChannelAcknowledgementRequest
import org.companerodeescuela.shared.contracts.ChannelFileScope
import org.companerodeescuela.shared.contracts.CreateChannelFileRequest
import org.companerodeescuela.shared.contracts.CreateChannelPostRequest
import org.companerodeescuela.shared.contracts.CreateChannelTaskRequest
import org.companerodeescuela.shared.contracts.ReviewSubmissionRequest
import org.companerodeescuela.shared.contracts.SubmitTaskRequest
import org.companerodeescuela.shared.contracts.UpdateChannelPostRequest
import org.companerodeescuela.shared.contracts.UpdateChannelTaskRequest

fun Route.channelRoutes(
    settings: ApiSettings,
    service: ChannelService,
    taskService: ChannelTaskService,
) {
    route("/channels") {
        if (!settings.hasAuthentication) {
            get {
                throw ApiException.DependencyUnavailable("Authentication is not configured")
            }
            return@route
        }

        val tokens = AuthTokenService(settings)
        authenticate(AuthTokenService.PROVIDER_NAME) {
            get {
                val principal = call.requirePlatformPrincipal()
                call.respond(
                    ApiResponse(
                        data =
                            service.channelsFor(
                                principal.subjectId(),
                                principal.platformRoles(),
                                call.requireActor(tokens),
                                call.verifiedSessionSource(),
                            ),
                        requestId = call.requestId(),
                    ),
                )
            }

            get("/{channelId}/posts") {
                val principal = call.requirePlatformPrincipal()
                val channelId = call.requireChannelId()
                call.respond(
                    ApiResponse(
                        data =
                            service.postsFor(
                                userId = principal.subjectId(),
                                roles = principal.platformRoles(),
                                actor = call.requireActor(tokens),
                                identitySource = call.verifiedSessionSource(),
                                channelId = channelId,
                            ),
                        requestId = call.requestId(),
                    ),
                )
            }

            post("/{channelId}/posts") {
                val principal = call.requirePlatformPrincipal()
                val channelId = call.requireChannelId()
                call.respond(
                    ApiResponse(
                        data =
                            service.createPost(
                                authorId = principal.subjectId(),
                                authorDisplayName = principal.displayNameOr("Docente"),
                                roles = principal.platformRoles(),
                                actor = call.requireActor(tokens),
                                identitySource = call.verifiedSessionSource(),
                                channelId = channelId,
                                request = call.boundedBody(CreateChannelPostRequest.serializer()),
                            ),
                        requestId = call.requestId(),
                    ),
                )
            }

            patch("/{channelId}/posts/{postId}") {
                val principal = call.requirePlatformPrincipal()
                val channelId = call.requireChannelId()
                val postId = call.requirePostId()
                call.respond(
                    ApiResponse(
                        data =
                            service.updatePost(
                                actorId = principal.subjectId(),
                                roles = principal.platformRoles(),
                                actor = call.requireActor(tokens),
                                identitySource = call.verifiedSessionSource(),
                                channelId = channelId,
                                postId = postId,
                                request = call.boundedBody(UpdateChannelPostRequest.serializer()),
                            ),
                        requestId = call.requestId(),
                    ),
                )
            }

            delete("/{channelId}/posts/{postId}") {
                val principal = call.requirePlatformPrincipal()
                val channelId = call.requireChannelId()
                val postId = call.requirePostId()
                call.respond(
                    ApiResponse(
                        data =
                            service.deletePost(
                                actorId = principal.subjectId(),
                                roles = principal.platformRoles(),
                                actor = call.requireActor(tokens),
                                identitySource = call.verifiedSessionSource(),
                                channelId = channelId,
                                postId = postId,
                            ),
                        requestId = call.requestId(),
                    ),
                )
            }

            put("/{channelId}/posts/{postId}/acknowledgement") {
                val principal = call.requirePlatformPrincipal()
                val channelId = call.requireChannelId()
                val postId = call.requirePostId()
                call.respond(
                    ApiResponse(
                        data =
                            service.acknowledge(
                                studentId = principal.subjectId(),
                                roles = principal.platformRoles(),
                                actor = call.requireActor(tokens),
                                identitySource = call.verifiedSessionSource(),
                                channelId = channelId,
                                postId = postId,
                                request = call.boundedBody(ChannelAcknowledgementRequest.serializer()),
                            ),
                        requestId = call.requestId(),
                    ),
                )
            }

            get("/{channelId}/posts/{postId}/stats") {
                val principal = call.requirePlatformPrincipal()
                val channelId = call.requireChannelId()
                val postId = call.requirePostId()
                call.respond(
                    ApiResponse(
                        data =
                            service.statsFor(
                                actorId = principal.subjectId(),
                                roles = principal.platformRoles(),
                                actor = call.requireActor(tokens),
                                identitySource = call.verifiedSessionSource(),
                                channelId = channelId,
                                postId = postId,
                            ),
                        requestId = call.requestId(),
                    ),
                )
            }

            get("/{channelId}/tasks") {
                val principal = call.requirePlatformPrincipal()
                call.respond(
                    ApiResponse(
                        data =
                            taskService.listTasks(
                                principal.subjectId(),
                                principal.platformRoles(),
                                call.requireChannelId(),
                                call.requireActor(tokens),
                                call.verifiedSessionSource(),
                            ),
                        requestId = call.requestId(),
                    ),
                )
            }

            post("/{channelId}/tasks") {
                val principal = call.requirePlatformPrincipal()
                call.respond(
                    ApiResponse(
                        data =
                            taskService.createTask(
                                principal.subjectId(),
                                principal.displayNameOr("Docente"),
                                principal.platformRoles(),
                                call.requireChannelId(),
                                call.boundedBody(CreateChannelTaskRequest.serializer()),
                                call.requireActor(tokens),
                                call.verifiedSessionSource(),
                            ),
                        requestId = call.requestId(),
                    ),
                )
            }

            get("/{channelId}/tasks/{taskId}") {
                val principal = call.requirePlatformPrincipal()
                call.respond(
                    ApiResponse(
                        data =
                            taskService.getTask(
                                principal.subjectId(),
                                principal.platformRoles(),
                                call.requireChannelId(),
                                call.requireTaskId(),
                                call.requireActor(tokens),
                                call.verifiedSessionSource(),
                            ),
                        requestId = call.requestId(),
                    ),
                )
            }

            patch("/{channelId}/tasks/{taskId}") {
                val principal = call.requirePlatformPrincipal()
                call.respond(
                    ApiResponse(
                        data =
                            taskService.updateTask(
                                principal.subjectId(),
                                principal.platformRoles(),
                                call.requireChannelId(),
                                call.requireTaskId(),
                                call.boundedBody(UpdateChannelTaskRequest.serializer()),
                                call.requireActor(tokens),
                                call.verifiedSessionSource(),
                            ),
                        requestId = call.requestId(),
                    ),
                )
            }

            get("/{channelId}/tasks/{taskId}/submissions") {
                val principal = call.requirePlatformPrincipal()
                call.respond(
                    ApiResponse(
                        data =
                            taskService.submissionsFor(
                                principal.subjectId(),
                                principal.platformRoles(),
                                call.requireChannelId(),
                                call.requireTaskId(),
                                call.requireActor(tokens),
                                call.verifiedSessionSource(),
                            ),
                        requestId = call.requestId(),
                    ),
                )
            }

            post("/{channelId}/tasks/{taskId}/submissions") {
                val principal = call.requirePlatformPrincipal()
                call.respond(
                    ApiResponse(
                        data =
                            taskService.submit(
                                principal.subjectId(),
                                principal.displayNameOr("Estudiante"),
                                principal.platformRoles(),
                                call.requireChannelId(),
                                call.requireTaskId(),
                                call.boundedBody(SubmitTaskRequest.serializer()),
                                call.requireActor(tokens),
                                call.verifiedSessionSource(),
                            ),
                        requestId = call.requestId(),
                    ),
                )
            }

            patch("/{channelId}/tasks/{taskId}/submissions/{submissionId}") {
                val principal = call.requirePlatformPrincipal()
                call.respond(
                    ApiResponse(
                        data =
                            taskService.review(
                                principal.subjectId(),
                                principal.platformRoles(),
                                call.requireChannelId(),
                                call.requireTaskId(),
                                call.requireSubmissionId(),
                                call.boundedBody(ReviewSubmissionRequest.serializer()),
                                call.requireActor(tokens),
                                call.verifiedSessionSource(),
                            ),
                        requestId = call.requestId(),
                    ),
                )
            }

            post("/{channelId}/tasks/{taskId}/files") {
                val principal = call.requirePlatformPrincipal()
                call.respond(
                    ApiResponse(
                        data =
                            taskService.reserveFile(
                                userId = principal.subjectId(),
                                roles = principal.platformRoles(),
                                channelId = call.requireChannelId(),
                                taskId = call.requireTaskId(),
                                request = call.boundedBody(CreateChannelFileRequest.serializer()),
                                scope = ChannelFileScope.TASK,
                                actor = call.requireActor(tokens),
                                identitySource = call.verifiedSessionSource(),
                            ),
                        requestId = call.requestId(),
                    ),
                )
            }

            post("/{channelId}/tasks/{taskId}/submissions/{submissionId}/files") {
                val principal = call.requirePlatformPrincipal()
                call.respond(
                    ApiResponse(
                        data =
                            taskService.reserveFile(
                                userId = principal.subjectId(),
                                roles = principal.platformRoles(),
                                channelId = call.requireChannelId(),
                                taskId = call.requireTaskId(),
                                request = call.boundedBody(CreateChannelFileRequest.serializer()),
                                scope = ChannelFileScope.SUBMISSION,
                                submissionId = call.requireSubmissionId(),
                                actor = call.requireActor(tokens),
                                identitySource = call.verifiedSessionSource(),
                            ),
                        requestId = call.requestId(),
                    ),
                )
            }

            get("/{channelId}/tasks/{taskId}/files/{fileId}/progress") {
                val principal = call.requirePlatformPrincipal()
                call.respond(
                    ApiResponse(
                        data =
                            taskService.fileUploadProgress(
                                userId = principal.subjectId(),
                                roles = principal.platformRoles(),
                                channelId = call.requireChannelId(),
                                taskId = call.requireTaskId(),
                                fileId = call.requireFileId(),
                                actor = call.requireActor(tokens),
                                identitySource = call.verifiedSessionSource(),
                            ),
                        requestId = call.requestId(),
                    ),
                )
            }

            put("/{channelId}/tasks/{taskId}/files/{fileId}") {
                val principal = call.requirePlatformPrincipal()
                val channelId = call.requireChannelId()
                val taskId = call.requireTaskId()
                val fileId = call.requireFileId()
                val roles = principal.platformRoles()
                val actor = call.requireActor(tokens)
                val identitySource = call.verifiedSessionSource()
                val target =
                    taskService.authorizeFileUpload(
                        principal.subjectId(),
                        roles,
                        channelId,
                        taskId,
                        fileId,
                        actor,
                        identitySource,
                    )
                val bytes = call.boundedFileBody(target.sizeBytes)
                val contentType =
                    call.request
                        .contentType()
                        .withoutParameters()
                        .toString()
                if (!contentType.equals(target.contentType, ignoreCase = true)) {
                    throw ApiException.Validation("Content-Type does not match the reserved file")
                }
                call.respond(
                    ApiResponse(
                        data =
                            taskService.completeFileUpload(
                                userId = principal.subjectId(),
                                channelId = channelId,
                                taskId = taskId,
                                fileId = fileId,
                                bytes = bytes,
                                actor = actor,
                                roles = roles,
                                identitySource = identitySource,
                            ),
                        requestId = call.requestId(),
                    ),
                )
            }

            get("/{channelId}/tasks/{taskId}/files/{fileId}") {
                val principal = call.requirePlatformPrincipal()
                val file =
                    taskService.downloadFile(
                        userId = principal.subjectId(),
                        roles = principal.platformRoles(),
                        channelId = call.requireChannelId(),
                        taskId = call.requireTaskId(),
                        fileId = call.requireFileId(),
                        actor = call.requireActor(tokens),
                        identitySource = call.verifiedSessionSource(),
                    )
                call.response.header(
                    HttpHeaders.ContentDisposition,
                    "attachment; filename=\"${file.metadata.fileName}\"",
                )
                call.respondBytes(file.bytes!!, ContentType.parse(file.metadata.contentType))
            }

            get("/{channelId}/tasks/{taskId}/submissions/{submissionId}/files/{fileId}/progress") {
                val principal = call.requirePlatformPrincipal()
                call.respond(
                    ApiResponse(
                        data =
                            taskService.fileUploadProgress(
                                userId = principal.subjectId(),
                                roles = principal.platformRoles(),
                                channelId = call.requireChannelId(),
                                taskId = call.requireTaskId(),
                                fileId = call.requireFileId(),
                                actor = call.requireActor(tokens),
                                identitySource = call.verifiedSessionSource(),
                                submissionId = call.requireSubmissionId(),
                            ),
                        requestId = call.requestId(),
                    ),
                )
            }

            put("/{channelId}/tasks/{taskId}/submissions/{submissionId}/files/{fileId}") {
                val principal = call.requirePlatformPrincipal()
                val channelId = call.requireChannelId()
                val taskId = call.requireTaskId()
                val submissionId = call.requireSubmissionId()
                val fileId = call.requireFileId()
                val roles = principal.platformRoles()
                val actor = call.requireActor(tokens)
                val identitySource = call.verifiedSessionSource()
                val target =
                    taskService.authorizeFileUpload(
                        principal.subjectId(),
                        roles,
                        channelId,
                        taskId,
                        fileId,
                        actor,
                        identitySource,
                        submissionId,
                    )
                val bytes = call.boundedFileBody(target.sizeBytes)
                val contentType =
                    call.request
                        .contentType()
                        .withoutParameters()
                        .toString()
                if (!contentType.equals(target.contentType, ignoreCase = true)) {
                    throw ApiException.Validation("Content-Type does not match the reserved file")
                }
                call.respond(
                    ApiResponse(
                        data =
                            taskService.completeFileUpload(
                                userId = principal.subjectId(),
                                channelId = channelId,
                                taskId = taskId,
                                fileId = fileId,
                                bytes = bytes,
                                actor = actor,
                                roles = roles,
                                identitySource = identitySource,
                            ),
                        requestId = call.requestId(),
                    ),
                )
            }

            get("/{channelId}/tasks/{taskId}/submissions/{submissionId}/files/{fileId}") {
                val principal = call.requirePlatformPrincipal()
                val file =
                    taskService.downloadFile(
                        userId = principal.subjectId(),
                        roles = principal.platformRoles(),
                        channelId = call.requireChannelId(),
                        taskId = call.requireTaskId(),
                        fileId = call.requireFileId(),
                        actor = call.requireActor(tokens),
                        identitySource = call.verifiedSessionSource(),
                        submissionId = call.requireSubmissionId(),
                    )
                call.response.header(
                    HttpHeaders.ContentDisposition,
                    "attachment; filename=\"${file.metadata.fileName}\"",
                )
                call.respondBytes(file.bytes!!, ContentType.parse(file.metadata.contentType))
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.requireChannelId(): String =
    parameters["channelId"]?.takeIf(String::isNotBlank)
        ?: throw ApiException.Validation("channelId is required")

private fun io.ktor.server.application.ApplicationCall.requirePostId(): String =
    parameters["postId"]?.takeIf(String::isNotBlank)
        ?: throw ApiException.Validation("postId is required")

private fun io.ktor.server.application.ApplicationCall.requireTaskId(): String =
    parameters["taskId"]?.takeIf(String::isNotBlank)
        ?: throw ApiException.Validation("taskId is required")

private fun io.ktor.server.application.ApplicationCall.requireSubmissionId(): String =
    parameters["submissionId"]?.takeIf(String::isNotBlank)
        ?: throw ApiException.Validation("submissionId is required")

private fun io.ktor.server.application.ApplicationCall.requireFileId(): String =
    parameters["fileId"]?.takeIf(String::isNotBlank)
        ?: throw ApiException.Validation("fileId is required")

private suspend fun io.ktor.server.application.ApplicationCall.boundedFileBody(expectedBytes: Long): ByteArray {
    val maxBytes = 8 * 1024 * 1024
    val contentLength = request.contentLength()
    if (expectedBytes !in 1..maxBytes.toLong() || contentLength?.let { it != expectedBytes } == true) {
        throw ApiException.Validation("Upload length does not match the reservation")
    }
    val channel = receiveChannel()
    val bytes = ByteArray(maxBytes + 1)
    var size = 0
    while (size < bytes.size) {
        val count = channel.readAvailable(bytes, size, bytes.size - size)
        if (count < 0) break
        size += count
    }
    if (size > maxBytes || size.toLong() != expectedBytes) {
        channel.cancel(null)
        throw ApiException.Validation("Uploaded byte count does not match the reservation")
    }
    return bytes.copyOf(size)
}
