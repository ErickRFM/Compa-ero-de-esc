package org.companerodeescuela.feature.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.shared.contracts.ChannelAcknowledgementRequest
import org.companerodeescuela.shared.contracts.ChannelAttachment
import org.companerodeescuela.shared.contracts.ChannelAttachmentType
import org.companerodeescuela.shared.contracts.ChannelPost
import org.companerodeescuela.shared.contracts.ChannelPresetResponse
import org.companerodeescuela.shared.contracts.ChannelPrivateFileMetadata
import org.companerodeescuela.shared.contracts.ChannelTask
import org.companerodeescuela.shared.contracts.ClassChannelSummary
import org.companerodeescuela.shared.contracts.CreateChannelFileRequest
import org.companerodeescuela.shared.contracts.CreateChannelPostRequest
import org.companerodeescuela.shared.contracts.CreateChannelTaskRequest
import org.companerodeescuela.shared.contracts.ReviewSubmissionRequest
import org.companerodeescuela.shared.contracts.SubmitTaskRequest
import org.companerodeescuela.shared.contracts.TaskStatus
import org.companerodeescuela.shared.contracts.TaskSubmission
import org.companerodeescuela.shared.contracts.TeacherClassContext
import org.companerodeescuela.shared.contracts.UpdateChannelTaskRequest
import java.util.UUID
import javax.inject.Inject

data class ChannelTaskUpload(
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray,
)

data class ChannelTaskDownload(
    val requestId: String,
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray,
)

data class ChannelUiState(
    val loading: Boolean = true,
    val tasksLoading: Boolean = false,
    val actionInProgress: Boolean = false,
    val channels: List<ClassChannelSummary> = emptyList(),
    val selectedChannelId: String? = null,
    val resolvedNotificationChannelId: String? = null,
    val posts: List<ChannelPost> = emptyList(),
    val tasks: List<ChannelTask> = emptyList(),
    val submissions: Map<String, List<TaskSubmission>> = emptyMap(),
    val submissionsLoading: Set<String> = emptySet(),
    val draftBody: String = "",
    val draftResourceLabel: String = "",
    val draftResourceUrl: String = "",
    val draftTaskTitle: String = "",
    val draftTaskInstructions: String = "",
    val successMessage: String? = null,
    val errorMessage: String? = null,
    val downloadReady: ChannelTaskDownload? = null,
)

