package org.companerodeescuela.feature.coordinator

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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.designsystem.v8.V8GlassCard
import org.companerodeescuela.core.designsystem.v8.V8ScreenHeader
import org.companerodeescuela.core.ui.component.StatusNotice
import org.companerodeescuela.shared.contracts.CoordinationOverview

@Composable
fun coordinatorHomeScreen(
    onOpenSchedule: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CoordinatorHomeViewModel = hiltViewModel(),
    directionExperience: Boolean = false,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
    }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .widthIn(max = CompaneroSize.homeContentMaxWidth)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CompaneroSpacing.page, vertical = CompaneroSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.section),
    ) {
        V8ScreenHeader {
            Text(
                text = if (directionExperience) "Dirección" else "Coordinación Académica",
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        Text(
            text = "Supervisión de grupos y seguimiento institucional de asistencia.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        V8GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(CompaneroSpacing.md),
                verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
            ) {
                Text(
                    text = "INDICADORES ACADÉMICOS",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                when {
                    state.overview != null -> {
                        Text(
                            text = "${state.overview?.activeGroups ?: 0} grupos activos",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text =
                                if (state.errorMessage == null) {
                                    "Grupos consultados del servidor para tu institución."
                                } else {
                                    "Último resumen disponible. No se pudo verificar el estado actual."
                                },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    state.loading -> CircularProgressIndicator()
                    else ->
                        StatusNotice(
                            title = "No se pudo consultar el resumen",
                            message = state.errorMessage ?: "Vuelve a intentar la consulta institucional.",
                        )
                }
                if (state.overview != null && state.errorMessage != null) {
                    StatusNotice(
                        title = "Datos sin actualizar",
                        message = state.errorMessage.orEmpty(),
                    )
                }
                TextButton(
                    onClick = viewModel::refresh,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.loading,
                ) {
                    Text(if (state.loading) "Actualizando…" else "Actualizar indicadores")
                }
            }
        }

        Text(
            if (directionExperience) "Supervisión institucional" else "Acciones de coordinación",
            style = MaterialTheme.typography.titleMedium,
        )

        V8GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
            ) {
                Text(
                    "Monitoreo de asistencia",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    "Pases de lista y revisiones docentes de tu institución.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                coordinationAttendanceIndicators(state.overview)
            }
        }

        V8GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
            ) {
                Text("Agenda personal", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Consulta o prepara tu agenda local. No cambia grupos, docentes ni aulas oficiales.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onOpenSchedule, modifier = Modifier.fillMaxWidth()) {
                    Text("Abrir mi agenda")
                }
                StatusNotice(
                    title = "Gestión institucional de horarios",
                    message =
                        "Las modificaciones y asignaciones oficiales de horarios a grupos y aulas requieren permiso " +
                            "explícito (MANAGE_ACADEMICS) y confirmación con segundo factor (MFA Step-Up). " +
                            "Para consultar horarios oficiales, utiliza la vista de grupos.",
                )
            }
        }

        V8GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
            ) {
                Text("Canal de avisos generales", style = MaterialTheme.typography.titleSmall)
                Text(
                    "La publicación general requiere un canal institucional con alcance y permisos explícitos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatusNotice(
                    title = "Publicación institucional pendiente",
                    message =
                        "No se abrirá el canal de clase de otro perfil. " +
                            "La función se habilitará cuando el servidor verifique destinatarios y permisos.",
                )
            }
        }
    }
}

@Composable
fun coordinationAttendanceIndicators(overview: CoordinationOverview?) {
    Column(verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm)) {
        Text(
            overview?.openAttendancePasses?.let { "$it pases abiertos" } ?: "Pases abiertos: no disponible",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            overview?.pendingAttendanceReviews?.let { "$it revisiones pendientes" } ?: "Revisiones pendientes: no disponible",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (overview?.attendanceSampleLimited == true) {
            Text(
                overview.attendanceSessionsSampleLimit?.let {
                    "Vista parcial: hasta $it sesiones recientes. Los conteos corresponden a esa muestra."
                } ?: "Vista parcial: los conteos corresponden a una muestra de sesiones recientes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
