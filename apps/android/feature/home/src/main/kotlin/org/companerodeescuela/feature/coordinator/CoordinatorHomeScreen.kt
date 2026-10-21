package org.companerodeescuela.feature.coordinator

import org.companerodeescuela.core.designsystem.v8.V8ScreenHeader
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import org.companerodeescuela.core.designsystem.theme.CompanionColors
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.ui.component.CompaneroHeroSurface
import org.companerodeescuela.core.ui.component.CompaneroSurface
import org.companerodeescuela.core.ui.component.CompaneroSurfaceRole
import org.companerodeescuela.core.ui.component.StatusNotice

@Composable
fun CoordinatorHomeScreen(
    onOpenSchedule: () -> Unit,
    onOpenChannel: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CoordinatorHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier = modifier
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

        CompaneroHeroSurface(
            modifier = Modifier.fillMaxWidth(),
            containerColor = CompanionColors.graphite,
        ) {
            Column(
                modifier = Modifier.padding(CompaneroSpacing.md),
                verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
            ) {
                Text(
                    text = "INDICADORES ACADÉMICOS",
                    style = MaterialTheme.typography.labelLarge,
                    color = CompanionColors.crimsonContainer,
                )
                when {
                    state.loading -> CircularProgressIndicator()
                    state.overview != null -> {
                        Text(
                            text = "${state.overview!!.activeGroups} grupos activos",
                            style = MaterialTheme.typography.titleLarge,
                            color = CompanionColors.onDarkSurface,
                        )
                        Text(
                            text = "Dato consultado del servidor para tu institución y periodo vigente.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CompanionColors.onDarkSurfaceVariant,
                        )
                    }
                    else -> StatusNotice(
                        title = "Sin estadísticas verificadas",
                        message = state.errorMessage ?: "No se pudieron consultar los grupos institucionales.",
                    )
                }
                Text(
                    text = "Asistencia docente e incidencias: indicadores pendientes de integración institucional.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CompanionColors.onDarkSurfaceVariant,
                )
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
                    message = "Los indicadores de asistencia no están conectados todavía. " +
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
                    "Comunica novedades, cambios de aula o cancelaciones a grupos y docentes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onOpenChannel, modifier = Modifier.fillMaxWidth()) {
                    Text("Publicar aviso institucional")
                }
            }
        }
    }
}