@HiltViewModel
class ChannelViewModel
    @Inject
    constructor(
        private val repository: ChannelRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(ChannelUiState())
        val state: StateFlow<ChannelUiState> = _state.asStateFlow()
        private var requestedClassroom: TeacherClassContext? = null
        private var requestedNotificationChannelId: String? = null
        private var refreshingChannels = false

        /** A push identifier never bypasses the fresh, authenticated list of readable channels. */
        fun requestChannel(channelId: String) {
            requestedNotificationChannelId = channelId
            requestedClassroom = null
            _state.update { it.copy(resolvedNotificationChannelId = null) }
            if (_state.value.actionInProgress) return
            transitionTo(null)
            refreshChannels()
        }

        fun consumeRequestedChannel() {
            _state.update { it.copy(resolvedNotificationChannelId = null) }
        }

        fun requestClassroom(selection: TeacherClassContext) {
            requestedClassroom = selection
            if (_state.value.channels.isNotEmpty()) resolveRequestedChannel()
        }

        private fun resolveRequestedChannel(): String? {
            val context = requestedClassroom ?: return _state.value.selectedChannelId
            val match =
                _state.value.channels.singleOrNull {
                    it.canPublish && it.id == context.classroomId && it.courseId == context.classroomId
                }
            if (match != null) {
                selectChannel(match.id)
                requestedClassroom = null
            } else {
                transitionTo(null)
                _state.update {
                    it.copy(
                        loading = false,
                        errorMessage = "No hay un canal autorizado para ${context.subjectName} · ${context.groupName.orEmpty()}.",
                    )
                }
            }
            return match?.id
        }

        init {
            refreshChannels()
        }

        fun refreshChannels() {
            if (_state.value.actionInProgress || refreshingChannels) return
            refreshingChannels = true
            _state.update { it.copy(loading = true, errorMessage = null) }
            viewModelScope.launch {
                val result = repository.channels()
                refreshingChannels = false
                when (result) {
                    is Outcome.Success -> {
                        val previous = _state.value.selectedChannelId
                        val selected =
                            result.value.firstOrNull { it.id == previous }
                                ?: result.value.firstOrNull()
                        _state.update {
                            it.copy(
                                loading = false,
                                channels = result.value,
                                errorMessage = null,
                            )
                        }
                        val notifiedChannelId = requestedNotificationChannelId
                        if (notifiedChannelId != null) {
                            requestedNotificationChannelId = null
                            val notifiedChannel = result.value.singleOrNull { it.id == notifiedChannelId }
                            transitionTo(notifiedChannel?.id)
                            _state.update { it.copy(resolvedNotificationChannelId = notifiedChannelId) }
                            if (notifiedChannel != null) {
                                loadPosts(notifiedChannel.id)
                            } else {
                                _state.update {
                                    it.copy(errorMessage = "El canal de esta notificación ya no está disponible o autorizado.")
                                }
                            }
                        } else if (requestedClassroom != null) {
                            resolveRequestedChannel()?.let(::loadPosts)
                        } else {
                            transitionTo(selected?.id)
                            selected?.let { loadPosts(it.id) }
                        }
                    }
                    is Outcome.Failure -> {
                        val notifiedChannelId = requestedNotificationChannelId
                        requestedNotificationChannelId = null
                        transitionTo(null)
                        _state.update {
                            it.copy(
                                loading = false,
                                channels = emptyList(),
                                selectedChannelId = null,
                                resolvedNotificationChannelId = notifiedChannelId,
                                posts = emptyList(),
                                errorMessage = result.error.userMessage,
                            )
                        }
                    }
                }
            }
        }

        fun selectChannel(channelId: String) {
            if (_state.value.actionInProgress || _state.value.channels.none { it.id == channelId }) return
            if (_state.value.selectedChannelId == channelId) return
            transitionTo(channelId)
            loadPosts(channelId)
        }

        private fun transitionTo(channelId: String?) {
            if (_state.value.selectedChannelId == channelId) return
            _state.update {
                it.copy(
                    selectedChannelId = channelId,
                    tasksLoading = false,
                    posts = emptyList(),
                    tasks = emptyList(),
                    submissions = emptyMap(),
                    submissionsLoading = emptySet(),
                    draftBody = "",
                    draftResourceLabel = "",
                    draftResourceUrl = "",
                    draftTaskTitle = "",
                    draftTaskInstructions = "",
                    successMessage = null,
                    errorMessage = null,
                )
            }
        }

        fun refreshPosts() {
            _state.value.selectedChannelId?.let(::loadPosts)
        }

        fun updateDraftBody(value: String) {
            _state.update { it.copy(draftBody = value.take(MAX_DRAFT_BODY)) }
        }

        fun updateDraftResourceLabel(value: String) {
            _state.update { it.copy(draftResourceLabel = value.take(MAX_RESOURCE_LABEL)) }
        }

        fun updateDraftResourceUrl(value: String) {
            _state.update { it.copy(draftResourceUrl = value.trim().take(MAX_RESOURCE_URL)) }
        }

        fun publish() {
            val snapshot = _state.value
            val channelId = snapshot.selectedChannelId ?: return
            val selected = snapshot.channels.firstOrNull { it.id == channelId } ?: return
            if (!selected.canPublish || snapshot.actionInProgress || snapshot.loading) return

            val body = snapshot.draftBody.trim()
            val resourceUrl = snapshot.draftResourceUrl.trim()
            val resourceLabel = snapshot.draftResourceLabel.trim()

            if (body.isBlank() && resourceUrl.isBlank()) {
                _state.update { it.copy(errorMessage = "Escribe un aviso o agrega un recurso.") }
                return
            }
            if (resourceUrl.isNotBlank() && !resourceUrl.startsWith("https://")) {
                _state.update { it.copy(errorMessage = "El recurso debe usar un enlace HTTPS.") }
                return
            }

            val attachments =
                if (resourceUrl.isBlank()) {
                    emptyList()
                } else {
                    listOf(
                        ChannelAttachment(
                            type = attachmentType(resourceUrl),
                            label = resourceLabel.ifBlank { defaultResourceLabel(resourceUrl) },
                            url = resourceUrl,
                        ),
                    )
                }

            _state.update { it.copy(actionInProgress = true, errorMessage = null, successMessage = null) }
            viewModelScope.launch {
                when (
                    val result =
                        repository.publish(
                            channelId = channelId,
                            request =
                                CreateChannelPostRequest(
                                    body = body.ifBlank { null },
                                    attachments = attachments,
                                    allowedResponses = setOf(ChannelPresetResponse.ACKNOWLEDGED),
                                ),
                        )
                ) {
                    is Outcome.Success ->
                        if (_state.value.selectedChannelId == channelId) {
                            _state.update {
                                it.copy(
                                    actionInProgress = false,
                                    posts =
                                        listOf(result.value) +
                                            it.posts.filterNot { post ->
                                                post.id == result.value.id
                                            },
                                    draftBody = "",
                                    draftResourceLabel = "",
                                    draftResourceUrl = "",
                                    successMessage = "Publicado para el grupo.",
                                    errorMessage = null,
                                )
                            }
                        }
                    is Outcome.Failure ->
                        if (_state.value.selectedChannelId == channelId) {
                            _state.update {
                                it.copy(
                                    actionInProgress = false,
                                    errorMessage = result.error.userMessage,
                                )
                            }
                        }
                }
            }
        }

        fun acknowledge(
            post: ChannelPost,
            response: ChannelPresetResponse,
        ) {
            val channelId = _state.value.selectedChannelId ?: return
            if (response !in post.allowedResponses || _state.value.actionInProgress) return
            if (_state.value.loading || post.channelId != channelId) return

            _state.update { it.copy(actionInProgress = true, errorMessage = null, successMessage = null) }
            viewModelScope.launch {
                when (
                    val result =
                        repository.acknowledge(
                            channelId = channelId,
                            postId = post.id,
                            request = ChannelAcknowledgementRequest(response),
                        )
                ) {
                    is Outcome.Success ->
                        _state.update {
                            it.copy(
                                actionInProgress = false,
                                successMessage = "Respuesta registrada.",
                            )
                        }
                    is Outcome.Failure ->
                        _state.update {
                            it.copy(
                                actionInProgress = false,
                                errorMessage = result.error.userMessage,
                            )
                        }
                }
            }
        }

        fun updateDraftTaskTitle(value: String) {
            _state.update { it.copy(draftTaskTitle = value.take(200)) }
        }

        fun updateDraftTaskInstructions(value: String) {
            _state.update { it.copy(draftTaskInstructions = value.take(MAX_DRAFT_BODY)) }
        }

        fun createTask(dueDateSeconds: Long) {
            val snapshot = _state.value
            val channelId = snapshot.selectedChannelId ?: return
            val selected = snapshot.channels.firstOrNull { it.id == channelId }
            if (selected?.canPublish != true) {
                _state.update { it.copy(errorMessage = "Solo el docente asignado puede publicar tareas.") }
                return
            }
            if (snapshot.actionInProgress || snapshot.loading) return
            val title = snapshot.draftTaskTitle.trim()
            val instructions = snapshot.draftTaskInstructions.trim()
            if (title.isBlank()) {
                _state.update { it.copy(errorMessage = "Ingresa el título de la tarea.") }
                return
            }
            _state.update { it.copy(actionInProgress = true, errorMessage = null, successMessage = null) }
            viewModelScope.launch {
                when (
                    val result =
                        repository.createTask(
                            channelId = channelId,
                            request = CreateChannelTaskRequest(title, instructions, dueDateSeconds),
                        )
                ) {
                    is Outcome.Success ->
                        if (_state.value.selectedChannelId == channelId) {
                            _state.update {
                                it.copy(
                                    actionInProgress = false,
                                    tasks = listOf(result.value) + it.tasks,
                                    draftTaskTitle = "",
                                    draftTaskInstructions = "",
                                    successMessage = "Tarea publicada para la clase.",
                                )
                            }
                        }
                    is Outcome.Failure ->
                        _state.update {
                            it.copy(
                                actionInProgress = false,
                                errorMessage = result.error.userMessage,
                            )
                        }
                }
            }
        }

        fun loadTaskSubmissions(taskId: String) {
            val snapshot = _state.value
            val channelId = snapshot.selectedChannelId ?: return
            if (taskId in snapshot.submissionsLoading || taskId in snapshot.submissions) return
            if (snapshot.tasks.none { it.id == taskId && it.channelId == channelId }) return
            _state.update {
                it.copy(submissionsLoading = it.submissionsLoading + taskId, errorMessage = null)
            }
            viewModelScope.launch {
                when (val result = repository.submissions(channelId, taskId)) {
                    is Outcome.Success ->
                        if (_state.value.selectedChannelId == channelId) {
                            _state.update {
                                it.copy(
                                    submissions = it.submissions + (taskId to result.value),
                                    submissionsLoading = it.submissionsLoading - taskId,
                                )
                            }
                        }
                    is Outcome.Failure ->
                        if (_state.value.selectedChannelId == channelId) {
                            _state.update {
                                it.copy(
                                    submissionsLoading = it.submissionsLoading - taskId,
                                    errorMessage = result.error.userMessage,
                                )
                            }
                        }
                }
            }
        }

        fun submitTask(
            taskId: String,
            comment: String,
            uploads: List<ChannelTaskUpload>,
        ) {
            val snapshot = _state.value
            val channelId = snapshot.selectedChannelId ?: return
            val selected = snapshot.channels.firstOrNull { it.id == channelId }
            val task = snapshot.tasks.firstOrNull { it.id == taskId && it.channelId == channelId } ?: return
            if (selected?.canPublish != false || task.closed || snapshot.actionInProgress || snapshot.loading) return
            val existing = snapshot.submissions[taskId]?.singleOrNull()
            if (existing != null && existing.status != TaskStatus.RETURNED) {
                _state.update { it.copy(errorMessage = "La entrega ya fue registrada y está en revisión.") }
                return
            }
            val submissionId = existing?.id ?: UUID.randomUUID().toString()
            val requestKey = UUID.randomUUID().toString()
            _state.update { it.copy(actionInProgress = true, errorMessage = null, successMessage = null) }
            viewModelScope.launch {
                val fileIds = mutableListOf<String>()
                uploads.forEach { upload ->
                    val reservation =
                        repository.reserveSubmissionFile(
                            channelId,
                            taskId,
                            submissionId,
                            CreateChannelFileRequest(upload.fileName, upload.contentType, upload.bytes.size.toLong()),
                        )
                    when (reservation) {
                        is Outcome.Success -> {
                            when (
                                val uploaded =
                                    repository.uploadSubmissionFile(
                                        channelId,
                                        taskId,
                                        submissionId,
                                        reservation.value.fileId,
                                        upload.contentType,
                                        upload.bytes,
                                    )
                            ) {
                                is Outcome.Success -> fileIds += reservation.value.fileId
                                is Outcome.Failure -> {
                                    finishTaskAction(uploaded.error.userMessage)
                                    return@launch
                                }
                            }
                        }
                        is Outcome.Failure -> {
                            finishTaskAction(reservation.error.userMessage)
                            return@launch
                        }
                    }
                }
                when (
                    val result =
                        repository.submitTask(
                            channelId = channelId,
                            taskId = taskId,
                            request =
                                SubmitTaskRequest(
                                    comment = comment.trim().ifBlank { null },
                                    fileIds = (existing?.fileIds.orEmpty() + fileIds).distinct(),
                                    idempotencyKey = requestKey,
                                    expectedVersion = existing?.version,
                                    submissionId = submissionId,
                                ),
                        )
                ) {
                    is Outcome.Success ->
                        if (_state.value.selectedChannelId == channelId) {
                            _state.update {
                                it.copy(
                                    actionInProgress = false,
                                    submissions = it.submissions + (taskId to listOf(result.value)),
                                    successMessage = "Entrega registrada correctamente.",
                                )
                            }
                        }
                    is Outcome.Failure ->
                        finishTaskAction(result.error.userMessage)
                }
            }
        }

        fun uploadTaskFiles(
            taskId: String,
            uploads: List<ChannelTaskUpload>,
        ) {
            val snapshot = _state.value
            val channelId = snapshot.selectedChannelId ?: return
            val selected = snapshot.channels.firstOrNull { it.id == channelId }
            val task = snapshot.tasks.firstOrNull { it.id == taskId && it.channelId == channelId } ?: return
            if (selected?.canPublish != true || task.closed || snapshot.actionInProgress || uploads.isEmpty()) return
            _state.update { it.copy(actionInProgress = true, errorMessage = null, successMessage = null) }
            viewModelScope.launch {
                uploads.forEach { upload ->
                    val reservation =
                        repository.reserveTaskFile(
                            channelId,
                            taskId,
                            CreateChannelFileRequest(upload.fileName, upload.contentType, upload.bytes.size.toLong()),
                        )
                    when (reservation) {
                        is Outcome.Success ->
                            when (
                                val uploaded =
                                    repository.uploadTaskFile(
                                        channelId,
                                        taskId,
                                        reservation.value.fileId,
                                        upload.contentType,
                                        upload.bytes,
                                    )
                            ) {
                                is Outcome.Success -> Unit
                                is Outcome.Failure -> {
                                    finishTaskAction(uploaded.error.userMessage)
                                    return@launch
                                }
                            }
                        is Outcome.Failure -> {
                            finishTaskAction(reservation.error.userMessage)
                            return@launch
                        }
                    }
                }
                if (_state.value.selectedChannelId == channelId) {
                    val pluralSuffix = if (uploads.size == 1) "" else "s"
                    _state.update {
                        it.copy(
                            actionInProgress = false,
                            successMessage =
                                "Archivo$pluralSuffix agregado$pluralSuffix a la tarea.",
                        )
                    }
                    loadTasks(channelId)
                }
            }
        }

        fun reviewSubmission(
            taskId: String,
            submissionId: String,
            status: TaskStatus,
            feedback: String?,
            score: Double? = null,
        ) {
            val snapshot = _state.value
            val channelId = snapshot.selectedChannelId ?: return
            val selected = snapshot.channels.firstOrNull { it.id == channelId }
            val submission = snapshot.submissions[taskId]?.singleOrNull { it.id == submissionId } ?: return
            if (selected?.canPublish != true || submission.status != TaskStatus.SUBMITTED || snapshot.actionInProgress) return
            _state.update { it.copy(actionInProgress = true, errorMessage = null, successMessage = null) }
            viewModelScope.launch {
                when (
                    val result =
                        repository.reviewSubmission(
                            channelId,
                            taskId,
                            submissionId,
                            ReviewSubmissionRequest(status, feedback, submission.version, score),
                        )
                ) {
                    is Outcome.Success ->
                        if (_state.value.selectedChannelId == channelId) {
                            _state.update {
                                it.copy(
                                    actionInProgress = false,
                                    submissions =
                                        it.submissions +
                                            (
                                                taskId to
                                                    it.submissions[taskId].orEmpty().map { current ->
                                                        if (current.id == result.value.id) result.value else current
                                                    }
                                            ),
                                    successMessage = "La entrega fue revisada.",
                                )
                            }
                        }
                    is Outcome.Failure -> finishTaskAction(result.error.userMessage)
                }
            }
        }

        fun setTaskClosed(
            taskId: String,
            closed: Boolean,
        ) {
            val snapshot = _state.value
            val channelId = snapshot.selectedChannelId ?: return
            val selected = snapshot.channels.firstOrNull { it.id == channelId }
            val task = snapshot.tasks.firstOrNull { it.id == taskId && it.channelId == channelId } ?: return
            if (selected?.canPublish != true || snapshot.actionInProgress) return
            _state.update { it.copy(actionInProgress = true, errorMessage = null, successMessage = null) }
            viewModelScope.launch {
                when (
                    val result =
                        repository.updateTask(
                            channelId,
                            taskId,
                            UpdateChannelTaskRequest(closed = closed, expectedVersion = task.version),
                        )
                ) {
                    is Outcome.Success ->
                        if (_state.value.selectedChannelId == channelId) {
                            _state.update {
                                it.copy(
                                    actionInProgress = false,
                                    tasks = it.tasks.map { current -> if (current.id == taskId) result.value else current },
                                    successMessage = if (closed) "Tarea cerrada." else "Tarea reabierta.",
                                )
                            }
                        }
                    is Outcome.Failure -> finishTaskAction(result.error.userMessage)
                }
            }
        }

        fun downloadTaskFile(
            taskId: String,
            file: ChannelPrivateFileMetadata,
        ) {
            val channelId = _state.value.selectedChannelId ?: return
            if (_state.value.actionInProgress) return
            _state.update { it.copy(actionInProgress = true, errorMessage = null, successMessage = null) }
            viewModelScope.launch {
                when (val result = repository.downloadFile(channelId, taskId, file)) {
                    is Outcome.Success ->
                        if (_state.value.selectedChannelId == channelId) {
                            _state.update {
                                it.copy(
                                    actionInProgress = false,
                                    downloadReady =
                                        ChannelTaskDownload(
                                            requestId = UUID.randomUUID().toString(),
                                            fileName = file.fileName,
                                            contentType = file.contentType,
                                            bytes = result.value,
                                        ),
                                )
                            }
                        }
                    is Outcome.Failure -> finishTaskAction(result.error.userMessage)
                }
            }
        }

        fun finishFileDownload(success: Boolean) {
            _state.update {
                it.copy(
                    downloadReady = null,
                    successMessage = if (success) "Archivo guardado." else it.successMessage,
                    errorMessage = if (success) null else "No se pudo guardar el archivo seleccionado.",
                )
            }
        }

        fun cancelFileDownload() {
            _state.update { it.copy(downloadReady = null) }
        }

        fun reportLocalFileError(message: String) {
            _state.update { it.copy(errorMessage = message, successMessage = null) }
        }

        private fun finishTaskAction(error: String) {
            _state.update { it.copy(actionInProgress = false, errorMessage = error) }
        }

        private fun loadPosts(channelId: String) {
            viewModelScope.launch {
                if (_state.value.selectedChannelId != channelId) return@launch
                _state.update { it.copy(loading = true, errorMessage = null) }
                when (val result = repository.posts(channelId)) {
                    is Outcome.Success ->
                        if (_state.value.selectedChannelId == channelId) {
                            _state.update {
                                it.copy(
                                    loading = false,
                                    posts = result.value,
                                    errorMessage = null,
                                )
                            }
                            loadTasks(channelId)
                        }
                    is Outcome.Failure ->
                        if (_state.value.selectedChannelId == channelId) {
                            _state.update {
                                it.copy(
                                    loading = false,
                                    posts = emptyList(),
                                    errorMessage = result.error.userMessage,
                                )
                            }
                        }
                }
            }
        }

        private fun loadTasks(channelId: String) {
            viewModelScope.launch {
                _state.update { it.copy(tasksLoading = true) }
                when (val result = repository.tasks(channelId)) {
                    is Outcome.Success ->
                        if (_state.value.selectedChannelId == channelId) {
                            _state.update {
                                it.copy(
                                    tasksLoading = false,
                                    tasks = result.value,
                                    errorMessage = null,
                                )
                            }
                        }
                    is Outcome.Failure ->
                        if (_state.value.selectedChannelId == channelId) {
                            _state.update {
                                it.copy(
                                    tasksLoading = false,
                                    tasks = emptyList(),
                                    errorMessage = result.error.userMessage,
                                )
                            }
                        }
                }
            }
        }

        private fun attachmentType(url: String): ChannelAttachmentType {
            val clean = url.substringBefore('?').lowercase()
            return when {
                clean.endsWith(".pdf") -> ChannelAttachmentType.DOCUMENT
                clean.endsWith(".png") ||
                    clean.endsWith(".jpg") ||
                    clean.endsWith(".jpeg") ||
                    clean.endsWith(".webp") -> ChannelAttachmentType.IMAGE
                else -> ChannelAttachmentType.LINK
            }
        }

        private fun defaultResourceLabel(url: String): String =
            url.substringBefore('?').substringAfterLast('/').takeIf(String::isNotBlank)
                ?: "Recurso compartido"

        private companion object {
            const val MAX_DRAFT_BODY = 8_000
            const val MAX_RESOURCE_LABEL = 160
            const val MAX_RESOURCE_URL = 2_048
        }
    }
