package org.companerodeescuela.feature.attendance

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.designsystem.theme.CompaneroWindowBreakpoints
import org.companerodeescuela.core.ui.component.NoticeTone
import org.companerodeescuela.core.ui.component.StatusNotice
import org.companerodeescuela.shared.contracts.AttendanceQrInspectionResponse
import org.companerodeescuela.shared.contracts.AttendanceQrInspectionStatus
import org.companerodeescuela.shared.contracts.AttendanceRecordResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse
import org.companerodeescuela.shared.contracts.AttendanceStatus
import org.companerodeescuela.shared.contracts.ClassOccurrenceContract

@Composable
fun AttendanceScreen(
    modifier: Modifier = Modifier,
    viewModel: AttendanceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
            onPickImage = { imageLauncher.launch(arrayOf("image/*")) },
            onInspectToken = viewModel::inspectQr,
            onRegister = viewModel::submitInspectedQr,
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
    onPickImage: () -> Unit,
    onInspectToken: (String) -> Unit,
    onRegister: () -> Unit,
    onDismissInspection: () -> Unit,
    modifier: Modifier,
) {
    val occurrenceById = remember(state.occurrences) {
        state.occurrences.associateBy { it.id }
    }
    var showPasteDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CompaneroSpacing.lg, vertical = CompaneroSpacing.md),
        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.md),
    ) {
        AttendanceHeader(
            title = "Asistencia",
            subtitle = "El QR es una evidencia. Puedes comprobarlo primero; el servidor decide el estado final.",
            loading = state.loading,
            onRefresh = onRefresh,
        )

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

        state.qrInspection?.let { inspection ->
            QrInspectionCard(
                inspection = inspection,
                occurrence = inspection.session?.occurrenceId?.let(occurrenceById::get),
                busy = state.actionInProgress,
                onRegister = onRegister,
                onDismiss = onDismissInspection,
            )
        }

        if (!state.loading && state.activeSessions.isEmpty()) {
            StatusNotice(
                title = "No hay pase activo",
                message = "Cuando tu docente abra asistencia aparecerá aquí. Mientras tanto puedes comprobar un QR sin registrar asistencia.",
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
            )
        }

        QrCenterCard(
            onScan = onGenericScan,
            onImage = onPickImage,
            onPaste = { showPasteDialog = true },
        )

        if (state.localRecords.isNotEmpty()) {
            Text(
                text = "Actividad reciente",
                style = MaterialTheme.typography.titleLarge,
            )
            state.localRecords.take(5).forEach { record ->
                LocalAttendanceRow(record)
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
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = CompanionColors.graphite,
                contentColor = CompanionColors.onDarkSurface,
            ),
        ) {
            Column(
                modifier = Modifier.padding(CompaneroSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
            ) {
                Text(
                    text = "PASE ACTIVO",
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
                    onClick = onScan,
                    enabled = local == null,
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
private fun QrCenterCard(
    onScan: () -> Unit,
    onImage: () -> Unit,
    onPaste: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(CompaneroSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
        ) {
            Text("Comprobar un código QR", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Escanéalo, elige una captura o pega el código. Comprobar no registra asistencia.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                Text(" Escanear con cámara")
            }
            OutlinedButton(onClick = onImage, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Image, contentDescription = null)
                Text(" Elegir imagen")
            }
            OutlinedButton(onClick = onPaste, modifier = Modifier.fillMaxWidth()) {
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
            ) { Text("Comprobar") }
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
    onRegister: () -> Unit,
    onDismiss: () -> Unit,
) {
    val valid = inspection.status == AttendanceQrInspectionStatus.VALID
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
            valid && inspection.session != null -> {
                val subject = occurrence?.subjectName
                    ?: "Grupo " + inspection.session.groupName
                subject + " · " +
                    inspection.session.scheduledStartsAt + "–" +
                    inspection.session.scheduledEndsAt +
                    ". Aún no se ha registrado asistencia."
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
    ) {
        if (valid) {
            Button(
                onClick = onRegister,
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (busy) "Guardando…" else "Registrar asistencia")
            }
        }
        OutlinedButton(
            onClick = onDismiss,
            enabled = !busy,
            modifier = Modifier.weight(1f),
        ) {
            Text("Cerrar")
        }
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
    onReview: (AttendanceRecordResponse, AttendanceStatus) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CompaneroSpacing.lg, vertical = CompaneroSpacing.md),
        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.md),
    ) {
        AttendanceHeader(
            title = "Pase de lista",
            subtitle = "Abre una sesión para una clase concreta. El QR se firma en el servidor y rota automáticamente.",
            loading = state.loading,
            onRefresh = onRefresh,
        )

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

        val active = state.teacherSession
        if (active != null) {
            TeacherActiveSession(
                session = active,
                occurrence = state.occurrences.firstOrNull { it.id == active.occurrenceId },
                qrToken = state.qr?.token,
                qrExpiresAt = state.qr?.expiresAtEpochSeconds,
                roster = state.roster?.records.orEmpty(),
                busy = state.actionInProgress,
                onClose = onClose,
                onRefreshRoster = onRefreshRoster,
                onReview = onReview,
            )
        } else {
            TeacherOccurrenceList(
                occurrences = state.occurrences,
                busy = state.actionInProgress,
                onOpen = onOpen,
            )
        }
    }
}

@Composable
private fun TeacherActiveSession(
    session: AttendanceSessionResponse,
    occurrence: ClassOccurrenceContract?,
    qrToken: String?,
    qrExpiresAt: Long?,
    roster: List<AttendanceRecordResponse>,
    busy: Boolean,
    onClose: () -> Unit,
    onRefreshRoster: () -> Unit,
    onReview: (AttendanceRecordResponse, AttendanceStatus) -> Unit,
) {
    var showCloseConfirmation by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth >= CompaneroWindowBreakpoints.expanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.md),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1.2f),
                    verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.md),
                ) {
                    TeacherSessionCard(
                        session = session,
                        occurrence = occurrence,
                        qrToken = qrToken,
                        qrExpiresAt = qrExpiresAt,
                        busy = busy,
                        onRefreshRoster = onRefreshRoster,
                        onRequestClose = { showCloseConfirmation = true },
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.md),
                ) {
                    TeacherRosterPanel(
                        roster = roster,
                        busy = busy,
                        onReview = onReview,
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.md)) {
                TeacherSessionCard(
                    session = session,
                    occurrence = occurrence,
                    qrToken = qrToken,
                    qrExpiresAt = qrExpiresAt,
                    busy = busy,
                    onRefreshRoster = onRefreshRoster,
                    onRequestClose = { showCloseConfirmation = true },
                )
                TeacherRosterPanel(
                    roster = roster,
                    busy = busy,
                    onReview = onReview,
                )
            }
        }
    }

    if (showCloseConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!busy) showCloseConfirmation = false },
            title = { Text("¿Cerrar el pase?") },
            text = {
                Text("El QR dejará de estar disponible y no se recibirán nuevos intentos.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCloseConfirmation = false
                        onClose()
                    },
                    enabled = !busy,
                ) {
                    Text("Cerrar pase")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCloseConfirmation = false },
                    enabled = !busy,
                ) {
                    Text("Seguir con el pase")
                }
            },
        )
    }
}

