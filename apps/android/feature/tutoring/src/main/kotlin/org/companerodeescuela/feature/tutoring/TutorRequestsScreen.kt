package org.companerodeescuela.feature.tutoring

import org.companerodeescuela.core.designsystem.v8.V8ScreenHeader
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.ui.component.CompaneroSurface
import org.companerodeescuela.core.ui.component.CompaneroSurfaceRole
import org.companerodeescuela.core.ui.component.StatusNotice
import org.companerodeescuela.shared.contracts.ExcuseRequestSummary
import org.companerodeescuela.shared.contracts.ExcuseStatus

@Composable
fun TutorRequestsScreen(
    modifier: Modifier = Modifier,
    viewModel: TutorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<Pair<ExcuseRequestSummary, Boolean>?>(null) }
    var comment by remember { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxSize()
            .widthIn(max = CompaneroSize.homeContentMaxWidth)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CompaneroSpacing.page, vertical = CompaneroSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.section),
    ) {
        V8ScreenHeader {
            Text("Justificantes", style = MaterialTheme.typography.headlineSmall)
        }
        Text("Revisa las solicitudes de tus grupos. Una justificación no cambia la presencia verificada por QR.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.loading) CircularProgressIndicator()
        state.error?.let { StatusNotice(title = "No pudimos cargar solicitudes", message = it) }
        state.notice?.let { StatusNotice(title = "Solicitud", message = it) }
        if (!state.loading && state.error == null && state.requests.isEmpty()) {
            StatusNotice(title = "Sin solicitudes", message = "No hay justificantes registrados para tus grupos.")
        }
        state.requests.forEach { request ->
            CompaneroSurface(modifier = Modifier.fillMaxWidth(), role = CompaneroSurfaceRole.CARD) {
                Column(modifier = Modifier.padding(CompaneroSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm)) {
                    Text("Grupo " + request.academicGroupId + " · " + request.attendanceDateIso,
                        style = MaterialTheme.typography.titleMedium)
                    Text("Estudiante: " + request.studentId, style = MaterialTheme.typography.bodySmall)
                    Text(request.reason, style = MaterialTheme.typography.bodyMedium)
                    Text(when (request.status) {
                        ExcuseStatus.PENDING -> "Pendiente"
                        ExcuseStatus.UNDER_REVIEW -> "En revisión"
                        ExcuseStatus.APPROVED -> "Justificado"
                        ExcuseStatus.REJECTED -> "Rechazado"
                        ExcuseStatus.CANCELLED -> "Cancelado"
                    }, style = MaterialTheme.typography.labelLarge)
                    if (request.status in setOf(ExcuseStatus.PENDING, ExcuseStatus.UNDER_REVIEW)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm)) {
                            Button(enabled = !state.submitting,
                                onClick = { comment = ""; selected = request to true }) { Text("Aprobar") }
                            OutlinedButton(enabled = !state.submitting,
                                onClick = { comment = ""; selected = request to false }) { Text("Rechazar") }
                        }
                    }
                    request.reviewComment?.let {
                        Text("Observación: " + it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        OutlinedButton(onClick = viewModel::refresh, enabled = !state.submitting && !state.loading) {
            Text("Actualizar")
        }
    }

    selected?.let { (request, approved) ->
        AlertDialog(
            onDismissRequest = { if (!state.submitting) selected = null },
            title = { Text(if (approved) "Aprobar justificación" else "Rechazar justificación") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm)) {
                    Text("Esta decisión quedará asociada a tu cuenta. No se modificará el registro QR.")
                    OutlinedTextField(value = comment, onValueChange = { comment = it.take(1000) },
                        label = { Text("Comentario de revisión (opcional)") },
                        modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(enabled = !state.submitting,
                    onClick = { viewModel.review(request.id, approved, comment); selected = null }) {
                    Text("Confirmar")
                }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Cancelar") } },
        )
    }
}
