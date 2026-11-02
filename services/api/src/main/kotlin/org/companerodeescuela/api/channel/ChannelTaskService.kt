package org.companerodeescuela.api.channel

import org.companerodeescuela.api.auth.SessionIdentitySource
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.ChannelAttachment
import org.companerodeescuela.shared.contracts.ChannelFileScope
import org.companerodeescuela.shared.contracts.ChannelFileState
import org.companerodeescuela.shared.contracts.ChannelFileUploadProgress
import org.companerodeescuela.shared.contracts.ChannelPrivateFileMetadata
import org.companerodeescuela.shared.contracts.ChannelTask
import org.companerodeescuela.shared.contracts.CreateChannelFileRequest
import org.companerodeescuela.shared.contracts.CreateChannelTaskRequest
import org.companerodeescuela.shared.contracts.ReviewSubmissionRequest
import org.companerodeescuela.shared.contracts.SubmitTaskRequest
import org.companerodeescuela.shared.contracts.TaskStatus
import org.companerodeescuela.shared.contracts.TaskSubmission
import org.companerodeescuela.shared.contracts.TaskSubmissionVersion
import org.companerodeescuela.shared.contracts.UpdateChannelTaskRequest
import org.companerodeescuela.shared.contracts.UserRole
import java.security.MessageDigest
import java.time.Clock
import java.util.UUID

