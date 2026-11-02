@file:Suppress("ktlint:standard:function-naming")

package org.companerodeescuela.feature.channel

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.designsystem.v8.V8GlassAssistChip
import org.companerodeescuela.core.designsystem.v8.V8GlassCard
import org.companerodeescuela.core.designsystem.v8.V8GlassFilterChip
import org.companerodeescuela.core.designsystem.v8.V8MatteBackdrop
import org.companerodeescuela.core.designsystem.v8.V8RedPrimaryButton
import org.companerodeescuela.core.designsystem.v8.V8ScreenHeader
import org.companerodeescuela.core.designsystem.v8.v8GlassSurface
import org.companerodeescuela.core.ui.component.NoticeTone
import org.companerodeescuela.core.ui.component.StatusNotice
import org.companerodeescuela.shared.contracts.ChannelPost
import org.companerodeescuela.shared.contracts.ChannelPostType
import org.companerodeescuela.shared.contracts.ChannelPresetResponse
import org.companerodeescuela.shared.contracts.ChannelPrivateFileMetadata
import org.companerodeescuela.shared.contracts.ChannelTask
import org.companerodeescuela.shared.contracts.ClassChannelSummary
import org.companerodeescuela.shared.contracts.TaskStatus
import org.companerodeescuela.shared.contracts.TaskSubmission
import org.companerodeescuela.shared.contracts.TeacherClassContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

internal fun shouldConsumeChannelTabRequest(requestedTab: Int, channelReady: Boolean): Boolean =
    requestedTab in 0..2 && channelReady

