package org.companerodeescuela.feature.attendance

import org.companerodeescuela.core.designsystem.v8.V8ScreenHeader
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.FilterChip
import org.companerodeescuela.shared.contracts.TeacherClassContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.companerodeescuela.core.database.LocalAttendanceRecord
import org.companerodeescuela.core.database.LocalAttendanceSyncState
import org.companerodeescuela.core.designsystem.theme.CompanionColors
import org.companerodeescuela.core.designsystem.v8.V8CampusBackdrop
import org.companerodeescuela.core.designsystem.v8.V8GlassCard
import org.companerodeescuela.core.designsystem.v8.V8AttendanceEvidence
import org.companerodeescuela.core.designsystem.v8.V8RedColors
import org.companerodeescuela.core.designsystem.v8.V8RedPrimaryButton
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.designsystem.theme.CompaneroWindowBreakpoints
import org.companerodeescuela.core.ui.component.CompaneroHeroSurface
import org.companerodeescuela.core.ui.component.CompaneroSurface
import org.companerodeescuela.core.ui.component.CompaneroSurfaceRole
import org.companerodeescuela.core.ui.component.NoticeTone
import org.companerodeescuela.core.ui.component.StatusNotice
import org.companerodeescuela.shared.contracts.AttendanceDisposition
import org.companerodeescuela.shared.contracts.AttendanceQrInspectionResponse
import org.companerodeescuela.shared.contracts.AttendanceQrInspectionStatus
import org.companerodeescuela.shared.contracts.AttendanceRecordResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse
import org.companerodeescuela.shared.contracts.AttendanceStatus
import org.companerodeescuela.shared.contracts.ClassOccurrenceContract

