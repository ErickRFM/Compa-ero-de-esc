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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.designsystem.v8.V8GlassCard
import org.companerodeescuela.core.designsystem.v8.V8ScreenHeader
import org.companerodeescuela.core.ui.component.CompaneroSurface
import org.companerodeescuela.core.ui.component.CompaneroSurfaceRole
import org.companerodeescuela.core.ui.component.StatusNotice

@Composable
fun CoordinatorHomeScreen(
    onOpenSchedule: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CoordinatorHomeViewModel = hiltViewModel(),
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
                text = "Coordinación Académica",
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        Text(
            text = "Supervisión de grupos, asistencia docente y seguimiento de incidencias.",
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
                Text(
                    text = "Asistencia docente e incidencias: indicadores pendientes de integración institucional.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = viewModel::refresh,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.loading,
                ) {
                    Text(if (state.loading) "Actualizando…" else "Actualizar indicadores")
                }
            }
        }

        Text("Acciones de coordinación", style = MaterialTheme.typography.titleMedium)

        CompaneroSurface(
            modifier = Modifier.fillMaxWidth(),
            role = CompaneroSurfaceRole.CARD,
        ) {
            Column(
                modifier = Modifier.padding(CompaneroSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
            ) {
                Text("Monitoreo de Asistencia", style = MaterialTheme.typography.titleSmall)
                Text(
                    "El reporte consolidado requiere una consulta institucional autorizada; " +
                        "el pase de lista de alumno y docente no concede acceso a Coordinación.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatusNotice(
                    title = "Reporte aún no disponible",
                    message =
                        "Los indicadores de asistencia no están conectados todavía. " +
                            "No se mostrará información de otros perfiles como si fuera un reporte.",
                )
            }
        }

        CompaneroSurface(
            modifier = Modifier.fillMaxWidth(),
            role = CompaneroSurfaceRole.CARD,
        ) {
            Column(
                modifier = Modifier.padding(CompaneroSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
            ) {
                Text("Horarios y Aulas", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Consulta la distribución de materias, profesores y asignación de salones.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onOpenSchedule, modifier = Modifier.fillMaxWidth()) {
                    Text("Consultar agenda académica")
                }
            }
        }

        CompaneroSurface(
            modifier = Modifier.fillMaxWidth(),
            role = CompaneroSurfaceRole.CARD,
        ) {
            Column(
                modifier = Modifier.padding(CompaneroSpacing.sm),
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