private data class PendingChannelTaskUpload(
    val taskId: String,
    val comment: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    requestedClassroom: TeacherClassContext? = null,
    isAdministrativeSession: Boolean = false,
    requestedTab: Int = -1,
    onConsumeRequestedTab: () -> Unit = {},
    viewModel: ChannelViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(requestedClassroom) { requestedClassroom?.let(viewModel::requestClassroom) }
    val selected = state.channels.firstOrNull { it.id == state.selectedChannelId }
    var selectedTab by remember(state.selectedChannelId) { mutableIntStateOf(0) }
    var composerOpen by remember(state.selectedChannelId) { mutableStateOf(false) }
    var taskComposerOpen by remember(state.selectedChannelId) { mutableStateOf(false) }
    var selectedTask by remember(state.selectedChannelId) { mutableStateOf<ChannelTask?>(null) }
    var pendingUpload by remember(state.selectedChannelId) { mutableStateOf<PendingChannelTaskUpload?>(null) }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val acceptedTaskFileTypes =
        arrayOf(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "text/plain",
            "text/csv",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        )
    val taskFilePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            val target = pendingUpload
            pendingUpload = null
            if (target != null && uris.isNotEmpty()) {
                scope.launch {
                    try {
                        val uploads = readChannelTaskUploads(context.contentResolver, uris)
                        if (target.comment == null) {
                            viewModel.uploadTaskFiles(target.taskId, uploads)
                        } else {
                            viewModel.submitTask(target.taskId, target.comment, uploads)
                        }
                    } catch (error: IOException) {
                        viewModel.reportLocalFileError(error.message ?: "No se pudieron leer los archivos.")
                    } catch (error: SecurityException) {
                        viewModel.reportLocalFileError("No hay permiso para leer uno de los archivos seleccionados.")
                    } catch (error: IllegalArgumentException) {
                        viewModel.reportLocalFileError(error.message ?: "El archivo seleccionado no es válido.")
                    }
                }
            }
        }
    val downloadPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
            val download = state.downloadReady
            if (uri != null && download != null) {
                try {
                    val output =
                        context.contentResolver.openOutputStream(uri)
                            ?: throw IOException("No se pudo abrir el destino seleccionado.")
                    output.use {
                        it.write(download.bytes)
                        it.flush()
                    }
                    viewModel.finishFileDownload(success = true)
                } catch (error: IOException) {
                    viewModel.finishFileDownload(success = false)
                } catch (error: SecurityException) {
                    viewModel.finishFileDownload(success = false)
                }
            } else if (uri == null) {
                viewModel.cancelFileDownload()
            }
        }
    LaunchedEffect(state.downloadReady?.requestId) {
        state.downloadReady?.let { downloadPicker.launch(it.fileName) }
    }
    LaunchedEffect(requestedTab, selected?.id) {
        if (shouldConsumeChannelTabRequest(requestedTab, selected != null)) {
            selectedTab = requestedTab
            onConsumeRequestedTab()
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (!org.companerodeescuela.core.designsystem.v8.LocalV8GlassEnabled.current) {
            V8MatteBackdrop(Modifier.matchParentSize())
        }
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.refreshChannels()
            },
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = CompaneroSize.homeContentMaxWidth)
                    .fillMaxSize(),
        ) {
            LazyColumn(
                modifier =
                    Modifier
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
                        StatusNotice(title = "No pudimos completar la acción", message = message, tone = NoticeTone.ERROR)
                    }
                }
                if (selected == null) {
                    item {
                        if (state.loading) CircularProgressIndicator()
                        StatusNotice(
                            title =
                                if (state.loading) {
                                    "Cargando tus canales…"
                                } else if (isAdministrativeSession) {
                                    "Canal de clase no disponible"
                                } else {
                                    "Todavía no hay canales"
                                },
                            message =
                                if (isAdministrativeSession) {
                                    "Los canales de clase están reservados para perfiles académicos (docentes y alumnos). Las cuentas administrativas institucionales operan mediante avisos institucionales."
                                } else {
                                    "Cuando tengas materias asignadas, sus avisos aparecerán aquí."
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
                                    V8RedPrimaryButton(
                                        text = if (composerOpen) "Cerrar redacción" else "Redactar aviso",
                                        onClick = { composerOpen = !composerOpen },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
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
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Avisos", "Recursos", "Tareas").forEachIndexed { index, label ->
                                V8ChannelTab(label, selectedTab == index, { selectedTab = index }, Modifier.weight(1f))
                            }
                        }
                    }
                    if (selectedTab == 2) {
                        if (selected.canPublish) {
                            item {
                                V8RedPrimaryButton(
                                    text = "Crear tarea",
                                    onClick = { taskComposerOpen = true },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        if (state.tasks.isEmpty()) {
                            item {
                                StatusNotice(
                                    title = if (state.tasksLoading) "Cargando tareas…" else "Sin tareas asignadas",
                                    message =
                                        if (state.tasksLoading) {
                                            "Consultando las tareas guardadas para esta clase."
                                        } else if (selected.canPublish) {
                                            "Las tareas publicadas para este grupo aparecerán aquí."
                                        } else {
                                            "Tu docente todavía no ha publicado tareas para esta materia."
                                        },
                                )
                            }
                        }
                        items(state.tasks, key = ChannelTask::id) { task ->
                            ChannelTaskCard(
                                task,
                                onClick = {
                                    selectedTask = task
                                    viewModel.loadTaskSubmissions(task.id)
                                },
                            )
                        }
                    } else {
                        // El canal es unidireccional: Recursos filtra adjuntos, no simula un chat libre.
                        val visiblePosts =
                            channelPostsForTab(
                                posts = state.posts,
                                tab = if (selectedTab == 1) ChannelFeedTab.RESOURCES else ChannelFeedTab.POSTS,
                            )
                        if (visiblePosts.isEmpty()) {
                            item {
                                StatusNotice(
                                    title =
                                        when {
                                            state.loading -> "Cargando publicaciones…"
                                            selectedTab == 1 && state.posts.isNotEmpty() -> "Sin recursos compartidos"
                                            else -> "Sin publicaciones"
                                        },
                                    message =
                                        when {
                                            state.loading -> "Consultando información de esta clase."
                                            selectedTab == 1 && state.posts.isNotEmpty() ->
                                                "No hay documentos ni enlaces en las publicaciones de este canal."
                                            selected.canPublish -> "Comparte el primer aviso o recurso con este grupo."
                                            else -> "Tu docente todavía no ha publicado información para esta materia."
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
            if (taskComposerOpen) {
                ModalBottomSheet(onDismissRequest = { taskComposerOpen = false }) {
                    TaskComposer(
                        title = state.draftTaskTitle,
                        instructions = state.draftTaskInstructions,
                        busy = state.actionInProgress,
                        onTitleChange = viewModel::updateDraftTaskTitle,
                        onInstructionsChange = viewModel::updateDraftTaskInstructions,
                        onCreate = { dueDate ->
                            viewModel.createTask(dueDate)
                            taskComposerOpen = false
                        },
                    )
                }
            }
            selectedTask?.let { task ->
                val currentTask = state.tasks.firstOrNull { it.id == task.id } ?: task
                ModalBottomSheet(onDismissRequest = { selectedTask = null }) {
                    ChannelTaskDetails(
                        task = currentTask,
                        canManage = selected?.canPublish == true,
                        submissions = state.submissions[task.id].orEmpty(),
                        submissionsLoading = task.id in state.submissionsLoading,
                        errorMessage = state.errorMessage,
                        busy = state.actionInProgress,
                        onSubmit = { comment -> viewModel.submitTask(task.id, comment, emptyList()) },
                        onPickSubmissionFiles = { comment ->
                            pendingUpload = PendingChannelTaskUpload(task.id, comment)
                            taskFilePicker.launch(acceptedTaskFileTypes)
                        },
                        onPickTaskFiles = {
                            pendingUpload = PendingChannelTaskUpload(task.id)
                            taskFilePicker.launch(acceptedTaskFileTypes)
                        },
                        onReview = { submissionId, status, feedback, score ->
                            viewModel.reviewSubmission(task.id, submissionId, status, feedback, score)
                        },
                        onSetClosed = { closed -> viewModel.setTaskClosed(task.id, closed) },
                        onDownload = { file -> viewModel.downloadTaskFile(task.id, file) },
                    )
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
    val haptic = LocalHapticFeedback.current
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
                text = "Avisos, recursos y tareas de tus materias",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onRefresh()
            },
            enabled = !loading,
        ) {
            Text(if (loading) "Actualizando…" else "Actualizar")
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
            text = channel.groupName + " · " + channel.teacherDisplayName,
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
                .semantics(mergeDescendants = true) {
                    this.role = Role.Tab
                    this.selected = selected
                }.clickable(onClickLabel = "Abrir $label", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector =
                    when (label) {
                        "Recursos" -> Icons.Filled.AttachFile
                        "Tareas" -> Icons.AutoMirrored.Filled.Assignment
                        else -> Icons.Filled.Campaign
                    },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskComposer(
    title: String,
    instructions: String,
    busy: Boolean,
    onTitleChange: (String) -> Unit,
    onInstructionsChange: (String) -> Unit,
    onCreate: (Long) -> Unit,
) {
    val datePickerState = rememberDatePickerState()
    var datePickerOpen by remember { mutableStateOf(false) }
    val selectedDate =
        datePickerState.selectedDateMillis?.let { millis ->
            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
        }

    V8GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm)) {
            Text("Nueva tarea", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !busy,
                label = { Text("Título") },
            )
            OutlinedTextField(
                value = instructions,
                onValueChange = onInstructionsChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5,
                enabled = !busy,
                label = { Text("Instrucciones") },
            )
            TextButton(onClick = { datePickerOpen = true }, enabled = !busy) {
                Text(
                    selectedDate?.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
                        ?: "Seleccionar fecha de entrega",
                )
            }
            V8RedPrimaryButton(
                text = if (busy) "Publicando tarea…" else "Publicar tarea",
                onClick = {
                    selectedDate?.let { date ->
                        onCreate(
                            date
                                .atTime(23, 59)
                                .atZone(ZoneId.systemDefault())
                                .toEpochSecond(),
                        )
                    }
                },
                enabled = !busy && title.isNotBlank() && selectedDate != null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (datePickerOpen) {
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = { datePickerOpen = false },
                    enabled = datePickerState.selectedDateMillis != null,
                ) { Text("Listo") }
            },
            dismissButton = { TextButton(onClick = { datePickerOpen = false }) { Text("Cancelar") } },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun ChannelTaskCard(
    task: org.companerodeescuela.shared.contracts.ChannelTask,
    onClick: () -> Unit,
) {
    V8GlassCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = "Abrir tarea ${task.title}", onClick = onClick),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs)) {
            Text(
                task.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            task.instructions.takeIf(String::isNotBlank)?.let { instructions ->
                Text(
                    instructions,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Entrega · " +
                        Instant
                            .ofEpochSecond(task.dueDateEpochSeconds)
                            .atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("d MMM, HH:mm")),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (task.closed) "Cerrada" else "Abierta",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ChannelTaskDetails(
    task: ChannelTask,
    canManage: Boolean,
    submissions: List<TaskSubmission>,
    submissionsLoading: Boolean,
    errorMessage: String?,
    busy: Boolean,
    onSubmit: (String) -> Unit,
    onPickSubmissionFiles: (String) -> Unit,
    onPickTaskFiles: () -> Unit,
    onReview: (String, TaskStatus, String?, Double?) -> Unit,
    onSetClosed: (Boolean) -> Unit,
    onDownload: (ChannelPrivateFileMetadata) -> Unit,
) {
    val submission = submissions.singleOrNull()
    var comment by remember(task.id, submission?.version) { mutableStateOf(submission?.comment.orEmpty()) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CompaneroSpacing.page, vertical = CompaneroSpacing.md),
        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
    ) {
        Text(task.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        task.instructions.takeIf(String::isNotBlank)?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            "Fecha de entrega · " +
                Instant
                    .ofEpochSecond(task.dueDateEpochSeconds)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Publicada por ${task.teacherDisplayName}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (task.files.isNotEmpty()) {
            Text("Archivos de la tarea", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            TaskFileRows(task.files, busy, onDownload)
        }
        errorMessage?.let {
            StatusNotice(
                title = "No se pudo completar la acción",
                message = it,
                tone = NoticeTone.ERROR,
            )
        }
        if (canManage) {
            TextButton(
                onClick = { onSetClosed(!task.closed) },
                enabled = !busy,
            ) {
                Text(if (task.closed) "Reabrir tarea" else "Cerrar tarea")
            }
            V8RedPrimaryButton(
                text = if (busy) "Subiendo archivos…" else "Agregar archivos de la tarea",
                onClick = onPickTaskFiles,
                enabled = !busy && !task.closed,
                modifier = Modifier.fillMaxWidth(),
            )
            when {
                submissionsLoading -> CircularProgressIndicator()
                submissions.isEmpty() ->
                    StatusNotice(
                        title = "Sin entregas",
                        message = "Las entregas de tus estudiantes aparecerán aquí.",
                    )
                else -> {
                    Text("Entregas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    submissions.forEach { item ->
                        TaskSubmissionReviewCard(
                            submission = item,
                            busy = busy,
                            onReview = { status, feedback, score -> onReview(item.id, status, feedback, score) },
                            onDownload = onDownload,
                        )
                    }
                }
            }
        } else {
            if (task.closed) {
                StatusNotice(title = "Tarea cerrada", message = "El docente ya no recibe entregas.")
            }
            if (submissionsLoading) {
                CircularProgressIndicator()
            } else {
                submission?.let {
                    StatusNotice(
                        title = taskSubmissionStatusLabel(it.status),
                        message = it.feedback ?: it.comment ?: "El docente revisará tu entrega.",
                        tone = if (it.feedback == null) NoticeTone.INFO else NoticeTone.WARNING,
                    )
                    if (it.status == TaskStatus.REVIEWED && it.score != null) {
                        Text(
                            "Calificación de tarea: ${it.score} / 10",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (it.files.isNotEmpty()) {
                        Text("Archivos enviados", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        TaskFileRows(it.files, busy, onDownload)
                    }
                }
                if (!task.closed && (submission == null || submission.status == TaskStatus.RETURNED)) {
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it.take(2_000) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 5,
                        enabled = !busy,
                        label = { Text("Comentario para el docente") },
                    )
                    V8RedPrimaryButton(
                        text =
                            if (busy) {
                                "Enviando…"
                            } else if (submission == null) {
                                "Enviar entrega"
                            } else {
                                "Reenviar corrección"
                            },
                        onClick = { onSubmit(comment) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        onClick = { onPickSubmissionFiles(comment) },
                        enabled = !busy,
                    ) {
                        Text(if (busy) "Subiendo archivos…" else "Adjuntar archivos y enviar")
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskFileRows(
    files: List<ChannelPrivateFileMetadata>,
    busy: Boolean,
    onDownload: (ChannelPrivateFileMetadata) -> Unit,
) {
    files.forEach { file ->
        V8GlassAssistChip(
            onClick = { onDownload(file) },
            enabled = !busy,
            label = { Text(file.fileName, maxLines = 1) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "Archivo privado",
                )
            },
        )
    }
}

@Composable
private fun TaskSubmissionReviewCard(
    submission: TaskSubmission,
    busy: Boolean,
    onReview: (TaskStatus, String?, Double?) -> Unit,
    onDownload: (ChannelPrivateFileMetadata) -> Unit,
) {
    var feedback by remember(submission.id, submission.version) { mutableStateOf(submission.feedback.orEmpty()) }
    var score by remember(submission.id, submission.version) { mutableStateOf(submission.score?.toString().orEmpty()) }
    V8GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs)) {
            Text(submission.studentDisplayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                taskSubmissionStatusLabel(submission.status),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            submission.comment?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (submission.files.isNotEmpty()) {
                TaskFileRows(submission.files, busy, onDownload)
            }
            if (submission.status == TaskStatus.SUBMITTED) {
                OutlinedTextField(
                    value = feedback,
                    onValueChange = { feedback = it.take(4_000) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    enabled = !busy,
                    label = { Text("Comentarios de revisión") },
                )
                OutlinedTextField(
                    value = score,
                    onValueChange = { score = it.take(6) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !busy,
                    label = { Text("Calificación de tarea (0 a 10, opcional)") },
                    isError = score.isNotBlank() && score.toDoubleOrNull()?.let { it.isFinite() && it in 0.0..10.0 } != true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs)) {
                    TextButton(
                        onClick = { onReview(TaskStatus.RETURNED, feedback, null) },
                        enabled = !busy && feedback.isNotBlank(),
                    ) {
                        Text("Devolver")
                    }
                    TextButton(
                        onClick = { onReview(TaskStatus.REVIEWED, feedback.ifBlank { null }, score.toDoubleOrNull()) },
                        enabled = !busy && (score.isBlank() || score.toDoubleOrNull()?.let { it.isFinite() && it in 0.0..10.0 } == true),
                    ) {
                        Text("Marcar revisada")
                    }
                }
            } else if (submission.feedback != null) {
                submission.feedback?.let { feedbackMessage ->
                    Text(
                        feedbackMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun taskSubmissionStatusLabel(status: org.companerodeescuela.shared.contracts.TaskStatus): String =
    when (status) {
        org.companerodeescuela.shared.contracts.TaskStatus.PENDING -> "Pendiente"
        org.companerodeescuela.shared.contracts.TaskStatus.SUBMITTING -> "Enviando entrega"
        org.companerodeescuela.shared.contracts.TaskStatus.SUBMITTED -> "Entrega recibida"
        org.companerodeescuela.shared.contracts.TaskStatus.OVERDUE -> "Entrega fuera de plazo"
        org.companerodeescuela.shared.contracts.TaskStatus.RETURNED -> "Devuelta para corrección"
        org.companerodeescuela.shared.contracts.TaskStatus.REVIEWED -> "Revisada"
        org.companerodeescuela.shared.contracts.TaskStatus.ERROR -> "No se pudo enviar"
    }

private fun readChannelTaskUploads(
    contentResolver: ContentResolver,
    uris: List<Uri>,
): List<ChannelTaskUpload> {
    if (uris.size > 8) throw IOException("Selecciona como máximo 8 archivos.")
    val allowedTypes =
        mapOf(
            "pdf" to "application/pdf",
            "png" to "image/png",
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "txt" to "text/plain",
            "csv" to "text/csv",
            "doc" to "application/msword",
            "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        )
    return uris.map { uri ->
        val cursor =
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?: throw IOException("No se pudo obtener el nombre del archivo.")
        val fileName =
            cursor
                .use {
                    if (!it.moveToFirst()) null else it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                }?.takeIf(String::isNotBlank)
                ?: throw IOException("El archivo seleccionado no tiene un nombre válido.")
        if (!fileName.matches(Regex("[A-Za-z0-9][A-Za-z0-9._ -]{0,159}"))) {
            throw IOException("El nombre del archivo debe usar letras, números, espacios, puntos, guiones o guiones bajos.")
        }
        val contentType =
            contentResolver.getType(uri)?.substringBefore(';')?.lowercase()
                ?: throw IOException("No se pudo determinar el tipo del archivo.")
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (allowedTypes[extension] != contentType) {
            throw IOException("El tipo de contenido de $fileName no coincide con su extensión.")
        }
        val input = contentResolver.openInputStream(uri) ?: throw IOException("No se pudo abrir $fileName.")
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        input.use { stream ->
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                if (output.size() + count > 8 * 1024 * 1024) {
                    throw IOException("$fileName excede el límite de 8 MB.")
                }
                output.write(buffer, 0, count)
            }
        }
        val bytes = output.toByteArray()
        if (bytes.isEmpty()) throw IOException("$fileName está vacío.")
        ChannelTaskUpload(fileName, contentType, bytes)
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
                    text = "Respuesta rápida",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
                ) {
                    items(post.allowedResponses.toList(), key = { it.name }) { response ->
                        val haptic = LocalHapticFeedback.current
                        V8GlassAssistChip(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onAcknowledge(post, response)
                            },
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
    val haptic = LocalHapticFeedback.current
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
            V8RedPrimaryButton(
                text = if (busy) "Publicando…" else "Publicar",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPublish()
                },
                enabled = !busy && (body.isNotBlank() || resourceUrl.isNotBlank()),
                modifier = Modifier.fillMaxWidth(),
            )
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
                    text = "Puedes leer y usar respuestas rápidas, pero no enviar mensajes.",
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
        ChannelPostType.MEETING -> "Reunión"
    }

private fun responseLabel(response: ChannelPresetResponse): String =
    when (response) {
        ChannelPresetResponse.ACKNOWLEDGED -> "Entendido"
        ChannelPresetResponse.CONFIRMED -> "Confirmado"
        ChannelPresetResponse.WILL_ATTEND -> "Asistiré"
        ChannelPresetResponse.CANNOT_ATTEND -> "No podré asistir"
        ChannelPresetResponse.NEED_CLARIFICATION -> "Necesito aclaración"
    }