@Composable
fun AttendanceScreen(
    modifier: Modifier = Modifier,
    requestedMode: AttendanceMode? = null,
    requestedClassroom: TeacherClassContext? = null,
    viewModel: AttendanceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(requestedMode, requestedClassroom) {
        viewModel.setClassroomContext(requestedClassroom)
        requestedMode?.let(viewModel::selectMode)
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imageReader = remember(context) { AttendanceQrImageReader(context) }
    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { imageReader.read(uri) }
                .onSuccess { token ->
                    if (token.isNullOrBlank()) viewModel.inspectQr("")
                    else viewModel.inspectQr(token)
                }
                .onFailure { viewModel.inspectQr("") }
        }
    }

    if (state.scannerOpen) {
        Dialog(
            onDismissRequest = viewModel::dismissScanner,
            properties = DialogProperties(
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
        AttendanceMode.LOADING -> LoadingAttendance(modifier)
        AttendanceMode.STUDENT -> StudentAttendance(
            state = state,
            onRefresh = viewModel::refresh,
            onScan = viewModel::openScanner,
            onGenericScan = viewModel::openGenericScanner,
            onStartSchoolDay = viewModel::openSchoolDayScanner,
            onConfirmClassCall = viewModel::confirmClassCall,
            onPickImage = { imageLauncher.launch(arrayOf("image/*")) },
            onInspectToken = viewModel::inspectQr,
            onDismissInspection = viewModel::clearQrInspection,
            modifier = modifier,
        )
        AttendanceMode.TEACHER -> TeacherAttendance(
            state = state,
            onRefresh = viewModel::refresh,
            onOpen = viewModel::openAttendance,
            onClose = viewModel::closeTeacherSession,
            onRefreshRoster = viewModel::refreshRoster,
            onReview = viewModel::reviewRecord,
            onMark = viewModel::markRecord,
            modifier = modifier,
        )
        AttendanceMode.UNSUPPORTED -> UnsupportedAttendance(
            message = state.errorMessage,
            onRetry = viewModel::refresh,
            modifier = modifier,
        )
    }
}

@Composable
private fun StudentAttendance(
    state: AttendanceUiState,
    onRefresh: () -> Unit,
    onScan: (String?) -> Unit,
    onGenericScan: () -> Unit,
    onStartSchoolDay: () -> Unit,
    onConfirmClassCall: (String) -> Unit,
    onPickImage: () -> Unit,
    onInspectToken: (String) -> Unit,
    onDismissInspection: () -> Unit,
    modifier: Modifier,
) {
    val occurrenceById = remember(state.occurrences) {
        state.occurrences.associateBy { it.id }
    }
    var showPasteDialog by remember { mutableStateOf(false) }
    // A monotonic interval advances the server timestamp even if the device wall clock changes.
    var monotonicNow by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            monotonicNow = android.os.SystemClock.elapsedRealtime()
        }
    }
    val schoolVerified = state.schoolPresenceReceivedRealtime?.let {
        schoolDayVerifiedAtServerTime(state.schoolPresence, (monotonicNow - it) / 1_000)
    } ?: false

    Box(modifier = modifier.fillMaxSize()) {
        if (!org.companerodeescuela.core.designsystem.v8.LocalV8GlassEnabled.current) {
            V8CampusBackdrop(modifier = Modifier.matchParentSize())
        }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = CompaneroSize.homeContentMaxWidth)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CompaneroSpacing.page, vertical = CompaneroSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.section),
    ) {
        V8ScreenHeader {
            org.companerodeescuela.core.designsystem.v8.V8HeroTitle("Pase de", "lista")
        }
        V8AttendanceEvidence(
            schoolNetworkVerified = schoolVerified,
            locationVerified = null,
            ready = schoolVerified && state.activeSessions.isNotEmpty(),
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

        QrCenterCard(
            onScan = onGenericScan,
            onImage = onPickImage,
            onPaste = { showPasteDialog = true },
        )

        SchoolDayPresenceCard(
            active = schoolVerified,
            ssid = state.schoolNetworkSsid,
            expiresAtEpochSeconds = state.schoolPresence?.expiresAtEpochSeconds,
            busy = state.actionInProgress,
            onStart = onStartSchoolDay,
        )

        state.qrInspection?.let { inspection ->
            QrInspectionCard(
                inspection = inspection,
                occurrence = inspection.session?.occurrenceId?.let(occurrenceById::get),
                busy = state.actionInProgress,
                onDismiss = onDismissInspection,
            )
        }

        if (!state.loading && state.activeSessions.isEmpty()) {
            StatusNotice(
                title = "Sin sesión de clase activa",
                message = "Puedes iniciar la jornada escolar con el QR institucional. El pase de clase aparecerá cuando lo abra tu docente.",
            )
        }

        state.activeSessions.forEach { session ->
            val occurrence = occurrenceById[session.occurrenceId]
            val local = state.localRecords
                .filter { it.sessionId == session.id }
                .maxByOrNull { it.updatedAtEpochSeconds }

            StudentSessionCard(
                session = session,
                occurrence = occurrence,
                local = local,
                onScan = { onScan(session.id) },
                onConfirm = { onConfirmClassCall(session.id) },
                confirmed = state.confirmedClassCalls[session.id],
                busy = state.actionInProgress,
                schoolPresenceActive = schoolVerified,
            )
        }

        if (state.localRecords.isNotEmpty()) {
            Text(
                text = "Actividad reciente",
                style = MaterialTheme.typography.titleMedium,
            )
            state.localRecords.take(5).forEach { record ->
                LocalAttendanceRow(record)
            }
        }
    }

    }

    if (showPasteDialog) {
        QrPasteDialog(
            onDismiss = { showPasteDialog = false },
            onInspect = {
                showPasteDialog = false
                onInspectToken(it)
            },
        )
    }
}

@Composable
private fun StudentSessionCard(
    session: AttendanceSessionResponse,
    occurrence: ClassOccurrenceContract?,
    local: LocalAttendanceRecord?,
    onScan: () -> Unit,
    onConfirm: () -> Unit,
    confirmed: AttendanceRecordResponse?,
    busy: Boolean,
    schoolPresenceActive: Boolean,
) {
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
                    text = session.scheduledStartsAt + " – " +
                        session.scheduledEndsAt + " · " + session.groupName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CompanionColors.onDarkSurfaceVariant,
                )
                occurrence?.let {
                    val location = listOfNotNull(it.classroomName, it.buildingName)
                        .joinToString(" · ")
                        .ifBlank { "Aula por confirmar" }
                    Text(
                        text = location,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CompanionColors.onDarkSurfaceVariant,
                    )
                }

                Button(
                    onClick = onConfirm,
                    enabled = !busy && confirmed == null && local == null && schoolPresenceActive,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (confirmed == null) "Confirmar pase desde la app" else
                        if (confirmed.disposition == AttendanceDisposition.LATE) "Retardo confirmado" else "Asistencia confirmada")
                }
                if (!schoolPresenceActive) {
                    Text("Primero registra tu entrada escolar con el QR institucional.",
                        style = MaterialTheme.typography.bodySmall)
                }

                OutlinedButton(
                    onClick = onScan,
                    enabled = !busy && local == null && confirmed == null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.QrCodeScanner,
                        contentDescription = null,
                    )
                    Text(
                        text = if (local == null) {
                            " Comprobar QR"
                        } else {
                            " Pase ya registrado en este dispositivo"
                        },
                    )
                }
            }
        }

        local?.let { LocalAttendanceStatus(it) }
    }
}