@Composable
private fun TeacherSessionCard(
    session: AttendanceSessionResponse,
    occurrence: ClassOccurrenceContract?,
    qrToken: String?,
    qrExpiresAt: Long?,
    busy: Boolean,
    onRefreshRoster: () -> Unit,
    onRequestClose: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = CompanionColors.graphite,
            contentColor = CompanionColors.onDarkSurface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(CompaneroSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "PASE EN VIVO",
                style = MaterialTheme.typography.labelLarge,
                color = CompanionColors.crimsonContainer,
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

            if (qrToken == null) {
                CircularProgressIndicator()
                Text("Generando QR firmado…")
            } else {
                AttendanceQrCode(
                    token = qrToken,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = CompaneroSize.qrMaxWidth),
                )
                QrCountdown(qrExpiresAt)
                Text(
                    text = "El QR cambia automáticamente. No contiene datos del alumno.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CompanionColors.onDarkSurfaceVariant,
                )
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
private fun TeacherRosterPanel(
    roster: List<AttendanceRecordResponse>,
    busy: Boolean,
    onReview: (AttendanceRecordResponse, AttendanceStatus) -> Unit,
) {
    Text(
        text = "Registros recibidos (${roster.size})",
        style = MaterialTheme.typography.titleLarge,
    )

    if (roster.isEmpty()) {
        StatusNotice(
            title = "Aún sin registros",
            message = "Los intentos aparecerán después de ser recibidos por el servidor.",
        )
    } else {
        roster.forEach { record ->
            RosterRecord(
                record = record,
                busy = busy,
                onReview = onReview,
            )
        }
    }
}

@Composable
private fun RosterRecord(
    record: AttendanceRecordResponse,
    busy: Boolean,
    onReview: (AttendanceRecordResponse, AttendanceStatus) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(CompaneroSpacing.md),
            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = record.studentId,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = attendanceStatusLabel(record.status),
                    style = MaterialTheme.typography.labelLarge,
                    color = when (record.status) {
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
                    },
                )
            }
            Text(
                text = record.reasonCode.name.lowercase().replace('_', ' '),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (record.status == AttendanceStatus.REVIEW_REQUIRED) {
                Row(horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs)) {
                    FilledTonalButton(
                        onClick = { onReview(record, AttendanceStatus.VERIFIED) },
                        enabled = !busy,
                    ) {
                        Text("Verificar")
                    }
                    OutlinedButton(
                        onClick = { onReview(record, AttendanceStatus.REJECTED) },
                        enabled = !busy,
                    ) {
                        Text("Rechazar")
                    }
                }
            }
        }
    }
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
        style = MaterialTheme.typography.titleLarge,
    )

    if (visible.isEmpty()) {
        StatusNotice(
            title = "Sin clases disponibles",
            message = "No encontramos ocurrencias académicas para abrir asistencia esta semana.",
        )
        return
    }

    visible.forEach { occurrence ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(CompaneroSpacing.md),
                verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
            ) {
                Text(
                    text = occurrence.subjectName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${dateLabel(occurrence.date)} · ${occurrence.startsAt} – ${occurrence.endsAt}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xxs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
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
