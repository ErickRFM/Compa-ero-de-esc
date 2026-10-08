package org.companerodeescuela.feature.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Forum
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
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
    var selectedTab by remember(state.selectedChannelId) { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize()) {
        V8CampusBackdrop(Modifier.matchParentSize())
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = CompaneroSpacing.page),
            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = CompaneroSpacing.sm),
        ) {
            item { V8BrandHeader() }
            item { ChannelHeader(state.loading, viewModel::refreshChannels) }
            if (state.channels.isNotEmpty()) item {
                ChannelSelector(state.channels, state.selectedChannelId, viewModel::selectChannel)
            }
            state.successMessage?.let { message -> item {
                StatusNotice(title = "Listo", message = message, tone = NoticeTone.SUCCESS)
            } }
            state.errorMessage?.let { message -> item {
                StatusNotice(title = "No pudimos completar la acción", message = message, tone = NoticeTone.ERROR)
            } }
            if (selected == null) item {
                if (state.loading) CircularProgressIndicator()
                StatusNotice(
                    title = if (state.loading) "Cargando tus canales…" else "Todavía no hay canales",
                    message = "Cuando tengas materias asignadas, sus avisos aparecerán aquí.",
                )
            } else {
                item { ChannelIdentity(selected) }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf("Avisos", "Chat").forEachIndexed { index, label ->
                            V8ChannelTab(label, selectedTab == index, { selectedTab = index }, Modifier.weight(1f))
                        }
                    }
                }
                if (state.posts.isEmpty()) item {
                    StatusNotice(
                        title = if (state.loading) "Cargando publicaciones…" else "Sin publicaciones",
                        message = if (selected.canPublish) "Comparte el primer aviso o recurso con este grupo."
                            else "Tu docente todavía no ha publicado información para esta materia.",
                    )
                }
                items(state.posts, key = ChannelPost::id) { post ->
                    ChannelPostCard(post, !selected.canPublish && selectedTab == 1, state.actionInProgress, viewModel::acknowledge)
                }
                item {
                    if (selected.canPublish) TeacherComposer(
                        state.draftBody, state.draftResourceLabel, state.draftResourceUrl, state.actionInProgress,
                        viewModel::updateDraftBody, viewModel::updateDraftResourceLabel,
                        viewModel::updateDraftResourceUrl, viewModel::publish,
                    ) else StudentChannelFooter()
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
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = V8RedColors.TextPrimary,
            )
            Text(
                text = "Avisos y recursos de tus materias",
                style = MaterialTheme.typography.bodySmall,
                color = V8RedColors.TextSecondary,
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
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = channel.subjectName,
            style = MaterialTheme.typography.headlineLarge,
            color = V8RedColors.TextPrimary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = channel.groupName + " · " + channel.teacherDisplayName,
            style = MaterialTheme.typography.bodyMedium,
            color = V8RedColors.TextSecondary,
        )
    }
}

@Composable
private fun V8ChannelTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val activeColor = if (selected) V8RedColors.Crimson else V8RedColors.Outline
    Box(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(
                if (selected) Color(0xFF341118) else V8RedColors.Surface.copy(alpha = 0.96f),
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = activeColor.copy(alpha = if (selected) 1f else 0.75f),
                shape = shape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (label == "Avisos") Icons.Filled.Campaign else Icons.Filled.Forum,
                contentDescription = null,
                tint = if (selected) V8RedColors.Crimson else V8RedColors.TextSecondary,
            )
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) V8RedColors.TextPrimary else V8RedColors.TextSecondary,
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
            modifier = Modifier,
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
                        color = V8RedColors.TextPrimary,
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
                        tint = V8RedColors.Crimson,
                    )
                }
            }

            post.title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = V8RedColors.TextPrimary,
                )
            }
            post.body?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = V8RedColors.TextSecondary,
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
                    color = V8RedColors.TextSecondary,
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
    V8GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
        ) {
            Text(
                text = "Publicar para el grupo",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = V8RedColors.TextPrimary,
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
