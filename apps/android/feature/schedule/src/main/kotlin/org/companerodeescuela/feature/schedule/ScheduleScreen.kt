package org.companerodeescuela.feature.schedule

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import kotlinx.coroutines.launch
import org.companerodeescuela.core.academic.PersonalScheduleDraft
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroWindowBreakpoints
import org.companerodeescuela.core.motion.CompaneroMotionDuration
import org.companerodeescuela.core.motion.LocalCompaneroMotionPreferences
import org.companerodeescuela.core.ui.component.AcademicTimelineItem
import org.companerodeescuela.core.ui.component.CompaneroSurface
import org.companerodeescuela.core.ui.component.CompaneroSurfaceRole
import org.companerodeescuela.core.ui.component.ExpressiveSegmentedControl
import org.companerodeescuela.core.ui.component.NoticeTone
import org.companerodeescuela.core.ui.component.StatusNotice
import org.companerodeescuela.shared.contracts.ScheduleEntry
import org.companerodeescuela.shared.contracts.ScheduleSource

private enum class AgendaMode {
    DAY,
    WEEK,
}

@Composable
fun ScheduleScreen(
    modifier: Modifier = Modifier,
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importProcessor = remember(context) { ScheduleImportProcessor(context) }
    var mode by remember { mutableStateOf(AgendaMode.DAY) }
    var showEditor by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<ScheduleEntry?>(null) }
    var editingImportIndex by remember { mutableStateOf<Int?>(null) }
    var importBusy by remember { mutableStateOf(false) }
    var moveProposal by remember { mutableStateOf<AgendaMoveProposal?>(null) }

    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            importBusy = true
            runCatching { importProcessor.extractText(uri) }
                .onSuccess(viewModel::stageImport)
                .onFailure { viewModel.reportImportFailure() }
            importBusy = false
        }
    }

    if (state.loading && state.entries.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    var selectedDay by remember(state.entries) {
        val today = LocalDate.now().dayOfWeek.name
        mutableStateOf(
            today.takeIf { it in academicDaysV8 } ?: "MONDAY",
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = CompaneroSize.homeContentMaxWidth)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CompaneroSpacing.page, vertical = CompaneroSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.section),
    ) {
        Text("Agenda", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Tu horario académico, incluso sin conexión.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ExpressiveSegmentedControl(
            options = listOf("Día", "Semana"),
            selectedIndex = if (mode == AgendaMode.DAY) 0 else 1,
            onSelected = { mode = if (it == 0) AgendaMode.DAY else AgendaMode.WEEK },
        )

        if (state.fromCache) {
            StatusNotice(
                title = "Agenda guardada",
                message = "Estás viendo la última versión disponible en este dispositivo.",
                tone = NoticeTone.WARNING,
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

        if (state.entries.isEmpty()) {
            EmptyScheduleActions(
                loading = state.loading || state.actionInProgress || importBusy,
                onSync = viewModel::load,
                onImport = {
                    documentLauncher.launch(arrayOf("application/pdf", "image/*"))
                },
                onManual = {
                    editingEntry = null
                    editingImportIndex = null
                    showEditor = true
                },
            )
        } else {
            val reducedMotion = LocalCompaneroMotionPreferences.current.reducedMotion
            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    val duration = if (reducedMotion) {
                        CompaneroMotionDuration.FAST
                    } else {
                        CompaneroMotionDuration.STANDARD
                    }
                    val enter = fadeIn(tween(duration)) +
                        if (reducedMotion) slideInHorizontally(tween(0)) { 0 }
                        else slideInHorizontally(tween(duration)) { it / 8 }
                    val exit = fadeOut(tween(duration)) +
                        if (reducedMotion) slideOutHorizontally(tween(0)) { 0 }
                        else slideOutHorizontally(tween(duration)) { -it / 8 }
                    enter.togetherWith(exit)
                },
                label = "agendaMode",
            ) { currentMode ->
                when (currentMode) {
                    AgendaMode.DAY -> DayAgenda(
                        entries = state.entries,
                        selectedDay = selectedDay,
                        onSelectedDay = { selectedDay = it },
                        onEdit = {
                            editingEntry = it
                            editingImportIndex = null
                            showEditor = true
                        },
                        onDelete = viewModel::deletePersonal,
                        onMoveRequest = { moveProposal = it },
                    )
                    AgendaMode.WEEK -> WeekAgenda(
                        entries = state.entries,
                        onEdit = {
                            editingEntry = it
                            editingImportIndex = null
                            showEditor = true
                        },
                        onDelete = viewModel::deletePersonal,
                        onMoveRequest = { moveProposal = it },
                    )
                }
            }

            Text(
                text = "Tip: mantén presionada una clase personal y arrástrala para moverla. Se ajusta en intervalos de 15 minutos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
            ) {
                OutlinedButton(
                    onClick = {
                        documentLauncher.launch(arrayOf("application/pdf", "image/*"))
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !importBusy && !state.actionInProgress,
                ) {
                    Icon(Icons.Filled.Description, contentDescription = null)
                    Text(" Importar")
                }
                Button(
                    onClick = {
                        editingEntry = null
                        editingImportIndex = null
                        showEditor = true
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !state.actionInProgress,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(" Clase")
                }
            }

            TextButton(
                onClick = viewModel::load,
                enabled = !state.loading,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(if (state.loading) "Actualizando…" else "Sincronizar UPTlax")
            }
        }
    }

    if (showEditor) {
        val initial = editingEntry?.toDraft()
            ?: editingImportIndex?.let { state.importCandidates.getOrNull(it) }
        ScheduleEditorDialog(
            initial = initial,
            onDismiss = {
                showEditor = false
                editingEntry = null
                editingImportIndex = null
            },
            onSave = { draft, days ->
                val importIndex = editingImportIndex
                if (importIndex != null) {
                    val selected = days.firstOrNull() ?: draft.dayOfWeek
                    viewModel.updateImportCandidate(importIndex, draft.copy(dayOfWeek = selected))
                } else {
                    viewModel.savePersonalDays(draft, days)
                }
                showEditor = false
                editingEntry = null
                editingImportIndex = null
            },
        )
    }

    moveProposal?.let { proposal ->
        MoveScheduleDialog(
            proposal = proposal,
            busy = state.actionInProgress,
            onDismiss = { moveProposal = null },
            onConfirm = {
                viewModel.movePersonal(proposal)
                moveProposal = null
            },
        )
    }

    if (state.importCandidates.isNotEmpty()) {
        ImportReviewDialog(
            candidates = state.importCandidates,
            busy = state.actionInProgress,
            onEdit = { index ->
                editingImportIndex = index
                editingEntry = null
                showEditor = true
            },
            onDismiss = viewModel::discardImport,
            onConfirm = viewModel::confirmImport,
        )
    }
}

@Composable
private fun EmptyScheduleActions(
    loading: Boolean,
    onSync: () -> Unit,
    onImport: () -> Unit,
    onManual: () -> Unit,
) {
    StatusNotice(
        title = "Aún no tienes un horario",
        message = "No encontramos clases asociadas a tu cuenta. UPTlax sigue siendo la fuente oficial; también puedes agregar una copia personal.",
    )
    Button(
        onClick = onSync,
        enabled = !loading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Sincronizar con UPTlax")
    }
    OutlinedButton(
        onClick = onImport,
        enabled = !loading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Importar PDF o imagen")
    }
    OutlinedButton(
        onClick = onManual,
        enabled = !loading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Crear horario manualmente")
    }
}

@Composable
private fun DayAgenda(
    entries: List<ScheduleEntry>,
    selectedDay: String,
    onSelectedDay: (String) -> Unit,
    onEdit: (ScheduleEntry) -> Unit,
    onDelete: (ScheduleEntry) -> Unit,
    onMoveRequest: (AgendaMoveProposal) -> Unit,
) {
    val days = academicDaysV8
    val dayEntries = entries.filter { it.dayOfWeek == selectedDay }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth >= CompaneroWindowBreakpoints.medium) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.md),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.width(CompaneroSize.agendaRailWidth),
                    verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
                ) {
                    days.forEach { day ->
                        FilterChip(
                            selected = selectedDay == day,
                            onClick = { onSelectedDay(day) },
                            label = { Text(dayLabel(day)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                DayAgendaDetails(
                    day = selectedDay,
                    entries = dayEntries,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onMoveRequest = onMoveRequest,
                    allEntries = entries,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.md)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
                ) {
                    days.chunked(3).forEach { rowDays ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
                        ) {
                            rowDays.forEach { day ->
                                FilterChip(
                                    selected = selectedDay == day,
                                    onClick = { onSelectedDay(day) },
                                    label = { Text(dayShortLabel(day)) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
                DayAgendaDetails(
                    day = selectedDay,
                    entries = dayEntries,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onMoveRequest = onMoveRequest,
                    allEntries = entries,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun DayAgendaDetails(
    day: String,
    entries: List<ScheduleEntry>,
    onEdit: (ScheduleEntry) -> Unit,
    onDelete: (ScheduleEntry) -> Unit,
    onMoveRequest: (AgendaMoveProposal) -> Unit,
    allEntries: List<ScheduleEntry>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
    ) {
        if (entries.isEmpty()) {
            StatusNotice(
                title = dayLabel(day),
                message = "No tienes clases programadas este día.",
            )
        } else {
            Text(dayLabel(day), style = MaterialTheme.typography.titleMedium)
            AgendaTimeline(
                entries = entries,
                allEntries = allEntries,
                onEdit = onEdit,
                onDelete = onDelete,
                onMoveRequest = onMoveRequest,
            )
        }
    }
}

@Composable
private fun WeekAgenda(
    entries: List<ScheduleEntry>,
    onEdit: (ScheduleEntry) -> Unit,
    onDelete: (ScheduleEntry) -> Unit,
    onMoveRequest: (AgendaMoveProposal) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.section),
    ) {
        academicDaysV8.forEach { day ->
            val dayEntries = entries.filter { it.dayOfWeek == day }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
            ) {
                Text(dayLabel(day), style = MaterialTheme.typography.titleMedium)
                if (dayEntries.isEmpty()) {
                    Text(
                        text = "Sin clases",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    AgendaTimeline(
                        entries = dayEntries,
                        allEntries = entries,
                        onEdit = onEdit,
                        onDelete = onDelete,
                        onMoveRequest = onMoveRequest,
                    )
                }
            }
        }
    }
}

@Composable
private fun AgendaTimeline(
    entries: List<ScheduleEntry>,
    allEntries: List<ScheduleEntry>,
    onEdit: (ScheduleEntry) -> Unit,
    onDelete: (ScheduleEntry) -> Unit,
    onMoveRequest: (AgendaMoveProposal) -> Unit,
) {
    val railColor = MaterialTheme.colorScheme.outlineVariant
    CompaneroSurface(
        modifier = Modifier.fillMaxWidth(),
        role = CompaneroSurfaceRole.INSET,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val railX = 59.dp.toPx()
                    drawLine(
                        color = railColor,
                        start = Offset(railX, 12.dp.toPx()),
                        end = Offset(railX, size.height - 12.dp.toPx()),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
                .padding(horizontal = CompaneroSpacing.card, vertical = CompaneroSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
        ) {
            entries.sortedBy { it.startsAt }.forEach { entry ->
                AgendaTimelineEntry(
                    entry = entry,
                    allEntries = allEntries,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onMoveRequest = onMoveRequest,
                )
            }
        }
    }
}

@Composable
private fun AgendaTimelineEntry(
    entry: ScheduleEntry,
    allEntries: List<ScheduleEntry>,
    onEdit: (ScheduleEntry) -> Unit,
    onDelete: (ScheduleEntry) -> Unit,
    onMoveRequest: (AgendaMoveProposal) -> Unit,
) {
    var dragOffset by remember(entry.courseId) { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val quarterHourPx = with(density) { 24.dp.toPx() }
    val horizontalThresholdPx = with(density) { 72.dp.toPx() }
    val draggable = entry.source != ScheduleSource.INSTITUTIONAL

    val dragModifier = if (!draggable) {
        Modifier
    } else {
        Modifier
            .graphicsLayer {
                translationX = dragOffset.x
                translationY = dragOffset.y
            }
            .pointerInput(entry.courseId, allEntries) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragCancel = { dragOffset = Offset.Zero },
                    onDragEnd = {
                        val targetStart = AgendaEditingRules.shiftFromDrag(
                            entry = entry,
                            verticalPixels = dragOffset.y,
                            pixelsPerQuarterHour = quarterHourPx,
                        )
                        val targetDay = AgendaEditingRules.adjacentDay(
                            currentDay = entry.dayOfWeek,
                            horizontalPixels = dragOffset.x,
                            thresholdPixels = horizontalThresholdPx,
                        )
                        if (targetStart != null) {
                            AgendaEditingRules.proposeMove(
                                entry = entry,
                                targetDay = targetDay,
                                targetStart = targetStart,
                                existing = allEntries,
                            )?.let { proposal ->
                                if (
                                    proposal.targetDay != entry.dayOfWeek ||
                                    proposal.targetStart != entry.startsAt
                                ) {
                                    onMoveRequest(proposal)
                                }
                            }
                        }
                        dragOffset = Offset.Zero
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragOffset += amount
                    },
                )
            }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(dragModifier),
        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xxs),
    ) {
        AcademicTimelineItem(
            time = "${entry.startsAt}–${entry.endsAt}",
            title = entry.subjectName,
            subtitle = locationAndTeacher(entry),
            status = when (entry.source) {
                ScheduleSource.INSTITUTIONAL -> null
                ScheduleSource.MANUAL -> "Horario personal"
                ScheduleSource.OCR_IMPORT -> "Importado · revisado"
            },
            subjectKey = entry.subjectCode.ifBlank { entry.subjectName },
            continuousRail = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (entry.source != ScheduleSource.INSTITUTIONAL) {
            Row(
                modifier = Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
            ) {
                TextButton(onClick = { onEdit(entry) }) { Text("Editar") }
                TextButton(onClick = { onDelete(entry) }) { Text("Eliminar") }
            }
        }
    }
}

@Composable
private fun ScheduleEditorDialog(
    initial: PersonalScheduleDraft?,
    onDismiss: () -> Unit,
    onSave: (PersonalScheduleDraft, Set<String>) -> Unit,
) {
    var subject by remember(initial) { mutableStateOf(initial?.subjectName.orEmpty()) }
    var teacher by remember(initial) { mutableStateOf(initial?.teacherName.orEmpty()) }
    var room by remember(initial) { mutableStateOf(initial?.classroomName.orEmpty()) }
    var group by remember(initial) { mutableStateOf(initial?.groupName.orEmpty()) }
    var start by remember(initial) { mutableStateOf(initial?.startsAt ?: "08:00") }
    var end by remember(initial) { mutableStateOf(initial?.endsAt ?: "10:00") }
    var selectedDays by remember(initial) {
        mutableStateOf(setOf(initial?.dayOfWeek ?: "MONDAY"))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nueva clase" else "Editar clase") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
            ) {
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Materia") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
                ) {
                    academicDaysV8.chunked(3).forEach { rowDays ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
                        ) {
                            rowDays.forEach { value ->
                                FilterChip(
                                    selected = value in selectedDays,
                                    onClick = {
                                        selectedDays = if (value in selectedDays) {
                                            selectedDays - value
                                        } else {
                                            selectedDays + value
                                        }
                                    },
                                    label = { Text(dayShortLabel(value)) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
                ) {
                    OutlinedTextField(
                        value = start,
                        onValueChange = { start = it },
                        label = { Text("Inicio") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = end,
                        onValueChange = { end = it },
                        label = { Text("Fin") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("Aula / laboratorio") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("Docente") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = group,
                    onValueChange = { group = it },
                    label = { Text("Grupo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Este horario es personal y no habilita asistencia ni cambia tu inscripción oficial.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        PersonalScheduleDraft(
                            id = initial?.id,
                            subjectCode = initial?.subjectCode.orEmpty(),
                            subjectName = subject,
                            groupName = group,
                            teacherName = teacher,
                            dayOfWeek = selectedDays.firstOrNull() ?: "MONDAY",
                            startsAt = start,
                            endsAt = end,
                            classroomName = room,
                            buildingName = initial?.buildingName,
                            source = initial?.source ?: ScheduleSource.MANUAL,
                            recurrence = initial?.recurrence
                                ?: org.companerodeescuela.shared.contracts.ScheduleRecurrence.WEEKLY,
                            seriesId = initial?.seriesId,
                            effectiveDate = initial?.effectiveDate,
                        ),
                        selectedDays,
                    )
                },
                enabled = subject.isNotBlank() &&
                    start.isNotBlank() &&
                    end.isNotBlank() &&
                    selectedDays.isNotEmpty(),
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@Composable
private fun ImportReviewDialog(
    candidates: List<PersonalScheduleDraft>,
    busy: Boolean,
    onEdit: (Int) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Revisa tu horario") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
            ) {
                Text(
                    "Detectamos " + candidates.size + " clases. Corrige cualquier dato antes de guardar.",
                )
                candidates.forEachIndexed { index, draft ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Column(
                            modifier = Modifier.padding(CompaneroSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
                        ) {
                            Text(
                                dayShortLabel(draft.dayOfWeek) + " · " +
                                    draft.startsAt + "–" + draft.endsAt,
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(draft.subjectName, style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { onEdit(index) }) { Text("Editar") }
                        }
                    }
                }
                Text(
                    text = "El OCR nunca guarda automáticamente: tú confirmas el resultado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) {
                Text(if (busy) "Guardando…" else "Confirmar horario")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Descartar") }
        },
    )
}

private fun ScheduleEntry.toDraft(): PersonalScheduleDraft =
    PersonalScheduleDraft(
        id = courseId,
        subjectCode = subjectCode,
        subjectName = subjectName,
        groupName = groupName,
        teacherName = teacherName,
        dayOfWeek = dayOfWeek,
        startsAt = startsAt,
        endsAt = endsAt,
        classroomName = classroomName,
        buildingName = buildingName,
        source = source,
    )

private fun locationAndTeacher(entry: ScheduleEntry): String {
    val location = listOfNotNull(entry.classroomName, entry.buildingName)
        .joinToString(" · ")
        .ifBlank { "Aula por confirmar" }
    return listOf(location, entry.teacherName)
        .filter(String::isNotBlank)
        .joinToString(" · ")
}

private fun dayOrder(day: String): Int = when (day) {
    "MONDAY" -> 1
    "TUESDAY" -> 2
    "WEDNESDAY" -> 3
    "THURSDAY" -> 4
    "FRIDAY" -> 5
    "SATURDAY" -> 6
    "SUNDAY" -> 7
    else -> 8
}

private fun dayShortLabel(day: String): String = when (day) {
    "MONDAY" -> "Lun"
    "TUESDAY" -> "Mar"
    "WEDNESDAY" -> "Mié"
    "THURSDAY" -> "Jue"
    "FRIDAY" -> "Vie"
    "SATURDAY" -> "Sáb"
    "SUNDAY" -> "Dom"
    else -> day.take(3)
}

private fun dayLabel(day: String): String = when (day) {
    "MONDAY" -> "Lunes"
    "TUESDAY" -> "Martes"
    "WEDNESDAY" -> "Miércoles"
    "THURSDAY" -> "Jueves"
    "FRIDAY" -> "Viernes"
    "SATURDAY" -> "Sábado"
    "SUNDAY" -> "Domingo"
    else -> day
}
