@file:Suppress("ktlint:standard:function-naming")

package org.companerodeescuela.feature.channel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.designsystem.v8.V8GlassAssistChip
import org.companerodeescuela.core.designsystem.v8.V8GlassCard
import org.companerodeescuela.core.designsystem.v8.V8GlassFilterChip
import org.companerodeescuela.core.designsystem.v8.V8MatteBackdrop
import org.companerodeescuela.core.designsystem.v8.V8ScreenHeader
import org.companerodeescuela.core.designsystem.v8.v8GlassSurface
import org.companerodeescuela.core.ui.component.NoticeTone
import org.companerodeescuela.core.ui.component.StatusNotice
import org.companerodeescuela.shared.contracts.ChannelPost
import org.companerodeescuela.shared.contracts.ChannelPostType
import org.companerodeescuela.shared.contracts.ChannelPresetResponse
import org.companerodeescuela.shared.contracts.ClassChannelSummary
import org.companerodeescuela.shared.contracts.TeacherClassContext

@Composable
fun ChannelScreen(
    requestedClassroom: TeacherClassContext? = null,
    isAdministrativeSession: Boolean = false,
    viewModel: ChannelViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(requestedClassroom) { requestedClassroom?.let(viewModel::requestClassroom) }
    val selected = state.channels.firstOrNull { it.id == state.selectedChannelId }
    var selectedTab by remember(state.selectedChannelId) { mutableIntStateOf(0) }
    var composerOpen by remember(state.selectedChannelId) { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        if (!org.companerodeescuela.core.designsystem.v8.LocalV8GlassEnabled.current) {
            V8MatteBackdrop(Modifier.matchParentSize())
        }
        LazyColumn(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = CompaneroSize.homeContentMaxWidth)
                    .fillMaxSize()
                    .padding(horizontal = CompaneroSpacing.page),
            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(vertical = CompaneroSpacing.sm),
        ) {
            item { V8ScreenHeader { ChannelHeader(state.loading || state.actionInProgress, viewModel::refreshChannels) } }
            if (state.channels.isNotEmpty()) {
                item {
                    ChannelSelector(state.channels, state.selectedChannelId, viewModel::selectChannel)
                }
            }
            state.successMessage?.let { message ->
                item {
                    StatusNotice(title = "Listo", message = message, tone = NoticeTone.SUCCESS)
                }
            }
            state.errorMessage?.let { message ->
                item {
                    StatusNotice(title = "No pudimos completar la acciÃ³n", message = message, tone = NoticeTone.ERROR)
                }
            }
            if (selected == null) {
                item {
                    if (state.loading) CircularProgressIndicator()
                    StatusNotice(
                        title =
                            if (state.loading) {
                                "Cargando tus canalesâ€¦"
                            } else if (isAdministrativeSession) {
                                "Canal de clase no disponible"
                            } else {
                                "TodavÃ­a no hay canales"
                            },
                        message =
                            if (isAdministrativeSession) {
                                "Los canales de clase estÃ¡n reservados para perfiles acadÃ©micos (docentes y alumnos). Las cuentas administrativas institucionales operan mediante avisos institucionales."
                            } else {
                                "Cuando tengas materias asignadas, sus avisos aparecerÃ¡n aquÃ­."
                            },
                    )
                }
            } else {
                item { ChannelIdentity(selected) }
                if (selected.canPublish) {
                    item {
                        V8GlassCard(modifier = Modifier.fillMaxWidth(), emphasized = true) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "Comunica con tu grupo",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Text(
                                    "Publica avisos y materiales. Los estudiantes responden con opciones predeterminadas.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Button(onClick = { composerOpen = !composerOpen }, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (composerOpen) "Cerrar redacciÃ³n" else "Redactar aviso")
                                }
                                if (composerOpen) {
                                    TeacherComposer(
                                        state.draftBody,
                                        state.draftResourceLabel,
                                        state.draftResourceUrl,
                                        state.actionInProgress,
                                        viewModel::updateDraftBody,
                                        viewModel::updateDraftResourceLabel,
                                        viewModel::updateDraftResourceUrl,
                                        viewModel::publish,
                                    )
                                }
                            }
                        }
                    }
                }
                if (!selected.canPublish) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf("Publicaciones", "Recursos").forEachIndexed { index, label ->
                                V8ChannelTab(label, selectedTab == index, { selectedTab = index }, Modifier.weight(1f))
                            }
                        }
                    }
                }
                // El canal es unidireccional: Recursos filtra adjuntos, no simula un chat libre.
                val visiblePosts =
                    channelPostsForTab(
                        posts = state.posts,
                        tab = if (!selected.canPublish && selectedTab == 1) ChannelFeedTab.RESOURCES else ChannelFeedTab.POSTS,
                    )
                if (visiblePosts.isEmpty()) {
                    item {
                        StatusNotice(
                            title =
                                when {
                                    state.loading -> "Cargando publicacionesâ€¦"
                                    state.posts.isNotEmpty() -> "Sin recursos compartidos"
                                    else -> "Sin publicaciones"
                                },
                            message =
                                when {
                                    state.loading -> "Consultando informaciÃ³n de esta clase."
                                    state.posts.isNotEmpty() -> "No hay documentos ni enlaces en las publicaciones de este canal."
                                    selected.canPublish -> "Comparte el primer aviso o recurso con este grupo."
                                    else -> "Tu docente todavÃ­a no ha publicado informaciÃ³n para esta materia."
                                },
                        )
                    }
                }
                items(visiblePosts, key = ChannelPost::id) { post ->
                    ChannelPostCard(post, !selected.canPublish, state.actionInProgress, viewModel::acknowledge)
                }
                item {
                    if (!selected.canPublish) StudentChannelFooter()
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
                text = "Canal",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Avisos y recursos de tus materias",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = onRefresh,
            enabled = !loading,
        ) {
            Text(if (loading) "â€¦" else "Actualizar")
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
            V8GlassFilterChip(
                selected = channel.id == selectedId,
                onClick = { onSelect(channel.id) },
                label = {
                    Text(
                        text = channel.groupName.ifBlank { channel.subjectName },
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
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = channel.groupName + " Â· " + channel.teacherDisplayName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    Box(
        modifier =
            modifier
                .heightIn(min = 52.dp)
                .v8GlassSurface(cornerRadius = 16.dp, emphasized = selected, elevation = 0.dp)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (label == "Recursos") Icons.Filled.AttachFile else Icons.Filled.Campaign,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
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
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = postTypeLabel(post.type),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (post.pinned) {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = "PublicaciÃ³n fijada",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            post.title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            post.body?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            post.attachments.forEach { attachment ->
                V8GlassAssistChip(
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
                    text = "Respuesta rÃ¡pida",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
                ) {
                    items(post.allowedResponses.toList(), key = { it.name }) { response ->
                        V8GlassAssistChip(
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
                color = MaterialTheme.colorScheme.onSurface,
            )
            OutlinedTextField(
                value = body,
                onValueChange = onBodyChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                enabled = !busy,
                label = { Text("Aviso o informaciÃ³n") },
                placeholder = { Text("Escribe lo que verÃ¡ toda la clase") },
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
                Text(if (busy) "Publicandoâ€¦" else "Publicar")
            }
        }
    }
}

@Composable
private fun StudentChannelFooter() {
    V8GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(0.dp),
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
                    text = "Puedes leer y usar respuestas rÃ¡pidas, pero no enviar mensajes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun postTypeLabel(type: ChannelPostType): String =
    when (type) {
        ChannelPostType.ANNOUNCEMENT -> "Aviso"
        ChannelPostType.MATERIAL -> "Material"
        ChannelPostType.REMINDER -> "Recordatorio"
        ChannelPostType.ROOM_CHANGE -> "Cambio de aula"
        ChannelPostType.SCHEDULE_CHANGE -> "Cambio de horario"
        ChannelPostType.CLASS_CANCELLED -> "Clase cancelada"
        ChannelPostType.EVENT -> "Evento"
        ChannelPostType.MEETING -> "ReuniÃ³n"
    }

private fun responseLabel(response: ChannelPresetResponse): String =
    when (response) {
        ChannelPresetResponse.ACKNOWLEDGED -> "Entendido"
        ChannelPresetResponse.CONFIRMED -> "Confirmado"
        ChannelPresetResponse.WILL_ATTEND -> "AsistirÃ©"
        ChannelPresetResponse.CANNOT_ATTEND -> "No podrÃ© asistir"
        ChannelPresetResponse.NEED_CLARIFICATION -> "Necesito aclaraciÃ³n"
    }