class ChannelTaskService(
    private val repository: ChannelTaskRepository,
    private val accessPolicy: ChannelAccessPolicy,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun listTasks(
        userId: String,
        roles: Set<UserRole>,
        channelId: String,
        actor: org.companerodeescuela.shared.contracts.UserSummary? = null,
        identitySource: SessionIdentitySource = SessionIdentitySource.INSTITUTIONAL,
    ): List<ChannelTask> {
        accessPolicy.requireCanRead(userId, roles, channelId, actor, identitySource)
        return repository.listTasks(channelId).map { task ->
            task.copy(
                files =
                    repository
                        .filesForTask(channelId, task.id)
                        .filter { it.state == ChannelFileState.AVAILABLE },
            )
        }
    }

    suspend fun getTask(
        userId: String,
        roles: Set<UserRole>,
        channelId: String,
        taskId: String,
        actor: org.companerodeescuela.shared.contracts.UserSummary? = null,
        identitySource: SessionIdentitySource = SessionIdentitySource.INSTITUTIONAL,
    ): ChannelTask {
        accessPolicy.requireCanRead(userId, roles, channelId, actor, identitySource)
        return requireTask(channelId, taskId).withFiles()
    }

    suspend fun createTask(
        userId: String,
        displayName: String,
        roles: Set<UserRole>,
        channelId: String,
        request: CreateChannelTaskRequest,
        actor: org.companerodeescuela.shared.contracts.UserSummary? = null,
        identitySource: SessionIdentitySource = SessionIdentitySource.INSTITUTIONAL,
    ): ChannelTask {
        val channel = accessPolicy.requireCanPublish(userId, roles, channelId, actor, identitySource)
        validateTask(request.title, request.instructions, request.dueDateEpochSeconds, request.attachments)
        if (request.fileIds.isNotEmpty()) {
            throw ApiException.Validation("Upload task files through the private task file route")
        }
        val task =
            ChannelTask(
                id = UUID.randomUUID().toString(),
                channelId = channelId,
                teacherId = userId,
                teacherDisplayName = displayName,
                title = request.title.trim(),
                instructions = request.instructions.trim(),
                dueDateEpochSeconds = request.dueDateEpochSeconds,
                attachments = request.attachments,
                createdAtEpochSeconds = clock.instant().epochSecond,
            )
        if (!channel.canPublish) throw ApiException.Forbidden()
        if (!repository.createTask(task)) throw ApiException.Conflict("Task could not be created")
        return task
    }

    suspend fun updateTask(
        userId: String,
        roles: Set<UserRole>,
        channelId: String,
        taskId: String,
        request: UpdateChannelTaskRequest,
        actor: org.companerodeescuela.shared.contracts.UserSummary? = null,
        identitySource: SessionIdentitySource = SessionIdentitySource.INSTITUTIONAL,
    ): ChannelTask {
        val channel = accessPolicy.requireCanPublish(userId, roles, channelId, actor, identitySource)
        val current = requireTask(channelId, taskId)
        requireTaskOwner(current, userId, roles, channel.canPublish)
        if (request.expectedVersion != current.version) throw ApiException.Conflict("Task version is stale")
        if (request.fileIds != null) {
            throw ApiException.Validation("Upload task files through the private task file route")
        }
        val updated =
            current.copy(
                title = request.title?.trim() ?: current.title,
                instructions = request.instructions?.trim() ?: current.instructions,
                dueDateEpochSeconds = request.dueDateEpochSeconds ?: current.dueDateEpochSeconds,
                attachments = request.attachments ?: current.attachments,
                closed = request.closed ?: current.closed,
                version = current.version + 1,
            )
        validateTask(updated.title, updated.instructions, updated.dueDateEpochSeconds, updated.attachments)
        if (!repository.replaceTask(updated, current.version)) throw ApiException.Conflict("Task was updated by another request")
        return updated.withFiles()
    }

    suspend fun submissionsFor(
        userId: String,
        roles: Set<UserRole>,
        channelId: String,
        taskId: String,
        actor: org.companerodeescuela.shared.contracts.UserSummary? = null,
        identitySource: SessionIdentitySource = SessionIdentitySource.INSTITUTIONAL,
    ): List<TaskSubmission> {
        accessPolicy.requireCanRead(userId, roles, channelId, actor, identitySource)
        val task = requireTask(channelId, taskId)
        val isTaskManager =
            (UserRole.TEACHER in roles && task.teacherId == userId) ||
                roles.any { it == UserRole.ADMIN || it == UserRole.SUPER_ADMIN }
        val values =
            when {
                isTaskManager -> {
                    val channel = accessPolicy.requireCanPublish(userId, roles, channelId, actor, identitySource)
                    requireTaskOwner(task, userId, roles, channel.canPublish)
                    repository.listSubmissions(channelId, taskId)
                }
                UserRole.STUDENT in roles -> listOfNotNull(repository.findSubmissionByStudent(task.id, userId))
                else -> throw ApiException.Forbidden("Student or task teacher role is required")
            }
        return values.map { it.withFiles() }
    }

    suspend fun submit(
        userId: String,
        displayName: String,
        roles: Set<UserRole>,
        channelId: String,
        taskId: String,
        request: SubmitTaskRequest,
        actor: org.companerodeescuela.shared.contracts.UserSummary? = null,
        identitySource: SessionIdentitySource = SessionIdentitySource.INSTITUTIONAL,
    ): TaskSubmission {
        if (UserRole.STUDENT !in roles) throw ApiException.Forbidden("Student role is required")
        accessPolicy.requireCanRead(userId, roles, channelId, actor, identitySource)
        val task = requireTask(channelId, taskId)
        if (task.closed || task.dueDateEpochSeconds < clock.instant().epochSecond) {
            throw ApiException.Conflict("This task is closed")
        }
        validateSubmission(request.comment, request.attachments)
        request.idempotencyKey?.let {
            if (it.length !in 8..128 || it.any(Char::isISOControl)) {
                throw ApiException.Validation("Idempotency key is invalid")
            }
        }
        val existing = repository.findSubmissionByStudent(taskId, userId)
        val submissionId = request.submissionId ?: existing?.id
        submissionId?.let { requireUuid(it, "submissionId") }
        if (existing != null && submissionId != null && existing.id != submissionId) {
            throw ApiException.Conflict("Use the existing submissionId to submit again")
        }
        if (existing == null && request.expectedVersion != null) {
            throw ApiException.Conflict("There is no existing submission to update")
        }
        val files =
            request.fileIds.distinct().map { fileId ->
                val file =
                    repository.findFile(fileId)?.metadata
                        ?: throw ApiException.Validation("Submission file was not uploaded")
                if (file.channelId != channelId ||
                    file.taskId != taskId ||
                    file.scope != ChannelFileScope.SUBMISSION ||
                    file.submissionId != submissionId ||
                    file.ownerId != userId ||
                    file.state != ChannelFileState.AVAILABLE
                ) {
                    throw ApiException.Validation("Submission file is unavailable or belongs to another submission")
                }
                file
            }
        if (request.fileIds.size != request.fileIds.distinct().size) {
            throw ApiException.Validation("Duplicate submission file ids are not allowed")
        }
        val now = clock.instant().epochSecond
        if (existing != null) {
            if (request.idempotencyKey != null && request.idempotencyKey in existing.idempotencyKeys) {
                if (existing.status != TaskStatus.RETURNED && isSameSubmission(existing, request)) {
                    return existing.withFiles()
                }
                throw ApiException.Conflict("Idempotency key was already used for a different submission")
            }
            if (existing.status != TaskStatus.RETURNED &&
                existing.comment == request.comment?.trim()?.takeIf(String::isNotEmpty) &&
                existing.attachments == request.attachments &&
                existing.fileIds == request.fileIds.distinct()
            ) {
                return existing.withFiles()
            }
            if (existing.status != TaskStatus.RETURNED) {
                throw ApiException.Conflict("A submission already exists for this task")
            }
            if (request.expectedVersion != existing.version) throw ApiException.Conflict("Submission version is stale")
            val updated =
                existing.copy(
                    status = TaskStatus.SUBMITTED,
                    comment = request.comment?.trim()?.takeIf(String::isNotEmpty),
                    attachments = request.attachments,
                    fileIds = request.fileIds.distinct(),
                    submittedAtEpochSeconds = now,
                    feedback = null,
                    reviewedAtEpochSeconds = null,
                    version = existing.version + 1,
                    history =
                        existing.history +
                            versionEntry(
                                existing.version + 1,
                                TaskStatus.SUBMITTED,
                                request.comment?.trim()?.takeIf(String::isNotEmpty),
                                request.fileIds.distinct(),
                                null,
                                userId,
                                now,
                            ),
                    idempotencyKeys = existing.idempotencyKeys + listOfNotNull(request.idempotencyKey),
                )
            if (!repository.replaceSubmission(updated, existing.version)) {
                val winner = repository.findSubmissionByStudent(taskId, userId)
                if (winner != null && isSameSubmission(winner, request)) return winner.withFiles()
                throw ApiException.Conflict("Submission was updated by another request")
            }
            return updated.withFiles()
        }

        val id = submissionId ?: UUID.randomUUID().toString()
        if (files.any { it.submissionId != id }) {
            throw ApiException.Validation("Submission file scope does not match submissionId")
        }
        val firstVersion =
            versionEntry(
                1,
                TaskStatus.SUBMITTED,
                request.comment?.trim()?.takeIf(String::isNotEmpty),
                request.fileIds.distinct(),
                null,
                userId,
                now,
            )
        val created =
            TaskSubmission(
                id = id,
                taskId = taskId,
                channelId = channelId,
                studentId = userId,
                studentDisplayName = displayName,
                status = TaskStatus.SUBMITTED,
                comment = request.comment?.trim()?.takeIf(String::isNotEmpty),
                attachments = request.attachments,
                submittedAtEpochSeconds = now,
                version = 1,
                history = listOf(firstVersion),
                fileIds = request.fileIds.distinct(),
                idempotencyKeys = setOfNotNull(request.idempotencyKey),
            )
        if (!repository.createSubmission(created)) {
            val winner = repository.findSubmissionByStudent(taskId, userId)
            if (winner != null && isSameSubmission(winner, request)) return winner.withFiles()
            throw ApiException.Conflict("A submission already exists for this task")
        }
        return created.withFiles()
    }

    suspend fun review(
        userId: String,
        roles: Set<UserRole>,
        channelId: String,
        taskId: String,
        submissionId: String,
        request: ReviewSubmissionRequest,
        actor: org.companerodeescuela.shared.contracts.UserSummary? = null,
        identitySource: SessionIdentitySource = SessionIdentitySource.INSTITUTIONAL,
    ): TaskSubmission {
        if (request.status !in setOf(TaskStatus.RETURNED, TaskStatus.REVIEWED)) {
            throw ApiException.Validation("Review status must be returned or reviewed")
        }
        val channel = accessPolicy.requireCanPublish(userId, roles, channelId, actor, identitySource)
        val task = requireTask(channelId, taskId)
        requireTaskOwner(task, userId, roles, channel.canPublish)
        val current =
            repository.findSubmission(channelId, taskId, submissionId)
                ?: throw ApiException.NotFound("Task submission was not found")
        if (current.status != TaskStatus.SUBMITTED) {
            throw ApiException.Conflict("Only submitted work can be reviewed")
        }
        if (request.expectedVersion != current.version) throw ApiException.Conflict("Submission version is stale")
        val feedback = request.feedback?.trim()?.takeIf(String::isNotEmpty)
        if (request.status == TaskStatus.RETURNED && feedback == null) {
            throw ApiException.Validation("Feedback is required when returning work")
        }
        val now = clock.instant().epochSecond
        val updated =
            current.copy(
                status = request.status,
                feedback = feedback,
                reviewedAtEpochSeconds = now,
                version = current.version + 1,
                history =
                    current.history +
                        versionEntry(
                            current.version + 1,
                            request.status,
                            current.comment,
                            current.fileIds,
                            feedback,
                            userId,
                            now,
                        ),
            )
        if (!repository.replaceSubmission(updated, current.version)) {
            throw ApiException.Conflict("Submission was updated by another request")
        }
        return updated.withFiles()
    }

    suspend fun reserveFile(
        userId: String,
        roles: Set<UserRole>,
        channelId: String,
        taskId: String,
        request: CreateChannelFileRequest,
        scope: ChannelFileScope,
        submissionId: String? = null,
        actor: org.companerodeescuela.shared.contracts.UserSummary? = null,
        identitySource: SessionIdentitySource = SessionIdentitySource.INSTITUTIONAL,
    ): ChannelFileUploadProgress {
        if (scope == ChannelFileScope.TASK) {
            val channel = accessPolicy.requireCanPublish(userId, roles, channelId, actor, identitySource)
            val task = requireTask(channelId, taskId)
            requireTaskOwner(task, userId, roles, channel.canPublish)
            if (task.closed) throw ApiException.Conflict("This task is closed")
        } else {
            if (UserRole.STUDENT !in roles) throw ApiException.Forbidden("Student role is required")
            accessPolicy.requireCanRead(userId, roles, channelId, actor, identitySource)
            val task = requireTask(channelId, taskId)
            if (task.closed || task.dueDateEpochSeconds < clock.instant().epochSecond) {
                throw ApiException.Conflict("This task is closed")
            }
            val id = submissionId ?: throw ApiException.Validation("submissionId is required")
            requireUuid(id, "submissionId")
            repository.findSubmissionByStudent(taskId, userId)?.let {
                if (it.id != id) throw ApiException.Conflict("Use the existing submissionId to upload a resubmission")
            }
        }
        val currentFiles =
            if (scope == ChannelFileScope.TASK) {
                repository.filesForTask(channelId, taskId)
            } else {
                repository.filesForSubmission(channelId, taskId, submissionId!!)
            }
        if (currentFiles.size >= MAX_FILES) throw ApiException.Validation("Too many files for this task")
        validateFileRequest(request)
        val id = UUID.randomUUID().toString()
        val path =
            when (scope) {
                ChannelFileScope.TASK -> "/channels/$channelId/tasks/$taskId/files/$id"
                ChannelFileScope.SUBMISSION -> "/channels/$channelId/tasks/$taskId/submissions/$submissionId/files/$id"
            }
        val metadata =
            ChannelPrivateFileMetadata(
                id = id,
                channelId = channelId,
                taskId = taskId,
                submissionId = submissionId,
                ownerId = userId,
                fileName = request.fileName,
                contentType = request.contentType,
                sizeBytes = request.sizeBytes,
                uploadedBytes = 0,
                state = ChannelFileState.UPLOADING,
                scope = scope,
                createdAtEpochSeconds = clock.instant().epochSecond,
                downloadPath = path,
            )
        if (!repository.createFile(metadata)) throw ApiException.Conflict("File upload could not be initialized")
        return metadata.toProgress()
    }

    suspend fun completeFileUpload(
        userId: String,
        channelId: String,
        taskId: String,
        fileId: String,
        bytes: ByteArray,
        actor: org.companerodeescuela.shared.contracts.UserSummary? = null,
        roles: Set<UserRole>,
        identitySource: SessionIdentitySource,
    ): ChannelFileUploadProgress {
        val file = requireFile(channelId, taskId, fileId)
        authorizeFileAccess(file, userId, roles, actor, identitySource, requireOwnerForUpload = true)
        if (bytes.size.toLong() != file.sizeBytes) {
            throw ApiException.Validation("Uploaded byte count does not match the declared size")
        }
        validateFileContent(file, bytes)
        if (file.state == ChannelFileState.AVAILABLE) {
            val existing = repository.findFile(fileId)?.bytes ?: throw ApiException.NotFound("Private file was not found")
            if (!MessageDigest.isEqual(existing, bytes)) throw ApiException.Conflict("This file was already uploaded")
            return file.toProgress(complete = true)
        }
        if (!repository.completeFile(fileId, userId, bytes)) {
            val winner = repository.findFile(fileId)
            if (winner?.metadata?.state == ChannelFileState.AVAILABLE &&
                winner.bytes != null &&
                MessageDigest.isEqual(winner.bytes, bytes)
            ) {
                return winner.metadata.toProgress(complete = true)
            }
            throw ApiException.Conflict("File upload was already completed or changed")
        }
        return requireFile(channelId, taskId, fileId).toProgress(complete = true)
    }

    suspend fun authorizeFileUpload(
        userId: String,
        roles: Set<UserRole>,
        channelId: String,
        taskId: String,
        fileId: String,
        actor: org.companerodeescuela.shared.contracts.UserSummary?,
        identitySource: SessionIdentitySource,
        submissionId: String? = null,
    ): ChannelPrivateFileMetadata {
        val file = requireFile(channelId, taskId, fileId)
        if (file.scope == ChannelFileScope.SUBMISSION && file.submissionId != submissionId) {
            throw ApiException.NotFound("Private file was not found")
        }
        authorizeFileAccess(file, userId, roles, actor, identitySource, requireOwnerForUpload = true)
        return file
    }

    suspend fun fileUploadProgress(
        userId: String,
        roles: Set<UserRole>,
        channelId: String,
        taskId: String,
        fileId: String,
        actor: org.companerodeescuela.shared.contracts.UserSummary?,
        identitySource: SessionIdentitySource,
        submissionId: String? = null,
    ): ChannelFileUploadProgress {
        val file = requireFile(channelId, taskId, fileId)
        if (file.scope == ChannelFileScope.SUBMISSION && file.submissionId != submissionId) {
            throw ApiException.NotFound("Private file was not found")
        }
        if (file.ownerId != userId) throw ApiException.Forbidden()
        authorizeFileAccess(file, userId, roles, actor, identitySource, requireOwnerForUpload = true)
        return file.toProgress()
    }

    suspend fun downloadFile(
        userId: String,
        roles: Set<UserRole>,
        channelId: String,
        taskId: String,
        fileId: String,
        actor: org.companerodeescuela.shared.contracts.UserSummary? = null,
        identitySource: SessionIdentitySource = SessionIdentitySource.INSTITUTIONAL,
        submissionId: String? = null,
    ): StoredChannelFile {
        val file = requireFile(channelId, taskId, fileId)
        if (file.scope == ChannelFileScope.SUBMISSION && file.submissionId != submissionId) {
            throw ApiException.NotFound("Private file was not found")
        }
        if (file.scope == ChannelFileScope.SUBMISSION && file.ownerId != userId) {
            val submission =
                repository.findSubmission(channelId, taskId, file.submissionId.orEmpty())
                    ?: throw ApiException.NotFound("Private file was not found")
            if (fileId !in submission.fileIds) throw ApiException.NotFound("Private file was not found")
        }
        authorizeFileAccess(file, userId, roles, actor, identitySource, requireOwnerForUpload = false)
        val stored = repository.findFile(fileId) ?: throw ApiException.NotFound("Private file was not found")
        if (stored.metadata.state != ChannelFileState.AVAILABLE || stored.bytes == null) {
            throw ApiException.Conflict("File upload is not complete")
        }
        return stored
    }

    private suspend fun authorizeFileAccess(
        file: ChannelPrivateFileMetadata,
        userId: String,
        roles: Set<UserRole>,
        actor: org.companerodeescuela.shared.contracts.UserSummary?,
        identitySource: SessionIdentitySource,
        requireOwnerForUpload: Boolean,
    ) {
        if (requireOwnerForUpload && file.ownerId != userId) throw ApiException.Forbidden()
        when (file.scope) {
            ChannelFileScope.TASK -> {
                val channel =
                    if (requireOwnerForUpload) {
                        accessPolicy.requireCanPublish(userId, roles, file.channelId, actor, identitySource)
                    } else {
                        accessPolicy.requireCanRead(userId, roles, file.channelId, actor, identitySource)
                    }
                if (requireOwnerForUpload) {
                    val task = requireTask(file.channelId, file.taskId)
                    requireTaskOwner(task, userId, roles, channel.canPublish)
                    if (task.closed) throw ApiException.Conflict("This task is closed")
                }
            }
            ChannelFileScope.SUBMISSION -> {
                accessPolicy.requireCanRead(userId, roles, file.channelId, actor, identitySource)
                if (requireOwnerForUpload) {
                    val task = requireTask(file.channelId, file.taskId)
                    if (task.closed || task.dueDateEpochSeconds < clock.instant().epochSecond) {
                        throw ApiException.Conflict("This task is closed")
                    }
                }
                if (!requireOwnerForUpload && file.ownerId != userId) {
                    val task = requireTask(file.channelId, file.taskId)
                    val channel = accessPolicy.requireCanPublish(userId, roles, file.channelId, actor, identitySource)
                    requireTaskOwner(task, userId, roles, channel.canPublish)
                }
            }
        }
    }

    private suspend fun requireTask(
        channelId: String,
        taskId: String,
    ): ChannelTask = repository.findTask(channelId, taskId) ?: throw ApiException.NotFound("Channel task was not found")

    private suspend fun requireFile(
        channelId: String,
        taskId: String,
        fileId: String,
    ): ChannelPrivateFileMetadata =
        repository.findFile(fileId)?.metadata?.takeIf { it.channelId == channelId && it.taskId == taskId }
            ?: throw ApiException.NotFound("Private file was not found")

    private suspend fun ChannelTask.withFiles(): ChannelTask =
        copy(files = repository.filesForTask(channelId, id).filter { it.state == ChannelFileState.AVAILABLE })

    private suspend fun TaskSubmission.withFiles(): TaskSubmission =
        copy(
            files =
                repository
                    .filesForSubmission(channelId, taskId, id)
                    .filter { it.state == ChannelFileState.AVAILABLE && it.id in fileIds },
        )

    private fun requireTaskOwner(
        task: ChannelTask,
        userId: String,
        roles: Set<UserRole>,
        canPublish: Boolean,
    ) {
        if (task.teacherId != userId &&
            !(canPublish && roles.any { it == UserRole.ADMIN || it == UserRole.SUPER_ADMIN })
        ) {
            throw ApiException.Forbidden("Only the task owner or scoped administration may manage it")
        }
    }

    private fun validateTask(
        title: String,
        instructions: String,
        dueDate: Long,
        attachments: List<ChannelAttachment>,
    ) {
        if (title.isBlank() || title.length > MAX_TITLE_LENGTH) throw ApiException.Validation("Task title is invalid")
        if (instructions.length > MAX_INSTRUCTIONS_LENGTH) throw ApiException.Validation("Task instructions are too long")
        if (dueDate <= 0) throw ApiException.Validation("Task due date is invalid")
        validateAttachments(attachments)
    }

    private fun validateSubmission(
        comment: String?,
        attachments: List<ChannelAttachment>,
    ) {
        if ((comment?.length ?: 0) > MAX_COMMENT_LENGTH) throw ApiException.Validation("Submission comment is too long")
        validateAttachments(attachments)
    }

    private fun validateAttachments(attachments: List<ChannelAttachment>) {
        if (attachments.size > MAX_FILES) throw ApiException.Validation("Too many attachments")
        attachments.forEach {
            if (it.label.isBlank() || it.label.length > 160 || !it.url.startsWith("https://")) {
                throw ApiException.Validation("Attachments must be labeled HTTPS URLs")
            }
        }
    }

    private fun validateFileRequest(request: CreateChannelFileRequest) {
        if (!request.fileName.matches(Regex("[A-Za-z0-9][A-Za-z0-9._ -]{0,159}"))) {
            throw ApiException.Validation("File name is invalid")
        }
        if (request.sizeBytes !in 1..MAX_FILE_BYTES.toLong()) {
            throw ApiException.Validation("File must be between 1 byte and $MAX_FILE_BYTES bytes")
        }
        val expectedType = ALLOWED_EXTENSIONS[request.fileName.substringAfterLast('.', "").lowercase()]
        if (expectedType == null || request.contentType.lowercase() !in expectedType) {
            throw ApiException.Validation("File type is not allowed")
        }
    }

    private fun validateFileContent(
        file: ChannelPrivateFileMetadata,
        bytes: ByteArray,
    ) {
        if (bytes.isEmpty() || bytes.size > MAX_FILE_BYTES) throw ApiException.Validation("File size is invalid")
        val valid =
            when (file.contentType.lowercase()) {
                "application/pdf" -> bytes.startsWithAscii("%PDF-")
                "image/png" -> bytes.startsWith(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))
                "image/jpeg" -> bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte()
                "application/msword" ->
                    bytes.startsWith(
                        byteArrayOf(0xd0.toByte(), 0xcf.toByte(), 0x11, 0xe0.toByte(), 0xa1.toByte(), 0xb1.toByte(), 0x1a, 0xe1.toByte()),
                    )
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                -> bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
                "text/plain", "text/csv" -> bytes.none { it == 0.toByte() }
                else -> false
            }
        if (!valid) throw ApiException.Validation("File content does not match its declared type")
    }

    private fun ByteArray.startsWithAscii(value: String): Boolean =
        size >= value.length && value.indices.all { this[it] == value[it].code.toByte() }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private fun requireUuid(
        value: String,
        field: String,
    ) {
        if (runCatching { UUID.fromString(value).toString() == value.lowercase() }.getOrDefault(false).not()) {
            throw ApiException.Validation("$field must be a UUID")
        }
    }

    private fun CreateChannelFileRequest.toProgress(
        fileId: String,
        path: String,
    ) = ChannelFileUploadProgress(
        fileId = fileId,
        uploadedBytes = 0,
        totalBytes = sizeBytes,
        complete = false,
        uploadPath = path,
    )

    private fun ChannelPrivateFileMetadata.toProgress(complete: Boolean = state == ChannelFileState.AVAILABLE) =
        ChannelFileUploadProgress(
            fileId = id,
            uploadedBytes = uploadedBytes,
            totalBytes = sizeBytes,
            complete = complete,
            uploadPath = downloadPath,
        )

    private fun versionEntry(
        version: Int,
        status: TaskStatus,
        comment: String?,
        fileIds: List<String>,
        feedback: String?,
        changedBy: String,
        changedAt: Long,
    ) = TaskSubmissionVersion(version, status, comment, fileIds, feedback, changedBy, changedAt)

    private fun isSameSubmission(
        current: TaskSubmission,
        request: SubmitTaskRequest,
    ): Boolean =
        current.comment == request.comment?.trim()?.takeIf(String::isNotEmpty) &&
            current.attachments == request.attachments &&
            current.fileIds == request.fileIds.distinct()

    private companion object {
        const val MAX_TITLE_LENGTH = 160
        const val MAX_INSTRUCTIONS_LENGTH = 16_000
        const val MAX_COMMENT_LENGTH = 4_000
        const val MAX_FILES = 8
        const val MAX_FILE_BYTES = 8 * 1024 * 1024
        val ALLOWED_EXTENSIONS =
            mapOf(
                "pdf" to setOf("application/pdf"),
                "png" to setOf("image/png"),
                "jpg" to setOf("image/jpeg"),
                "jpeg" to setOf("image/jpeg"),
                "txt" to setOf("text/plain"),
                "csv" to setOf("text/csv"),
                "doc" to setOf("application/msword"),
                "docx" to setOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
                "pptx" to setOf("application/vnd.openxmlformats-officedocument.presentationml.presentation"),
                "xlsx" to setOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            )
    }
}