@Composable
private fun SchoolDayPresenceCard(
    active: Boolean,
    ssid: String?,
    expiresAtEpochSeconds: Long?,
    busy: Boolean,
    onStart: () -> Unit,
) {
    CompaneroSurface(
        modifier = Modifier.fillMaxWidth(),
        role = CompaneroSurfaceRole.CARD,
    ) {
        Column(
            modifier = Modifier.padding(CompaneroSpacing.md),
            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
        ) {
            Text(
                text = "Presencia en la escuela",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (active) {
                StatusNotice(
                    title = "Jornada iniciada",
                    message = buildString {
                        append("QR institucional validado")
                        ssid?.let { append(" · Wi-Fi: ").append(it) }
                        expiresAtEpochSeconds?.let {
                            append(" · válida hasta ")
                            append(
                                DateTimeFormatter.ofPattern("HH:mm")
                                    .withZone(java.time.ZoneId.systemDefault())
                                    .format(Instant.ofEpochSecond(it)),
                            )
                        }
                    },
                    tone = NoticeTone.SUCCESS,
                )
            } else {
                Text(
                    text = "Conéctate al Wi-Fi de la escuela y escanea el QR institucional una vez al iniciar tu jornada.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (ssid != null) {
                    Text(
                        text = "Red detectada: $ssid",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Button(
                    onClick = onStart,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                    Text(" Iniciar jornada")
                }
            }
        }
    }
}

@Composable
private fun QrCenterCard(
    onScan: () -> Unit,
    onImage: () -> Unit,
    onPaste: () -> Unit,
) {
    V8GlassCard(modifier = Modifier.fillMaxWidth(), emphasized = true) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(124.dp).align(Alignment.CenterHorizontally)
                .then(Modifier.drawBehind {
                    val length = 20.dp.toPx(); val inset = 4.dp.toPx(); val stroke = 3.dp.toPx()
                    for (x in listOf(inset, size.width - inset)) for (y in listOf(inset, size.height - inset)) {
                        val sx = if (x == inset) 1 else -1; val sy = if (y == inset) 1 else -1
                        drawLine(V8RedColors.Crimson, androidx.compose.ui.geometry.Offset(x,y), androidx.compose.ui.geometry.Offset(x + sx * length,y), stroke)
                        drawLine(V8RedColors.Crimson, androidx.compose.ui.geometry.Offset(x,y), androidx.compose.ui.geometry.Offset(x,y + sy * length), stroke)
                    }
                }), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = V8RedColors.TextPrimary, modifier = Modifier.size(78.dp))
            }
            Text(
                "Escanea el código QR",
                style = MaterialTheme.typography.titleLarge,
                color = V8RedColors.TextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Apunta la cámara al código de la escuela o de tu clase.",
                style = MaterialTheme.typography.bodyMedium,
                color = V8RedColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            BoxWithConstraints {
                if (maxWidth >= 300.dp && androidx.compose.ui.platform.LocalDensity.current.fontScale <= 1.2f) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        org.companerodeescuela.core.designsystem.v8.V8RedPrimaryButton("Escanear código QR", onScan, Modifier.weight(1.8f))
                        OutlinedButton(onClick = onImage, modifier = Modifier.weight(1f)) { Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Image, contentDescription = null); Text("Desde galería")
                        } }
                    }
                } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    org.companerodeescuela.core.designsystem.v8.V8RedPrimaryButton("Escanear código QR", onScan, Modifier.fillMaxWidth())
                    OutlinedButton(onClick = onImage, modifier = Modifier.fillMaxWidth()) { Text("Desde galería") }
                }
            }
            TextButton(onClick = onPaste, modifier = Modifier.fillMaxWidth()) {
                Text("Pegar código")
            }
        }
    }
}

