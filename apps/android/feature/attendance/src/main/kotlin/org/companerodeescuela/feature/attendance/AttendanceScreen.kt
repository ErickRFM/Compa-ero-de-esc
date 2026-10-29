package org.companerodeescuela.feature.attendance

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.companerodeescuela.core.database.LocalAttendanceRecord
import org.companerodeescuela.core.database.LocalAttendanceSyncState
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.designsystem.theme.CompanionColors
import org.companerodeescuela.core.designsystem.v8.V8AttendanceEvidence
import org.companerodeescuela.core.designsystem.v8.V8GlassCard
import org.companerodeescuela.core.designsystem.v8.V8MatteBackdrop
import org.companerodeescuela.core.designsystem.v8.V8RedColors
import org.companerodeescuela.core.designsystem.v8.V8ScreenHeader
import org.companerodeescuela.core.ui.component.CompaneroHeroSurface
import org.companerodeescuela.core.ui.component.CompaneroSurface
import org.companerodeescuela.core.ui.component.NoticeTone
import org.companerodeescuela.core.ui.component.StatusNotice
import org.companerodeescuela.shared.contracts.AttendanceDisposition
import org.companerodeescuela.shared.contracts.AttendanceQrInspectionResponse
import org.companerodeescuela.shared.contracts.AttendanceQrInspectionStatus
import org.companerodeescuela.shared.contracts.AttendanceRecordResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse
import org.companerodeescuela.shared.contracts.AttendanceStatus
import org.companerodeescuela.shared.contracts.ClassOccurrenceContract
import org.companerodeescuela.shared.contracts.TeacherClassContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun attendanceScreen(
    modifier: Modifier = Modifier,
    requestedMode: AttendanceMode? = null,
    requestedClassroom: TeacherClassContext? = null,
    requestedSessionId: String? = null,
    onConsumeRequestedSession: () -> Unit = {},
    viewModel: AttendanceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(requestedMode, requestedClassroom) {
        viewModel.setClassroomContext(requestedClassroom)
        requestedMode?.let(viewModel::selectMode)
    }
    LaunchedEffect(requestedSessionId, requestedMode) {
        val sessionId = requestedSessionId ?: return@LaunchedEffect
        if (requestedMode == AttendanceMode.STUDENT &&
            sessionId.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}"))
        ) {
            viewModel.openRequestedSession(sessionId)
        }
        onConsumeRequestedSession()
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imageReader = remember(context) { AttendanceQrImageReader(context) }
    val imageLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                runCatching { imageReader.read(uri) }
                    .onSuccess { token ->
                        if (token.isNullOrBlank()) {
                            viewModel.inspectQr("")
                        } else {
                            viewModel.inspectQr(token)
                        }
                    }.onFailure { viewModel.inspectQr("") }
            }
        }

    if (state.scannerOpen) {
        Dialog(
            onDismissRequest = viewModel::dismissScanner,
            properties =
                DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false,
                ),
        ) {
            AttendanceQrScanner(
                onToken = viewModel::inspectQr,
                onClose = viewModel::dismissScanner,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    when (state.mode) {
        AttendanceMode.LOADING -> loadingAttendance(modifier)
        AttendanceMode.STUDENT ->
            studentAttendanceContent(
                state = state,
                onRefresh = viewModel::refresh,
                onScan = viewModel::openScanner,
                onPrepareClassContingency = viewModel::prepareClassContingency,
                onStartSchoolDay = viewModel::openSchoolDayScanner,
                onConfirmClassCall = viewModel::confirmClassCall,
                onPickImage = { imageLauncher.launch(arrayOf("image/*")) },
                onInspectToken = viewModel::inspectQr,
                onDismissInspection = viewModel::clearQrInspection,
                modifier = modifier,
            )
        AttendanceMode.TEACHER ->
            teacherAttendanceContent(
                state = state,
                onRefresh = viewModel::refresh,
                onOpen = viewModel::openAttendance,
                onClose = viewModel::closeTeacherSession,
                onResendNotice = viewModel::resendTeacherSessionNotice,
                onLoadHistory = viewModel::loadTeacherHistory,
                onRefreshRoster = viewModel::refreshRoster,
                onReview = viewModel::reviewRecord,
                onMark = viewModel::markRecord,
                modifier = modifier,
            )
        AttendanceMode.UNSUPPORTED ->
            unsupportedAttendance(
                message = state.errorMessage,
                onRetry = viewModel::refresh,
                modifier = modifier,
            )
    }
}

@Composable
fun studentAttendanceContent(
    state: AttendanceUiState,
    onRefresh: () -> Unit,
    onScan: (String?) -> Unit,
    onPrepareClassContingency: (String) -> Unit,
    onStartSchoolDay: () -> Unit,
    onConfirmClassCall: (String) -> Unit,
    onPickImage: () -> Unit,
    onInspectToken: (String) -> Unit,
    onDismissInspection: () -> Unit,
    modifier: Modifier,
) {
    val occurrenceById =
        remember(state.occurrences) {
            state.occurrences.associateBy { it.id }
        }
    var showPasteDialog by remember { mutableStateOf(false) }
    var historyExpanded by remember { mutableStateOf(false) }
    var historyFilter by remember { mutableStateOf(StudentHistoryFilter.ALL) }
    val selectedSessionFocus = remember { BringIntoViewRequester() }
    LaunchedEffect(state.requestedStudentSessionId, state.activeSessions) {
        if (state.activeSessions.any { it.id == state.requestedStudentSessionId }) {
            withFrameNanos { }
            selectedSessionFocus.bringIntoView()
        }
    }
    // A monotonic interval advances the server timestamp even if the device wall clock changes.
    var monotonicNow by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            monotonicNow = android.os.SystemClock.elapsedRealtime()
        }
    }
    val schoolRecorded =
        state.schoolPresenceReceivedRealtime?.let {
            schoolDayRecordedAtServerTime(state.schoolPresence, (monotonicNow - it) / 1_000)
        } ?: false

    Box(modifier = modifier.fillMaxSize()) {
        if (!org.companerodeescuela.core.designsystem.v8.LocalV8GlassEnabled.current) {
            V8MatteBackdrop(modifier = Modifier.matchParentSize())
        }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .widthIn(max = CompaneroSize.homeContentMaxWidth)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = CompaneroSpacing.page, vertical = CompaneroSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.section),
        ) {
            V8ScreenHeader {
                org.companerodeescuela.core.designsystem.v8
                    .V8HeroTitle("Pase de", "lista")
            }
            V8AttendanceEvidence(
                // No independently authenticated campus witness is available.
                schoolNetworkVerified = null,
                locationVerified = null,
                ready = schoolRecorded && state.activeSessions.isNotEmpty(),
            )
            TextButton(onClick = onRefresh, enabled = !state.loading, modifier = Modifier.align(Alignment.End)) {
                Text(if (state.loading) "Actualizando…" else "Actualizar")
            }

            state.successMessage?.let {
                StatusNotice(
                    title = "Pase guardado",
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
            state.requestedStudentSessionId?.let { selectedId ->
                if (!state.loading && state.activeSessions.none { it.id == selectedId }) {
                    StatusNotice(
                        title = "Pase no disponible",
                        message = "El pase del aviso ya terminó o no está disponible para tu cuenta. No se envió ninguna confirmación.",
                    )
                }
            }

            if (!state.loading && state.activeSessions.isEmpty()) {
                StatusNotice(
                    title = "Esperando pase del docente",
                    message =
                        "Recibirás una notificación cuando tu docente abra el pase de lista. " +
                            "También aparecerá aquí, incluso si no recibiste el aviso.",
                )
            }

            state.activeSessions.sortedBy { it.id != state.requestedStudentSessionId }.forEach { session ->
                val occurrence = occurrenceById[session.occurrenceId]
                val local =
                    state.localRecords
                        .filter { it.sessionId == session.id }
                        .maxByOrNull { it.updatedAtEpochSeconds }

                Column(
                    modifier =
                        if (session.id ==
                            state.requestedStudentSessionId
                        ) {
                            Modifier.bringIntoViewRequester(selectedSessionFocus)
                        } else {
                            Modifier
                        },
                ) {
                    if (session.id == state.requestedStudentSessionId) {
                        Text("Pase del aviso · revisa la clase antes de enviar", style = MaterialTheme.typography.labelLarge)
                    }
                    studentSessionCard(
                        session = session,
                        occurrence = occurrence,
                        local = local,
                        onScan = { onScan(session.id) },
                        onImage = {
                            onPrepareClassContingency(session.id)
                            onPickImage()
                        },
                        onPaste = {
                            onPrepareClassContingency(session.id)
                            showPasteDialog = true
                        },
                        onConfirm = { onConfirmClassCall(session.id) },
                        confirmed = state.confirmedClassCalls[session.id],
                        busy = state.actionInProgress,
                        schoolPresenceActive = schoolRecorded,
                    )
                }
            }

            schoolDayPresenceCard(
                recorded = schoolRecorded,
                ssid = state.schoolNetworkSsid,
                expiresAtEpochSeconds = state.schoolPresence?.expiresAtEpochSeconds,
                busy = state.actionInProgress,
                onStart = onStartSchoolDay,
            )

            state.qrInspection?.let { inspection ->
                qrInspectionCard(
                    inspection = inspection,
                    occurrence = inspection.session?.occurrenceId?.let(occurrenceById::get),
                    busy = state.actionInProgress,
                    onDismiss = onDismissInspection,
                )
            }

            val historicalRecords = studentHistoricalRecords(state.studentHistory, state.activeSessions.map { it.id }.toSet())
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Mi historial", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (state.studentHistoryLoaded) {
                    Text("${historicalRecords.size} registros", style = MaterialTheme.typography.labelMedium)
                }
            }
            state.studentHistoryError?.let {
                StatusNotice(title = "Historial sin actualizar", message = it, tone = NoticeTone.ERROR)
            }
            if (!state.studentHistoryLoaded && state.studentHistoryError == null) {
                Text("Cargando historial…", style = MaterialTheme.typography.bodySmall)
            }
            if (historicalRecords.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm)) {
                    items(StudentHistoryFilter.entries) { option ->
                        FilterChip(
                            selected = historyFilter == option,
                            onClick = {
                                historyFilter = option
                                historyExpanded = false
                            },
                            label = { Text(option.label) },
                        )
                    }
                }
            }
            val filteredHistory = filterStudentHistory(historicalRecords, historyFilter)
            if (historicalRecords.isEmpty() && state.studentHistoryLoaded && state.studentHistoryError == null) {
                Text(
                    "Todavía no hay asistencias anteriores. Los resultados revisados por el docente aparecerán aquí.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else if (filteredHistory.isEmpty() && historicalRecords.isNotEmpty()) {
                Text("No hay registros con este estado.", style = MaterialTheme.typography.bodyMedium)
            }
            filteredHistory.take(if (historyExpanded) 100 else 5).forEach { record ->
                CompaneroSurface {
                    Column(
                        Modifier.padding(CompaneroSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
                    ) {
                        Text(
                            occurrenceById[record.occurrenceId]?.subjectName ?: "Clase registrada",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            Instant
                                .ofEpochSecond(record.receivedAtEpochSeconds)
                                .atZone(java.time.ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm", Locale.getDefault())),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(classCallRecordMessage(record), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (filteredHistory.size > 5) {
                TextButton(onClick = { historyExpanded = !historyExpanded }) {
                    Text(if (historyExpanded) "Ver menos" else "Ver todos (${filteredHistory.size})")
                }
            }
            val unsummarizedLocal = state.localRecords.filter { local -> state.studentHistory.none { it.sessionId == local.sessionId } }
            if (unsummarizedLocal.isNotEmpty()) {
                Text(
                    text = "Actividad reciente",
                    style = MaterialTheme.typography.titleMedium,
                )
                unsummarizedLocal.take(5).forEach { record ->
                    localAttendanceRow(record)
                }
            }
        }
    }

    if (showPasteDialog) {
        qrPasteDialog(
            onDismiss = { showPasteDialog = false },
            onInspect = {
                showPasteDialog = false
                onInspectToken(it)
            },
        )
    }
}

@Composable
private fun studentSessionCard(
    session: AttendanceSessionResponse,
    occurrence: ClassOccurrenceContract?,
    local: LocalAttendanceRecord?,
    onScan: () -> Unit,
    onImage: () -> Unit,
    onPaste: () -> Unit,
    onConfirm: () -> Unit,
    confirmed: AttendanceRecordResponse?,
    busy: Boolean,
    schoolPresenceActive: Boolean,
) {
    var showContingencyDialog by remember(session.id) { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
    ) {
        CompaneroHeroSurface(
            modifier = Modifier.fillMaxWidth(),
            containerColor = CompanionColors.graphite,
        ) {
            Column(
                modifier = Modifier.padding(CompaneroSpacing.md),
                verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
            ) {
                Text(
                    text = "EN VIVO  ●",
                    style = MaterialTheme.typography.labelLarge,
                    color = CompanionColors.crimsonContainer,
                )
                Text(
                    text = occurrence?.subjectName ?: "Grupo " + session.groupName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text =
                        session.scheduledStartsAt + " – " +
                            session.scheduledEndsAt + " · " + session.groupName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CompanionColors.onDarkSurfaceVariant,
                )
                occurrence?.let {
                    val location =
                        listOfNotNull(it.classroomName, it.buildingName)
                            .joinToString(" · ")
                            .ifBlank { "Aula por confirmar" }
                    Text(
                        text = location,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CompanionColors.onDarkSurfaceVariant,
                    )
                }

                if (confirmed == null && local == null) {
                    Button(
                        onClick = onConfirm,
                        enabled = !busy && schoolPresenceActive,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Confirmar pase de lista")
                    }
                } else if (confirmed != null) {
                    StatusNotice(
                        title = "Pase enviado",
                        message = classCallRecordMessage(confirmed),
                        tone = NoticeTone.SUCCESS,
                    )
                }
                if (!schoolPresenceActive && confirmed == null) {
                    Text(
                        "Para confirmar el pase, primero registra tu jornada con el QR institucional. " +
                            "Si hay una incidencia, consulta al docente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CompanionColors.onDarkSurfaceVariant,
                    )
                }
                if (confirmed == null && local == null) {
                    TextButton(
                        onClick = { showContingencyDialog = true },
                        enabled = !busy,
                    ) { Text("¿Problemas con el pase? · Contingencia") }
                }
                if (showContingencyDialog) {
                    AlertDialog(
                        onDismissRequest = { showContingencyDialog = false },
                        title = { Text("Contingencia de clase") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm)) {
                                Text("Solo si tu docente te proporcionó un QR o código alternativo. No sustituye su revisión.")
                                TextButton(
                                    onClick = {
                                        showContingencyDialog = false
                                        onScan()
                                    },
                                ) {
                                    Text("Escanear QR del docente")
                                }
                                TextButton(
                                    onClick = {
                                        showContingencyDialog = false
                                        onImage()
                                    },
                                ) {
                                    Text("Elegir imagen del código")
                                }
                                TextButton(
                                    onClick = {
                                        showContingencyDialog = false
                                        onPaste()
                                    },
                                ) {
                                    Text("Pegar código")
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showContingencyDialog = false }) { Text("Cerrar") }
                        },
                    )
                }
            }
        }

        local?.let { localAttendanceStatus(it) }
    }
}

@Composable
private fun schoolDayPresenceCard(
    recorded: Boolean,
    ssid: String?,
    expiresAtEpochSeconds: Long?,
    busy: Boolean,
    onStart: () -> Unit,
) {
    V8GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Jornada escolar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (recorded) "Registrada" else "Pendiente",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (recorded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (recorded) {
                Text(
                    "Tu QR institucional fue reconocido. La presencia física queda sujeta a verificación; " +
                        "cada asistencia a clase requiere confirmación y revisión docente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                expiresAtEpochSeconds?.let {
                    Text(
                        "Vigente hasta " +
                            DateTimeFormatter
                                .ofPattern("HH:mm")
                                .withZone(java.time.ZoneId.systemDefault())
                                .format(Instant.ofEpochSecond(it)),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            } else {
                Text(
                    "Al llegar, conéctate al Wi-Fi escolar y escanea una sola vez el QR institucional.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (ssid != null) {
                    Text("Red detectada: $ssid", style = MaterialTheme.typography.labelMedium)
                }
                Button(
                    onClick = onStart,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                    Text("Escanear QR institucional", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun qrPasteDialog(
    onDismiss: () -> Unit,
    onInspect: (String) -> Unit,
) {
    var token by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pegar código QR") },
        text = {
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("Código / token") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onInspect(token) },
                enabled = token.isNotBlank(),
            ) { Text("Guardar evidencia") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@Composable
private fun qrInspectionCard(
    inspection: AttendanceQrInspectionResponse,
    occurrence: ClassOccurrenceContract?,
    busy: Boolean,
    onDismiss: () -> Unit,
) {
    val valid = inspection.status == AttendanceQrInspectionStatus.VALID
    val session = inspection.session
    StatusNotice(
        title =
            when (inspection.status) {
                AttendanceQrInspectionStatus.VALID -> "QR válido"
                AttendanceQrInspectionStatus.EXPIRED -> "QR expirado"
                AttendanceQrInspectionStatus.WRONG_SESSION -> "QR de otra sesión"
                AttendanceQrInspectionStatus.INVALID -> "QR no válido"
                AttendanceQrInspectionStatus.SESSION_CLOSED -> "El pase ya cerró"
                AttendanceQrInspectionStatus.NOT_ENROLLED -> "No corresponde a tu inscripción"
            },
        message =
            when {
                valid && session != null -> {
                    val subject =
                        occurrence?.subjectName
                            ?: "Grupo " + session.groupName
                    subject + " · " +
                        session.scheduledStartsAt + "–" +
                        session.scheduledEndsAt +
                        ". La evidencia ya quedó guardada; el servidor confirmará el resultado."
                }
                inspection.status == AttendanceQrInspectionStatus.EXPIRED ->
                    "El código fue reconocido, pero su ventana de validez terminó."
                inspection.status == AttendanceQrInspectionStatus.SESSION_CLOSED ->
                    "La sesión de asistencia ya no acepta nuevos registros."
                inspection.status == AttendanceQrInspectionStatus.NOT_ENROLLED ->
                    "El servidor no reconoce esta sesión como parte de tus clases inscritas."
                else ->
                    "No podemos usar este código para registrar asistencia."
            },
        tone = if (valid) NoticeTone.SUCCESS else NoticeTone.WARNING,
    )
    OutlinedButton(
        onClick = onDismiss,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Cerrar")
    }
}

@Composable
private fun localAttendanceStatus(record: LocalAttendanceRecord) {
    val (label, tone) = localStatusLabel(record)
    StatusNotice(
        title = label,
        message =
            when (record.syncState) {
                LocalAttendanceSyncState.PENDING ->
                    "Aún no hay veredicto. La app lo enviará cuando tenga conexión."
                LocalAttendanceSyncState.AUTH_REQUIRED ->
                    "Conservamos el intento local, pero necesitas una sesión válida para sincronizar."
                LocalAttendanceSyncState.SYNCED ->
                    "El estado mostrado ya fue devuelto por el servidor."
                LocalAttendanceSyncState.REVIEW_REQUIRED ->
                    "El servidor recibió el intento y requiere revisión del docente."
                LocalAttendanceSyncState.REJECTED ->
                    "El servidor rechazó el intento. Consulta al docente si necesitas una revisión."
            },
        tone = tone,
    )
}

@Composable
private fun localAttendanceRow(record: LocalAttendanceRecord) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(CompaneroSpacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sesión " + record.sessionId.take(8),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = localStatusLabel(record).first,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (record.syncState == LocalAttendanceSyncState.SYNCED) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Sincronizado",
                    tint =
                        if (MaterialTheme.colorScheme.surfaceContainer.luminance() < 0.5f) {
                            CompanionColors.semanticGreenDark
                        } else {
                            CompanionColors.semanticGreen
                        },
                )
            }
        }
    }
}

@Composable
fun teacherAttendanceContent(
    state: AttendanceUiState,
    onRefresh: () -> Unit,
    onOpen: (ClassOccurrenceContract) -> Unit,
    onClose: () -> Unit,
    onResendNotice: () -> Unit,
    onLoadHistory: () -> Unit,
    onRefreshRoster: () -> Unit,
    onReview: (AttendanceRecordResponse, AttendanceStatus, String) -> Unit,
    onMark: (AttendanceRecordResponse, AttendanceDisposition, String) -> Unit,
    modifier: Modifier,
) {
    key(state.teacherScopeGeneration) {
        teacherAttendanceBody(state, onRefresh, onOpen, onClose, onResendNotice, onLoadHistory, onRefreshRoster, onReview, onMark, modifier)
    }
}

@Composable
private fun teacherAttendanceBody(
    state: AttendanceUiState,
    onRefresh: () -> Unit,
    onOpen: (ClassOccurrenceContract) -> Unit,
    onClose: () -> Unit,
    onResendNotice: () -> Unit,
    onLoadHistory: () -> Unit,
    onRefreshRoster: () -> Unit,
    onReview: (AttendanceRecordResponse, AttendanceStatus, String) -> Unit,
    onMark: (AttendanceRecordResponse, AttendanceDisposition, String) -> Unit,
    modifier: Modifier,
) {
    var filter by remember(state.teacherSession?.id) { mutableStateOf("Recibidos") }
    var confirmClose by remember { mutableStateOf(false) }
    val active = state.teacherSession
    val campus = state.campusRoster
    val records =
        state.roster
            ?.takeIf { it.session.id == active?.id }
            ?.records
            .orEmpty()
    val review = records.filter(::attendanceRecordRequiresReview)
    Box(modifier = modifier.fillMaxSize()) {
        if (!org.companerodeescuela.core.designsystem.v8.LocalV8GlassEnabled.current) {
            V8MatteBackdrop(Modifier.matchParentSize())
        }
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .widthIn(max = CompaneroSize.homeContentMaxWidth)
                    .align(Alignment.TopCenter),
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(CompaneroSpacing.page),
            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
        ) {
            item {
                V8ScreenHeader {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Asistencia",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "Pase de lista · " + (active?.groupName ?: campus?.groupName ?: "Tus grupos"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            state.errorMessage?.let { item { StatusNotice("No pudimos completar la operación", it, tone = NoticeTone.ERROR) } }
            state.successMessage?.let { item { StatusNotice("Listo", it, tone = NoticeTone.SUCCESS) } }
            item {
                OutlinedButton(onClick = onLoadHistory, enabled = !state.actionInProgress, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.teacherHistoryLoaded) "Actualizar historial" else "Consultar historial")
                }
            }
            if (state.teacherHistoryLoaded) {
                if (state.teacherHistory.isEmpty()) {
                    item { StatusNotice("Sin pases anteriores", "No hay sesiones disponibles para tu asignación vigente.") }
                } else {
                    item { Text("Historial reciente", style = MaterialTheme.typography.titleMedium) }
                    items(state.teacherHistory, key = { "history:${it.id}" }) { historical ->
                        V8GlassCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(CompaneroSpacing.sm), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("${historical.groupName} · ${historical.occurrenceDate}", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${historical.scheduledStartsAt}–${historical.scheduledEndsAt} · ${historical.status.name.lowercase()}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
            if (active != null) {
                item {
                    teacherSessionCard(
                        active,
                        state.occurrences.firstOrNull { it.id == active.occurrenceId },
                        state.qr?.token,
                        state.qr?.expiresAtEpochSeconds,
                        state.qrVisualState,
                        state.offlineQrPrepared,
                        state.actionInProgress,
                        onRefreshRoster,
                        onResendNotice,
                        { confirmClose = true },
                    )
                }
            } else {
                item {
                    teacherOccurrenceList(state.occurrences, state.actionInProgress, onOpen)
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("Recibidos", "Por revisar", "Entrada escolar")) { label ->
                        val count =
                            when (label) {
                                "Recibidos" -> if (state.roster != null) records.size.toString() else "—"
                                "Por revisar" -> if (state.roster != null) review.size.toString() else "—"
                                else -> campus?.students?.count { it.campusEntryAtEpochSeconds != null }?.toString() ?: "—"
                            }
                        FilterChip(
                            selected = filter == label,
                            onClick = { filter = label },
                            label = { Text("$label · $count") },
                        )
                    }
                }
            }
            if (filter == "Entrada escolar") {
                item { Text("Entrada escolar", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface) }
                if (campus ==
                    null
                ) {
                    item {
                        StatusNotice(
                            "Padrón no disponible",
                            state.campusRosterError ?: "Selecciona una clase para consultar su padrón autorizado.",
                        )
                    }
                } else {
                    items(campus.students, key = { "campus:" + it.studentId }) { student ->
                        V8GlassCard(Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    student.studentId,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    student.campusEntryAtEpochSeconds?.let {
                                        "Entrada registrada · " +
                                            java.time.Instant
                                                .ofEpochSecond(
                                                    it,
                                                ).atZone(java.time.ZoneId.systemDefault())
                                                .format(DateTimeFormatter.ofPattern("HH:mm"))
                                    }
                                        ?: "Sin entrada registrada",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            } else if (active == null) {
                item {
                    StatusNotice(
                        "Sin pase activo",
                        "Abre un pase vigente para consultar registros de clase. La entrada escolar es independiente.",
                    )
                }
            } else if (state.roster == null) {
                item {
                    StatusNotice(
                        "Registros pendientes",
                        "Todavía no recibimos el padrón de este pase. Actualiza para consultarlo.",
                    )
                }
            } else {
                val shown = if (filter == "Por revisar") review else records
                item {
                    Text(
                        "Lista de estudiantes · ${shown.size}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (shown.isEmpty()) {
                    item {
                        StatusNotice(
                            "Sin registros en este filtro",
                            "El servidor aún no reporta registros para este filtro.",
                        )
                    }
                }
                items(shown, key = AttendanceRecordResponse::id) { record ->
                    rosterRecord(record, state.actionInProgress, state.teacherReviewCompletion, state.errorMessage, onReview, onMark)
                }
                if (filter == "Recibidos" && campus?.sessionId == active.id) {
                    items(campus.students.filter { student -> records.none { it.studentId == student.studentId } }, key = {
                        "missing:" +
                            it.studentId
                    }) { student ->
                        V8GlassCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(student.studentId, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                                Text("Sin registro", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            if (campus != null) {
                item {
                    V8GlassCard(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Entrada escolar · ${campus.groupName}",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            val validated = campus.students.count { it.campusEntryAtEpochSeconds != null }
                            Text(
                                "$validated de ${campus.students.size} con entrada registrada",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { if (campus.students.isEmpty()) 0f else validated.toFloat() / campus.students.size },
                                modifier = Modifier.fillMaxWidth(),
                                color = V8RedColors.Success,
                            )
                            OutlinedButton(
                                onClick = { filter = "Entrada escolar" },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Ver padrón escolar") }
                        }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = onRefresh,
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Actualizar asistencia") }
            }
        }
    }
    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text("¿Cerrar el pase?") },
            text = { Text("El QR dejará de recibir nuevos intentos.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmClose = false
                        onClose()
                    },
                    enabled = !state.actionInProgress,
                ) { Text("Cerrar pase") }
            },
            dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Seguir con el pase") } },
        )
    }
}

@Composable
private fun teacherSessionCard(
    session: AttendanceSessionResponse,
    occurrence: ClassOccurrenceContract?,
    qrToken: String?,
    qrExpiresAt: Long?,
    qrState: QrVisualState,
    offlineQrPrepared: Boolean,
    busy: Boolean,
    onRefreshRoster: () -> Unit,
    onResendNotice: () -> Unit,
    onRequestClose: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(qrExpiresAt) {
        while (true) {
            now = System.currentTimeMillis() / 1000
            delay(1000)
        }
    }
    val qrValid = qrExpiresAt != null && now < qrExpiresAt && now < session.closesAtEpochSeconds
    V8GlassCard(modifier = Modifier.fillMaxWidth(), emphasized = true) {
        Column(
            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (now < session.closesAtEpochSeconds) "PASE EN VIVO" else "PASE FINALIZADO",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = occurrence?.subjectName ?: "Grupo ${session.groupName}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${session.scheduledStartsAt} – ${session.scheduledEndsAt} · ${session.groupName}",
                style = MaterialTheme.typography.bodyLarge,
            )

            when {
                qrState == QrVisualState.ACTIVE && qrToken != null && qrValid -> {
                    AttendanceQrCode(
                        token = qrToken,
                        modifier =
                            Modifier
                                .widthIn(max = 200.dp)
                                .fillMaxWidth(),
                    )
                    qrCountdown(qrExpiresAt)
                    Text(
                        text =
                            if (offlineQrPrepared) {
                                "QR de contingencia preparado: seguirá rotando durante este pase aunque se caiga el Wi-Fi."
                            } else {
                                "El QR cambia automáticamente. Se requiere conexión para preparar la contingencia."
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                qrState == QrVisualState.UNAVAILABLE || !qrValid && qrToken != null -> {
                    Text(
                        text = "QR temporalmente no disponible",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Reconectando. Abre el pase con red para preparar QR de contingencia.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    CircularProgressIndicator()
                    Text(
                        if (qrState == QrVisualState.RENEWING) {
                            "Renovando QR firmado…"
                        } else {
                            "Generando QR firmado…"
                        },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
            ) {
                OutlinedButton(
                    onClick = onResendNotice,
                    enabled = !busy && now < session.closesAtEpochSeconds,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Reenviar aviso")
                }
                OutlinedButton(
                    onClick = onRefreshRoster,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Text(" Registros")
                }
                Button(
                    onClick = onRequestClose,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cerrar pase")
                }
            }
        }
    }
}

@Composable
private fun rosterRecord(
    record: AttendanceRecordResponse,
    busy: Boolean,
    completion: TeacherReviewCompletion?,
    errorMessage: String?,
    onReview: (AttendanceRecordResponse, AttendanceStatus, String) -> Unit,
    onMark: (AttendanceRecordResponse, AttendanceDisposition, String) -> Unit,
) {
    var expanded by remember(record.id) { mutableStateOf(false) }
    var decision by remember(record.id) { mutableStateOf<AttendanceDisposition?>(null) }
    var verify by remember(record.id) { mutableStateOf(false) }
    var reason by remember(record.id) { mutableStateOf("") }
    var submittedVersion by remember(record.id) { mutableStateOf<Long?>(null) }
    // The parent scope key disposes this editor on invalidation. Only its matching
    // successful submission may dismiss it within the same authorized generation.
    LaunchedEffect(completion) {
        val submitted = submittedVersion
        if (submitted != null && completion?.recordId == record.id && completion.version > submitted) {
            decision = null
            verify = false
            reason = ""
            submittedVersion = null
        }
    }
    V8GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(record.studentId, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        java.time.Instant
                            .ofEpochSecond(
                                record.receivedAtEpochSeconds,
                            ).atZone(java.time.ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("HH:mm")),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(attendanceDisplayLabel(record), style = MaterialTheme.typography.labelLarge, color = attendanceDisplayColor(record))
            }
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Ocultar revisión" else "Revisar registro") }
            if (expanded) {
                Text(
                    "Evidencia original: " + attendanceStatusLabel(record.originalStatus ?: record.status) + " · " +
                        (record.originalReasonCode ?: record.reasonCode).name.lowercase().replace('_', ' '),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                record.reviewHistory.lastOrNull()?.let { audit ->
                    Text("Último ajuste: ${audit.reviewerId} · ${audit.note}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalButton(onClick = {
                    decision = AttendanceDisposition.PRESENT
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Marcar presente") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { decision = AttendanceDisposition.LATE },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) { Text("Retardo") }
                    OutlinedButton(
                        onClick = { decision = AttendanceDisposition.ABSENT },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) { Text("Ausente") }
                }
                if (attendanceRecordRequiresReview(record)) {
                    OutlinedButton(
                        onClick = { verify = true },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Validar por excepción docente") }
                }
            }
        }
    }
    if (decision != null || verify) {
        AlertDialog(
            onDismissRequest = {
                if (!busy) {
                    decision = null
                    verify = false
                    reason = ""
                    submittedVersion = null
                }
            },
            title = { Text("Motivo del ajuste") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("El servidor registrará tu identidad, fecha y motivo. La evidencia original se conserva.")
                    OutlinedTextField(value = reason, onValueChange = {
                        reason = it.take(500)
                    }, enabled = !busy, label = { Text("Motivo") }, modifier = Modifier.fillMaxWidth())
                    if (submittedVersion != null && !busy && errorMessage != null) {
                        Text(errorMessage, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    submittedVersion = completion?.version ?: 0
                    if (verify) {
                        onReview(record, AttendanceStatus.VERIFIED, reason.trim())
                    } else {
                        decision?.let { onMark(record, it, reason.trim()) }
                    }
                }, enabled = !busy && reason.trim().length >= 3) { Text("Confirmar ajuste") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        decision = null
                        verify = false
                        reason = ""
                        submittedVersion = null
                    },
                    enabled = !busy,
                ) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun teacherOccurrenceList(
    occurrences: List<ClassOccurrenceContract>,
    busy: Boolean,
    onOpen: (ClassOccurrenceContract) -> Unit,
) {
    val today = LocalDate.now().toString()
    val todayOccurrences = occurrences.filter { it.date == today }
    val visible = if (todayOccurrences.isNotEmpty()) todayOccurrences else occurrences
    val now by produceState(initialValue = LocalDateTime.now()) {
        while (true) {
            delay(60_000L)
            value = LocalDateTime.now()
        }
    }

    Text(
        text = if (todayOccurrences.isNotEmpty()) "Clases de hoy" else "Clases de la semana",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )

    if (visible.isEmpty()) {
        StatusNotice(
            title = "Sin clases disponibles",
            message = "No encontramos ocurrencias académicas para abrir asistencia esta semana.",
        )
        return
    }

    visible.forEach { occurrence ->
        val availability = teacherPassOpenStatus(occurrence, now)
        V8GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(CompaneroSpacing.md),
                verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
            ) {
                Text(
                    text = occurrence.subjectName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${dateLabel(occurrence.date)} · ${occurrence.startsAt} – ${occurrence.endsAt}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text =
                        listOfNotNull(occurrence.classroomName, occurrence.buildingName)
                            .joinToString(" · ")
                            .ifBlank { "Aula por confirmar" },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = { onOpen(occurrence) },
                    enabled = !busy && availability == TeacherPassOpenStatus.AVAILABLE,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        when (availability) {
                            TeacherPassOpenStatus.AVAILABLE -> "Abrir pase"
                            TeacherPassOpenStatus.ENDED -> "Clase finalizada"
                            TeacherPassOpenStatus.CANCELLED -> "Clase cancelada"
                            TeacherPassOpenStatus.INVALID_SCHEDULE -> "Revisar horario"
                        },
                    )
                }
                if (availability != TeacherPassOpenStatus.AVAILABLE) {
                    Text(
                        teacherPassUnavailableMessage(availability),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun attendanceHeader(
    title: String,
    subtitle: String,
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xxs),
    ) {
        Text(
            text = title,
            modifier =
                Modifier.padding(
                    end = CompaneroSpacing.hero + CompaneroSpacing.sm,
                ),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = onRefresh,
            enabled = !loading,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(if (loading) "…" else "Actualizar", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun qrCountdown(expiresAtEpochSeconds: Long?) {
    if (expiresAtEpochSeconds == null) return
    var now by remember { mutableLongStateOf(Instant.now().epochSecond) }
    LaunchedEffect(expiresAtEpochSeconds) {
        while (true) {
            now = Instant.now().epochSecond
            delay(1_000)
        }
    }
    val remaining = (expiresAtEpochSeconds - now).coerceAtLeast(0)
    Text(
        text =
            if (remaining > 0) {
                "QR válido por $remaining s"
            } else {
                "Renovando QR…"
            },
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun loadingAttendance(modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        V8ScreenHeader { Text("Asistencia", style = MaterialTheme.typography.headlineSmall) }
        CircularProgressIndicator()
        Text(
            text = "Preparando asistencia…",
            modifier = Modifier.padding(top = CompaneroSpacing.sm),
        )
    }
}

@Composable
private fun unsupportedAttendance(
    message: String?,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(CompaneroSpacing.lg),
        verticalArrangement = Arrangement.Center,
    ) {
        V8ScreenHeader { Text("Asistencia", style = MaterialTheme.typography.headlineSmall) }
        StatusNotice(
            title = "Asistencia no disponible",
            message = message ?: "Tu rol actual no tiene un flujo de asistencia en esta versión.",
            tone = NoticeTone.WARNING,
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.padding(top = CompaneroSpacing.sm),
        ) {
            Text("Reintentar")
        }
    }
}

private fun localStatusLabel(record: LocalAttendanceRecord): Pair<String, NoticeTone> =
    when (attendanceClientVerdict(record)) {
        AttendanceClientVerdict.PENDING ->
            "Pendiente de sincronizar" to NoticeTone.WARNING
        AttendanceClientVerdict.AUTH_REQUIRED ->
            "Guardado · requiere sesión" to NoticeTone.WARNING
        AttendanceClientVerdict.SERVER_RECEIVED ->
            "Recibida por el servidor" to NoticeTone.SUCCESS
        AttendanceClientVerdict.REVIEW_REQUIRED ->
            "En revisión por el docente" to NoticeTone.WARNING
        AttendanceClientVerdict.SERVER_REJECTED ->
            "Rechazada por el servidor" to NoticeTone.ERROR
    }

private fun attendanceDisplayLabel(record: AttendanceRecordResponse): String =
    if (attendanceRecordRequiresReview(record)) {
        "En revisión"
    } else {
        when (record.disposition) {
            AttendanceDisposition.PRESENT -> "Presente"
            AttendanceDisposition.LATE -> "Retardo"
            AttendanceDisposition.ABSENT -> "Ausente"
            null -> attendanceStatusLabel(record.status)
        }
    }

@Composable
private fun attendanceDisplayColor(record: AttendanceRecordResponse) =
    if (attendanceRecordRequiresReview(record)) {
        MaterialTheme.colorScheme.tertiary
    } else {
        when (record.disposition) {
            AttendanceDisposition.PRESENT ->
                if (
                    MaterialTheme.colorScheme.surface.luminance() < 0.5f
                ) {
                    CompanionColors.semanticGreenDark
                } else {
                    CompanionColors.semanticGreen
                }
            AttendanceDisposition.LATE -> MaterialTheme.colorScheme.tertiary
            AttendanceDisposition.ABSENT -> MaterialTheme.colorScheme.error
            null ->
                when (record.status) {
                    AttendanceStatus.VERIFIED ->
                        if (
                            MaterialTheme.colorScheme.surface.luminance() < 0.5f
                        ) {
                            CompanionColors.semanticGreenDark
                        } else {
                            CompanionColors.semanticGreen
                        }
                    AttendanceStatus.REJECTED -> MaterialTheme.colorScheme.error
                    AttendanceStatus.REVIEW_REQUIRED -> MaterialTheme.colorScheme.tertiary
                    AttendanceStatus.LIKELY ->
                        if (
                            MaterialTheme.colorScheme.surface.luminance() < 0.5f
                        ) {
                            CompanionColors.semanticBlueDark
                        } else {
                            CompanionColors.semanticBlue
                        }
                }
        }
    }

private fun attendanceStatusLabel(status: AttendanceStatus): String =
    when (status) {
        AttendanceStatus.VERIFIED -> "Verificada"
        AttendanceStatus.LIKELY -> "Probable"
        AttendanceStatus.REVIEW_REQUIRED -> "En revisión"
        AttendanceStatus.REJECTED -> "Rechazada"
    }

private fun dateLabel(raw: String): String {
    val date = runCatching { LocalDate.parse(raw) }.getOrNull() ?: return raw
    val formatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale("es", "MX"))
    return date.format(formatter).replaceFirstChar { it.uppercase() }
}
