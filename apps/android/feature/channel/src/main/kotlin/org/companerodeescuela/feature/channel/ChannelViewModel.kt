package org.companerodeescuela.feature.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
import org.companerodeescuela.shared.contracts.ClassChannelSummary
import org.companerodeescuela.shared.contracts.CreateChannelPostRequest

data class ChannelUiState(
    val loading: Boolean = true,
    val actionInProgress: Boolean = false,
    val channels: List<ClassChannelSummary> = emptyList(),
    val selectedChannelId: String? = null,
    val posts: List<ChannelPost> = emptyList(),
    val draftBody: String = "",
    val draftResourceLabel: String = "",
    val draftResourceUrl: String = "",
    val successMessage: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class ChannelViewModel @Inject constructor(
    private val repository: ChannelRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ChannelUiState())
    val state: StateFlow<ChannelUiState> = _state.asStateFlow()

    init { refreshChannels() }

    fun refreshChannels() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, errorMessage = null) }
            when (val result = repository.channels()) {
                is Outcome.Success -> {
                    val previous = _state.value.selectedChannelId
                    val selected = result.value.firstOrNull { it.id == previous }
                        ?: result.value.firstOrNull()
                    _state.update {
                        it.copy(
                            loading = false,
                            channels = result.value,
                            selectedChannelId = selected?.id,
                            posts = if (selected == null) emptyList() else it.posts,
                            errorMessage = null,
                        )
                    }
                    selected?.let { loadPosts(it.id) }
                }
                is Outcome.Failure -> _state.update {
                    it.copy(
                        loading = false,
                        channels = emptyList(),
                        selectedChannelId = null,
                        posts = emptyList(),
                        errorMessage = result.error.userMessage,
                    )
                }
            }
        }
    }

    fun selectChannel(channelId: String) {
        if (_state.value.selectedChannelId == channelId) return
        _state.update {
            it.copy(
                selectedChannelId = channelId,
                posts = emptyList(),
                successMessage = null,
                errorMessage = null,
            )
        }
        loadPosts(channelId)
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
        if (!selected.canPublish || snapshot.actionInProgress) return

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

        val attachments = if (resourceUrl.isBlank()) {
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

        viewModelScope.launch {
            _state.update {
                it.copy(
                    actionInProgress = true,
                    errorMessage = null,
                    successMessage = null,
                )
            }
            when (
                val result = repository.publish(
                    channelId = channelId,
                    request = CreateChannelPostRequest(
                        body = body.ifBlank { null },
                        attachments = attachments,
                        allowedResponses = setOf(ChannelPresetResponse.ACKNOWLEDGED),
                    ),
                )
            ) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        actionInProgress = false,
                        posts = listOf(result.value) + it.posts.filterNot { post ->
                            post.id == result.value.id
                        },
                        draftBody = "",
                        draftResourceLabel = "",
                        draftResourceUrl = "",
                        successMessage = "Publicado para el grupo.",
                        errorMessage = null,
                    )
                }
                is Outcome.Failure -> _state.update {
                    it.copy(
                        actionInProgress = false,
                        errorMessage = result.error.userMessage,
                    )
                }
            }
        }
    }

    fun acknowledge(post: ChannelPost, response: ChannelPresetResponse) {
        val channelId = _state.value.selectedChannelId ?: return
        if (response !in post.allowedResponses || _state.value.actionInProgress) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    actionInProgress = true,
                    errorMessage = null,
                    successMessage = null,
                )
            }
            when (
                val result = repository.acknowledge(
                    channelId = channelId,
                    postId = post.id,
                    request = ChannelAcknowledgementRequest(response),
                )
            ) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        actionInProgress = false,
                        successMessage = "Respuesta registrada.",
                    )
                }
                is Outcome.Failure -> _state.update {
                    it.copy(
                        actionInProgress = false,
                        errorMessage = result.error.userMessage,
                    )
                }
            }
        }
    }

    private fun loadPosts(channelId: String) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, errorMessage = null) }
            when (val result = repository.posts(channelId)) {
                is Outcome.Success -> if (_state.value.selectedChannelId == channelId) {
                    _state.update {
                        it.copy(
                            loading = false,
                            posts = result.value,
                            errorMessage = null,
                        )
                    }
                }
                is Outcome.Failure -> if (_state.value.selectedChannelId == channelId) {
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