@Composable
private fun QrPasteDialog(
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
private fun QrInspectionCard(
    inspection: AttendanceQrInspectionResponse,
    occurrence: ClassOccurrenceContract?,
    busy: Boolean,
    onDismiss: () -> Unit,
) {
    val valid = inspection.status == AttendanceQrInspectionStatus.VALID
    val session = inspection.session
    StatusNotice(
        title = when (inspection.status) {
            AttendanceQrInspectionStatus.VALID -> "QR válido"
            AttendanceQrInspectionStatus.EXPIRED -> "QR expirado"
            AttendanceQrInspectionStatus.WRONG_SESSION -> "QR de otra sesión"
            AttendanceQrInspectionStatus.INVALID -> "QR no válido"
            AttendanceQrInspectionStatus.SESSION_CLOSED -> "El pase ya cerró"
            AttendanceQrInspectionStatus.NOT_ENROLLED -> "No corresponde a tu inscripción"
        },
        message = when {
            valid && session != null -> {
                val subject = occurrence?.subjectName
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
private fun LocalAttendanceStatus(record: LocalAttendanceRecord) {
    val (label, tone) = localStatusLabel(record)
    StatusNotice(
        title = label,
        message = when (record.syncState) {
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
private fun LocalAttendanceRow(record: LocalAttendanceRecord) {
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
                    tint = if (MaterialTheme.colorScheme.surfaceContainer.luminance() < 0.5f) {
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
private fun TeacherAttendance(
    state: AttendanceUiState,
    onRefresh: () -> Unit,
    onOpen: (ClassOccurrenceContract) -> Unit,
    onClose: () -> Unit,
    onRefreshRoster: () -> Unit,
    onReview: (AttendanceRecordResponse, AttendanceStatus, String) -> Unit,
    onMark: (AttendanceRecordResponse, AttendanceDisposition, String) -> Unit,
    modifier: Modifier,
) {
    var filter by remember(state.teacherSession?.id) { mutableStateOf("Recibidos") }
    var confirmClose by remember { mutableStateOf(false) }
    val active = state.teacherSession
    val campus = state.campusRoster
    val records = state.roster?.takeIf { it.session.id == active?.id }?.records.orEmpty()
    val review = records.filter { it.status == AttendanceStatus.REVIEW_REQUIRED }
    Box(modifier = modifier.fillMaxSize()) {
        if (!org.companerodeescuela.core.designsystem.v8.LocalV8GlassEnabled.current) {
            V8CampusBackdrop(Modifier.matchParentSize())
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().widthIn(max = CompaneroSize.homeContentMaxWidth)
                .align(Alignment.TopCenter),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(CompaneroSpacing.page),
            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
        ) {
            item {
                V8ScreenHeader {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Asistencia", style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold, color = V8RedColors.TextPrimary)
                        Text("Pase de lista · " + (active?.groupName ?: campus?.groupName ?: "Tus grupos"), color = V8RedColors.TextSecondary)
                    }
                }
            }
            state.errorMessage?.let { item { StatusNotice("No pudimos actualizar", it, tone = NoticeTone.ERROR) } }
            state.successMessage?.let { item { StatusNotice("Listo", it, tone = NoticeTone.SUCCESS) } }
            if (active != null) item {
                TeacherSessionCard(active, state.occurrences.firstOrNull { it.id == active.occurrenceId },
                    state.qr?.token, state.qr?.expiresAtEpochSeconds, state.qrVisualState,
                    state.actionInProgress, onRefreshRoster, { confirmClose = true })
            } else item {
                TeacherOccurrenceList(state.occurrences, state.actionInProgress, onOpen)
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("Recibidos", "Por revisar", "Entrada escolar")) { label ->
                        val count = when (label) {
                            "Recibidos" -> if (state.roster != null) records.size.toString() else "—"
                            "Por revisar" -> if (state.roster != null) review.size.toString() else "—"
                            else -> campus?.students?.count { it.campusEntryAtEpochSeconds != null }?.toString() ?: "—"
                        }
                        FilterChip(selected = filter == label, onClick = { filter = label },
                            label = { Text("$label · $count") })
                    }
                }
            }
            if (filter == "Entrada escolar") {
                item { Text("Entrada escolar", style = MaterialTheme.typography.titleLarge, color = V8RedColors.TextPrimary) }
                if (campus == null) item { StatusNotice("Padrón no disponible", state.campusRosterError ?: "Selecciona una clase para consultar su padrón autorizado.") }
                else items(campus.students, key = { "campus:" + it.studentId }) { student ->
                    V8GlassCard(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(student.studentId, color = V8RedColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
                            Text(student.campusEntryAtEpochSeconds?.let { "Entrada registrada · " + java.time.Instant.ofEpochSecond(it).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")) }
                                ?: "Sin entrada registrada", color = V8RedColors.TextSecondary)
                        }
                    }
                }
            } else {
                val shown = if (filter == "Por revisar") review else records
                item { Text("Lista de estudiantes · ${shown.size}", style = MaterialTheme.typography.titleLarge, color = V8RedColors.TextPrimary) }
                if (shown.isEmpty()) item { StatusNotice("Sin registros en este filtro", "Los registros aparecen al recibirse del servidor. Actualiza para volver a consultar.") }
                items(shown, key = AttendanceRecordResponse::id) { record ->
                    RosterRecord(record, state.actionInProgress, onReview, onMark)
                }
                if (filter == "Recibidos" && active != null && campus?.sessionId == active.id) {
                    items(campus.students.filter { student -> records.none { it.studentId == student.studentId } }, key = { "missing:" + it.studentId }) { student ->
                        V8GlassCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(student.studentId, Modifier.weight(1f), color = V8RedColors.TextPrimary)
                                Text("Sin registro", color = V8RedColors.TextSecondary)
                            }
                        }
                    }
                }
            }
            if (campus != null) item {
                V8GlassCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Entrada escolar · ${campus.groupName}", color = V8RedColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
                        val validated = campus.students.count { it.campusEntryAtEpochSeconds != null }
                        Text("$validated de ${campus.students.size} con entrada registrada", color = V8RedColors.TextSecondary)
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { if (campus.students.isEmpty()) 0f else validated.toFloat() / campus.students.size },
                            modifier = Modifier.fillMaxWidth(), color = V8RedColors.Success)
                        OutlinedButton(onClick = { filter = "Entrada escolar" }, modifier = Modifier.fillMaxWidth()) { Text("Ver padrón escolar") }
                    }
                }
            }
            item { OutlinedButton(onClick = onRefresh, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) { Text("Actualizar asistencia") } }
        }
    }
    if (confirmClose) AlertDialog(onDismissRequest = { confirmClose = false },
        title = { Text("¿Cerrar el pase?") }, text = { Text("El QR dejará de recibir nuevos intentos.") },
        confirmButton = { Button(onClick = { confirmClose = false; onClose() }, enabled = !state.actionInProgress) { Text("Cerrar pase") } },
        dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Seguir con el pase") } })
}

@Composable
private fun TeacherSessionCard(
    session: AttendanceSessionResponse,
    occurrence: ClassOccurrenceContract?,
    qrToken: String?,
    qrExpiresAt: Long?,
    qrState: QrVisualState,
    busy: Boolean,
    onRefreshRoster: () -> Unit,
    onRequestClose: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(qrExpiresAt) { while (true) { now = System.currentTimeMillis() / 1000; delay(1000) } }
    val qrValid = qrExpiresAt != null && now < qrExpiresAt && now < session.closesAtEpochSeconds
    V8GlassCard(modifier = Modifier.fillMaxWidth(), emphasized = true) {
        Column(
            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (now < session.closesAtEpochSeconds) "PASE EN VIVO" else "PASE FINALIZADO",
                style = MaterialTheme.typography.labelLarge,
                color = V8RedColors.Crimson,
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
                        modifier = Modifier
                            .widthIn(max = 200.dp)
                            .fillMaxWidth(),
                    )
                    QrCountdown(qrExpiresAt)
                    Text(
                        text = "El QR cambia automáticamente. No contiene datos del alumno.",
                        style = MaterialTheme.typography.bodySmall,
                        color = V8RedColors.TextSecondary,
                    )
                }
                qrState == QrVisualState.UNAVAILABLE || !qrValid && qrToken != null -> {
                    Text(
                        text = "QR temporalmente no disponible",
                        style = MaterialTheme.typography.titleMedium,
                    color = V8RedColors.TextPrimary,
                    )
                    Text(
                        text = "Reconectando automáticamente. No uses un código anterior.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = V8RedColors.TextSecondary,
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
private fun RosterRecord(
    record: AttendanceRecordResponse,
    busy: Boolean,
    onReview: (AttendanceRecordResponse, AttendanceStatus, String) -> Unit,
    onMark: (AttendanceRecordResponse, AttendanceDisposition, String) -> Unit,
) {
    var expanded by remember(record.id) { mutableStateOf(false) }
    var decision by remember(record.id) { mutableStateOf<AttendanceDisposition?>(null) }
    var verify by remember(record.id) { mutableStateOf(false) }
    var reason by remember(record.id) { mutableStateOf("") }
    V8GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(record.studentId, style = MaterialTheme.typography.titleMedium, color = V8RedColors.TextPrimary)
                    Text(java.time.Instant.ofEpochSecond(record.receivedAtEpochSeconds).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")), color = V8RedColors.TextSecondary)
                }
                Text(attendanceDisplayLabel(record), style = MaterialTheme.typography.labelLarge, color = attendanceDisplayColor(record))
            }
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Ocultar revisión" else "Revisar registro") }
            if (expanded) {
                Text("Evidencia original: " + attendanceStatusLabel(record.originalStatus ?: record.status) + " · " + (record.originalReasonCode ?: record.reasonCode).name.lowercase().replace('_', ' '), color = V8RedColors.TextSecondary)
                record.reviewHistory.lastOrNull()?.let { audit ->
                    Text("Último ajuste: ${audit.reviewerId} · ${audit.note}", color = V8RedColors.TextSecondary)
                }
                FilledTonalButton(onClick = { decision = AttendanceDisposition.PRESENT }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Marcar presente") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { decision = AttendanceDisposition.LATE }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Retardo") }
                    OutlinedButton(onClick = { decision = AttendanceDisposition.ABSENT }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Ausente") }
                }
                if (record.status == AttendanceStatus.REVIEW_REQUIRED) OutlinedButton(onClick = { verify = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Validar por excepción docente") }
            }
        }
    }
    if (decision != null || verify) AlertDialog(
        onDismissRequest = { decision = null; verify = false; reason = "" },
        title = { Text("Motivo del ajuste") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("El servidor registrará tu identidad, fecha y motivo. La evidencia original se conserva.")
            OutlinedTextField(value = reason, onValueChange = { reason = it.take(500) }, label = { Text("Motivo") }, modifier = Modifier.fillMaxWidth())
        } },
        confirmButton = { Button(onClick = {
            if (verify) onReview(record, AttendanceStatus.VERIFIED, reason.trim())
            else decision?.let { onMark(record, it, reason.trim()) }
            decision = null; verify = false; reason = ""
        }, enabled = !busy && reason.trim().length >= 3) { Text("Confirmar ajuste") } },
        dismissButton = { TextButton(onClick = { decision = null; verify = false; reason = "" }) { Text("Cancelar") } },
    )
}

@Composable
private fun TeacherOccurrenceList(
    occurrences: List<ClassOccurrenceContract>,
    busy: Boolean,
    onOpen: (ClassOccurrenceContract) -> Unit,
) {
    val today = LocalDate.now().toString()
    val todayOccurrences = occurrences.filter { it.date == today }
    val visible = if (todayOccurrences.isNotEmpty()) todayOccurrences else occurrences

    Text(
        text = if (todayOccurrences.isNotEmpty()) "Clases de hoy" else "Clases de la semana",
        style = MaterialTheme.typography.titleMedium,
                    color = V8RedColors.TextPrimary,
    )

    if (visible.isEmpty()) {
        StatusNotice(
            title = "Sin clases disponibles",
            message = "No encontramos ocurrencias académicas para abrir asistencia esta semana.",
        )
        return
    }

    visible.forEach { occurrence ->
        V8GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(CompaneroSpacing.md),
                verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
            ) {
                Text(
                    text = occurrence.subjectName,
                    style = MaterialTheme.typography.titleMedium,
                    color = V8RedColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${dateLabel(occurrence.date)} · ${occurrence.startsAt} – ${occurrence.endsAt}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = V8RedColors.TextSecondary,
                )
                Text(
                    text = listOfNotNull(occurrence.classroomName, occurrence.buildingName)
                        .joinToString(" · ")
                        .ifBlank { "Aula por confirmar" },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = { onOpen(occurrence) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Abrir pase")
                }
            }
        }
    }
}

@Composable
private fun AttendanceHeader(
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
            modifier = Modifier.padding(
                end = CompaneroSpacing.hero + CompaneroSpacing.sm,
            ),
            style = MaterialTheme.typography.headlineLarge,
            color = V8RedColors.TextPrimary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = V8RedColors.TextSecondary,
        )
        TextButton(
            onClick = onRefresh,
            enabled = !loading,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(if (loading) "…" else "Actualizar", color = V8RedColors.Crimson)
        }
    }
}

@Composable
private fun QrCountdown(expiresAtEpochSeconds: Long?) {
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
        text = if (remaining > 0) {
            "QR válido por $remaining s"
        } else {
            "Renovando QR…"
        },
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun LoadingAttendance(modifier: Modifier) {
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
private fun UnsupportedAttendance(
    message: String?,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
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

private fun localStatusLabel(
    record: LocalAttendanceRecord,
): Pair<String, NoticeTone> = when (attendanceClientVerdict(record)) {
    AttendanceClientVerdict.PENDING ->
        "Pendiente de sincronizar" to NoticeTone.WARNING
    AttendanceClientVerdict.AUTH_REQUIRED ->
        "Guardado · requiere sesión" to NoticeTone.WARNING
    AttendanceClientVerdict.SERVER_RECEIVED ->
        "Recibida por el servidor" to NoticeTone.SUCCESS
    AttendanceClientVerdict.SERVER_VERIFIED ->
        "Verificada por el servidor" to NoticeTone.SUCCESS
    AttendanceClientVerdict.REVIEW_REQUIRED ->
        "En revisión por el docente" to NoticeTone.WARNING
    AttendanceClientVerdict.SERVER_REJECTED ->
        "Rechazada por el servidor" to NoticeTone.ERROR
}

private fun attendanceDisplayLabel(record: AttendanceRecordResponse): String =
    when (record.disposition) {
        AttendanceDisposition.PRESENT -> "Presente"
        AttendanceDisposition.LATE -> "Retardo"
        AttendanceDisposition.ABSENT -> "Ausente"
        null -> attendanceStatusLabel(record.status)
    }

@Composable
private fun attendanceDisplayColor(record: AttendanceRecordResponse) =
    when (record.disposition) {
        AttendanceDisposition.PRESENT -> if (
            MaterialTheme.colorScheme.surface.luminance() < 0.5f
        ) {
            CompanionColors.semanticGreenDark
        } else {
            CompanionColors.semanticGreen
        }
        AttendanceDisposition.LATE -> MaterialTheme.colorScheme.tertiary
        AttendanceDisposition.ABSENT -> MaterialTheme.colorScheme.error
        null -> when (record.status) {
            AttendanceStatus.VERIFIED -> if (
                MaterialTheme.colorScheme.surface.luminance() < 0.5f
            ) {
                CompanionColors.semanticGreenDark
            } else {
                CompanionColors.semanticGreen
            }
            AttendanceStatus.REJECTED -> MaterialTheme.colorScheme.error
            AttendanceStatus.REVIEW_REQUIRED -> MaterialTheme.colorScheme.tertiary
            AttendanceStatus.LIKELY -> if (
                MaterialTheme.colorScheme.surface.luminance() < 0.5f
            ) {
                CompanionColors.semanticBlueDark
            } else {
                CompanionColors.semanticBlue
            }
        }
    }

private fun attendanceStatusLabel(status: AttendanceStatus): String = when (status) {
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
