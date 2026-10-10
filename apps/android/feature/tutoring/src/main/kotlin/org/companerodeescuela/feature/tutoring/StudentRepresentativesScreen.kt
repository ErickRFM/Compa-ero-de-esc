package org.companerodeescuela.feature.tutoring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.ui.component.CompaneroSurface
import org.companerodeescuela.core.ui.component.CompaneroSurfaceRole
import org.companerodeescuela.core.ui.component.StatusNotice
import org.companerodeescuela.shared.contracts.RepresentativePosition
import org.companerodeescuela.shared.contracts.RepresentativeStatus

/** Student-only destination. The API validates student, group and invitation state. */
@Composable
fun StudentRepresentativesScreen(
    modifier: Modifier = Modifier,
    viewModel: StudentRepresentativesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    StudentRepresentativesContent(state, viewModel::refresh, viewModel::decide, modifier)
}

@Composable
internal fun StudentRepresentativesContent(
    state: StudentRepresentativesState,
    onRefresh: () -> Unit,
    onDecision: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().widthIn(max = CompaneroSize.homeContentMaxWidth)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = CompaneroSpacing.page, vertical = CompaneroSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.section)) {
        Text("Representantes de mi grupo", style = MaterialTheme.typography.headlineSmall)
        Text("Revisa tus invitaciones de Jefe o Subjefe de grupo. Aceptar no cambia tu rol escolar.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.loading) CircularProgressIndicator()
        state.error?.let { StatusNotice(title = "No se pudieron cargar las invitaciones", message = it) }
        state.notice?.let { StatusNotice(title = "Representantes", message = it) }
        if (!state.loading && state.assignments.isEmpty()) {
            StatusNotice(title = "Sin nombramientos", message = "Cuando tu tutor te invite, aparecerá aquí.")
        }
        state.assignments.forEach { assignment ->
            CompaneroSurface(modifier = Modifier.fillMaxWidth(), role = CompaneroSurfaceRole.CARD) {
                Column(modifier = Modifier.padding(CompaneroSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm)) {
                    val position = if (assignment.position == RepresentativePosition.CHIEF) "Jefe de grupo" else "Subjefe de grupo"
                    Text(position, style = MaterialTheme.typography.titleMedium)
                    Text(assignment.academicGroupName, style = MaterialTheme.typography.bodyMedium)
                    Text(when (assignment.status) {
                        RepresentativeStatus.PENDING -> "Invitación pendiente"
                        RepresentativeStatus.ACTIVE -> "Nombramiento activo"
                        RepresentativeStatus.DECLINED -> "Invitación rechazada"
                        RepresentativeStatus.REVOKED -> "Nombramiento revocado"
                        RepresentativeStatus.EXPIRED -> "Nombramiento vencido"
                    }, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (assignment.status == RepresentativeStatus.PENDING) {
                        Button(onClick = { onDecision(assignment.id, true) }, enabled = !state.submitting,
                            modifier = Modifier.fillMaxWidth().testTag("representative_accept")) {
                            Text("Aceptar nombramiento")
                        }
                        OutlinedButton(onClick = { onDecision(assignment.id, false) }, enabled = !state.submitting,
                            modifier = Modifier.fillMaxWidth().testTag("representative_decline")) {
                            Text("Rechazar invitación")
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = onRefresh, enabled = !state.loading && !state.submitting,
            modifier = Modifier.fillMaxWidth()) { Text("Actualizar invitaciones") }
    }
}
