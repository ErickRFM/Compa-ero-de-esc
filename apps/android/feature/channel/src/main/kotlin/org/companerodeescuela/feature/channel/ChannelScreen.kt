package org.companerodeescuela.feature.channel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.companerodeescuela.core.designsystem.theme.CompaneroElevation
import org.companerodeescuela.core.designsystem.v8.V8CampusBackdrop
import org.companerodeescuela.core.designsystem.v8.V8BrandHeader
import org.companerodeescuela.core.designsystem.v8.V8GlassCard
import org.companerodeescuela.core.designsystem.v8.V8RedColors
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.ui.component.NoticeTone
import org.companerodeescuela.core.ui.component.StatusNotice
import org.companerodeescuela.shared.contracts.ChannelPost
import org.companerodeescuela.shared.contracts.ChannelPostType
import org.companerodeescuela.shared.contracts.ChannelPresetResponse
import org.companerodeescuela.shared.contracts.ClassChannelSummary

@Composable
fun ChannelScreen(
    viewModel: ChannelViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selected = state.channels.firstOrNull { it.id == state.selectedChannelId }

    Box(modifier = Modifier.fillMaxSize()) {
        V8CampusBackdrop(modifier = Modifier.matchParentSize())
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = CompaneroSpacing.page,
                vertical = CompaneroSpacing.sm,
            ),
        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
    ) {
        V8BrandHeader()
        ChannelHeader(
            loading = state.loading,
            onRefresh = viewModel::refreshChannels,
        )

        if (state.channels.isNotEmpty()) {
            ChannelSelector(
                channels = state.channels,
                selectedId = state.selectedChannelId,
                onSelect = viewModel::selectChannel,
            )
        }

        state.successMessage?.let {
            StatusNotice(
                title = "Listo",
                message = it,
                tone = NoticeTone.SUCCESS,
            )
        }
        state.errorMessage?.let {
            StatusNotice(
                title = "No pudimos completar la acción",
                message = it,
                tone = NoticeTone.ERROR,
            )
        }

        when {
            state.loading && selected == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Cargando tus canales…",
                        modifier = Modifier.padding(top = CompaneroSpacing.xs),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            selected == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    StatusNotice(
                        title = "Todavía no hay canales",
                        message = "Cuando tengas materias asignadas, sus avisos aparecerán aquí.",
                    )
                }
            }

            else -> {
                ChannelIdentity(selected)
                ChannelFeed(
                    posts = state.posts,
                    canPublish = selected.canPublish,
                    busy = state.actionInProgress,
                    loading = state.loading,
                    onAcknowledge = viewModel::acknowledge,
                    onRefresh = viewModel::refreshPosts,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )

                if (selected.canPublish) {
                    TeacherComposer(
                        body = state.draftBody,
                        resourceLabel = state.draftResourceLabel,
                        resourceUrl = state.draftResourceUrl,
                        busy = state.actionInProgress,
                        onBodyChange = viewModel::updateDraftBody,
                        onResourceLabelChange = viewModel::updateDraftResourceLabel,
                        onResourceUrlChange = viewModel::updateDraftResourceUrl,
                        onPublish = viewModel::publish,
                    )
                } else {
                    StudentChannelFooter()
                }
            }
        }
    }
    }
}

@Composable
private fun ChannelHeader(
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Canal de clase",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Avisos y recursos de tus materias",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = onRefresh,
            enabled = !loading,
        ) {
            Text(if (loading) "…" else "Actualizar")
        }
    }
}

@Composable
private fun ChannelSelector(
    channels: List<ClassChannelSummary>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
    ) {
        items(channels, key = ClassChannelSummary::id) { channel ->
            FilterChip(
                selected = channel.id == selectedId,
                onClick = { onSelect(channel.id) },
                label = {
                    Text(
                        text = channel.subjectCode.ifBlank { channel.subjectName },
                        maxLines = 1,
                    )
                },
            )
        }
    }
}

