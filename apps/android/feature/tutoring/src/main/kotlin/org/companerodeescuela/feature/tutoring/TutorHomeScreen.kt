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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.companerodeescuela.core.designsystem.theme.CompanionColors
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.ui.component.CompaneroHeroSurface
import org.companerodeescuela.core.ui.component.CompaneroSurface
import org.companerodeescuela.core.ui.component.CompaneroSurfaceRole
import org.companerodeescuela.core.ui.component.StatusNotice
import org.companerodeescuela.shared.contracts.ExcuseStatus

@Composable
fun TutorHomeScreen(
    onOpenRequests: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TutorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pending = state.requests.count {
        it.status == ExcuseStatus.PENDING || it.status == ExcuseStatus.UNDER_REVIEW
    }
    Column(
        modifier = modifier.fillMaxSize()
            .widthIn(max = CompaneroSize.homeContentMaxWidth)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CompaneroSpacing.page, vertical = CompaneroSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.section),
    ) {
        Text("Tutorías", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Acompañamiento académico de tus grupos asignados.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.loading) CircularProgressIndicator()
        state.error?.let { StatusNotice(title = "No pudimos actualizar", message = it) }
        state.notice?.let { StatusNotice(title = "Tutorías", message = it) }
        CompaneroHeroSurface(
            modifier = Modifier.fillMaxWidth(),
            containerColor = CompanionColors.graphite,
        ) {
            Column(
                modifier = Modifier.padding(CompaneroSpacing.md),
                verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
            ) {
                Text("MI GRUPO TUTORADO", style = MaterialTheme.typography.labelLarge,
                    color = CompanionColors.crimsonContainer)
                Text(
                    when {
                        state.loading -> "Consultando asignaciones"
                        state.groups.isEmpty() -> "Sin grupo asignado"
                        else -> state.groups.joinToString(" · ") { it.name }
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = CompanionColors.onDarkSurface,
                )
                Text(
                    if (state.groups.isEmpty()) {
                        "Control escolar debe asignarte un grupo antes de iniciar el acompañamiento."
                    } else {
                        "$pending solicitudes pendientes de revisión"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = CompanionColors.onDarkSurfaceVariant,
                )
                Button(onClick = onOpenRequests, enabled = state.groups.isNotEmpty() && !state.loading,
                    modifier = Modifier.fillMaxWidth()) { Text("Revisar solicitudes") }
            }
        }
        if (state.groups.isNotEmpty()) {
            Text("Grupos a mi cargo", style = MaterialTheme.typography.titleMedium)
            state.groups.forEach { group ->
                CompaneroSurface(modifier = Modifier.fillMaxWidth(), role = CompaneroSurfaceRole.CARD) {
                    Column(modifier = Modifier.padding(CompaneroSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs)) {
                        Text(group.name, style = MaterialTheme.typography.titleMedium)
                        Text("Asignación activa", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Button(onClick = viewModel::refresh, enabled = !state.loading && !state.submitting) {
            Text("Actualizar")
        }
    }
}
