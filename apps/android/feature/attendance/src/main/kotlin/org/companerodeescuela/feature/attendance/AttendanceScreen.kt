package org.companerodeescuela.feature.attendance

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import org.companerodeescuela.core.database.LocalAttendanceRecord
import org.companerodeescuela.core.database.LocalAttendanceSyncState
import org.companerodeescuela.core.ui.component.NoticeTone
import org.companerodeescuela.core.ui.component.StatusNotice
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

    if (state.scannerSessionId != null) {
        Dialog(
            onDismissRequest = viewModel::dismissScanner,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            AttendanceQrScanner(
                onToken = viewModel::submitScannedQr,
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
    onScan: (String) -> Unit,
    modifier: Modifier,
) {
    val occurrenceById = remember(state.occurrences) {
        state.occurrences.associateBy { it.id }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AttendanceHeader(
            title = "Asistencia",
            subtitle = "Escanea el QR de tu clase. Primero se guarda en este teléfono y después el servidor decide el resultado.",
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
                title = "No pudimos actualizar",
                message = it,
                tone = NoticeTone.ERROR,
            )
        }

        if (!state.loading && state.activeSessions.isEmpty()) {
            StatusNotice(
                title = "Sin pase activo",
                message = "Cuando tu docente abra asistencia para una clase inscrita aparecerá aquí.",
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
}

@Composable
private fun StudentSessionCard(
    session: AttendanceSessionResponse,
    occurrence: ClassOccurrenceContract?,
    local: LocalAttendanceRecord?,
    onScan: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = occurrence?.subjectName ?: "Grupo ${session.groupName}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${session.scheduledStartsAt} – ${session.scheduledEndsAt} · ${session.groupName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            occurrence?.let {
                val location = listOfNotNull(it.classroomName, it.buildingName)
                    .joinToString(" · ")
                    .ifBlank { "Aula por confirmar" }
                Text(
                    text = location,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            local?.let { LocalAttendanceStatus(it) }

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
                        " Escanear QR"
                    } else {
                        " Pase ya registrado en este dispositivo"
                    },
                )
            }
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
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sesión ${record.sessionId.take(8)}",
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
                    tint = MaterialTheme.colorScheme.primary,
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
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
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
                        .widthIn(max = 340.dp),
                )
                QrCountdown(qrExpiresAt)
                Text(
                    text = "El QR cambia automáticamente. No contiene datos del alumno.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.74f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                    onClick = onClose,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cerrar pase")
                }
            }
        }
    }

    Text(
        text = "Registros (${roster.size})",
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
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                        AttendanceStatus.VERIFIED -> MaterialTheme.colorScheme.primary
                        AttendanceStatus.REJECTED -> MaterialTheme.colorScheme.error
                        AttendanceStatus.REVIEW_REQUIRED -> MaterialTheme.colorScheme.tertiary
                        AttendanceStatus.LIKELY -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Text(
                text = record.reasonCode.name.lowercase().replace('_', ' '),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (record.status == AttendanceStatus.REVIEW_REQUIRED) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
            verticalArrangement = Arrangement.spacedBy(4.dp),
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
            modifier = Modifier.padding(top = 12.dp),
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
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        StatusNotice(
            title = "Asistencia no disponible",
            message = message ?: "Tu rol actual no tiene un flujo de asistencia en esta versión.",
            tone = NoticeTone.WARNING,
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.padding(top = 12.dp),
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