@Composable
private fun ChannelIdentity(channel: ClassChannelSummary) {
    V8GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(CompaneroSpacing.card),
            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xxs),
        ) {
            Text(
                text = channel.subjectName,
                style = MaterialTheme.typography.headlineSmall,
                color = V8RedColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = channel.groupName + " · " + channel.teacherDisplayName,
                style = MaterialTheme.typography.bodyMedium,
                color = V8RedColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun ChannelFeed(
    posts: List<ChannelPost>,
    canPublish: Boolean,
    busy: Boolean,
    loading: Boolean,
    onAcknowledge: (ChannelPost, ChannelPresetResponse) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (posts.isEmpty()) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.Center,
        ) {
            StatusNotice(
                title = if (loading) "Cargando publicaciones…" else "Sin publicaciones",
                message = if (canPublish) {
                    "Comparte el primer aviso o recurso con este grupo."
                } else {
                    "Tu docente todavía no ha publicado información para esta materia."
                },
            )
            if (!loading) {
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.padding(top = CompaneroSpacing.xs),
                ) {
                    Text("Actualizar")
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
    ) {
        items(posts, key = ChannelPost::id) { post ->
            ChannelPostCard(
                post = post,
                showStudentActions = !canPublish,
                busy = busy,
                onAcknowledge = onAcknowledge,
            )
        }
    }
}

@Composable
private fun ChannelPostCard(
    post: ChannelPost,
    showStudentActions: Boolean,
    busy: Boolean,
    onAcknowledge: (ChannelPost, ChannelPresetResponse) -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    V8GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(CompaneroSpacing.card),
            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = post.authorDisplayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = postTypeLabel(post.type),
                        style = MaterialTheme.typography.labelMedium,
                        color = V8RedColors.Crimson,
                    )
                }
                if (post.pinned) {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = "Publicación fijada",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            post.title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            post.body?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            post.attachments.forEach { attachment ->
                AssistChip(
                    onClick = { uriHandler.openUri(attachment.url) },
                    label = { Text(attachment.label) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.AttachFile,
                            contentDescription = null,
                        )
                    },
                )
            }

            if (showStudentActions && post.allowedResponses.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = "Respuesta rápida",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
                ) {
                    items(post.allowedResponses.toList(), key = { it.name }) { response ->
                        AssistChip(
                            onClick = { onAcknowledge(post, response) },
                            enabled = !busy,
                            label = { Text(responseLabel(response)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TeacherComposer(
    body: String,
    resourceLabel: String,
    resourceUrl: String,
    busy: Boolean,
    onBodyChange: (String) -> Unit,
    onResourceLabelChange: (String) -> Unit,
    onResourceUrlChange: (String) -> Unit,
    onPublish: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = CompaneroElevation.card,
        shadowElevation = CompaneroElevation.raised,
    ) {
        Column(
            modifier = Modifier.padding(CompaneroSpacing.card),
            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
        ) {
            Text(
                text = "Publicar para el grupo",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = body,
                onValueChange = onBodyChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                enabled = !busy,
                label = { Text("Aviso o información") },
                placeholder = { Text("Escribe lo que verá toda la clase") },
            )
            OutlinedTextField(
                value = resourceLabel,
                onValueChange = onResourceLabelChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !busy,
                label = { Text("Nombre del recurso (opcional)") },
            )
            OutlinedTextField(
                value = resourceUrl,
                onValueChange = onResourceUrlChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !busy,
                label = { Text("Enlace HTTPS del recurso (opcional)") },
            )
            Button(
                onClick = onPublish,
                enabled = !busy && (body.isNotBlank() || resourceUrl.isNotBlank()),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (busy) "Publicando…" else "Publicar")
            }
        }
    }
}

@Composable
private fun StudentChannelFooter() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(CompaneroSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column {
                Text(
                    text = "Canal administrado por tu docente",
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = "Puedes leer y usar respuestas rápidas, pero no enviar mensajes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun postTypeLabel(type: ChannelPostType): String = when (type) {
    ChannelPostType.ANNOUNCEMENT -> "Aviso"
    ChannelPostType.MATERIAL -> "Material"
    ChannelPostType.REMINDER -> "Recordatorio"
    ChannelPostType.ROOM_CHANGE -> "Cambio de aula"
    ChannelPostType.SCHEDULE_CHANGE -> "Cambio de horario"
    ChannelPostType.CLASS_CANCELLED -> "Clase cancelada"
    ChannelPostType.EVENT -> "Evento"
    ChannelPostType.MEETING -> "Reunión"
}

private fun responseLabel(response: ChannelPresetResponse): String = when (response) {
    ChannelPresetResponse.ACKNOWLEDGED -> "Entendido"
    ChannelPresetResponse.CONFIRMED -> "Confirmado"
    ChannelPresetResponse.WILL_ATTEND -> "Asistiré"
    ChannelPresetResponse.CANNOT_ATTEND -> "No podré asistir"
    ChannelPresetResponse.NEED_CLARIFICATION -> "Necesito aclaración"
}
