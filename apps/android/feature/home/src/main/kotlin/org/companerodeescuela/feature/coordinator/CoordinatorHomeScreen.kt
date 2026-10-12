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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.companerodeescuela.core.designsystem.theme.CompanionColors
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.ui.component.CompaneroHeroSurface
import org.companerodeescuela.core.ui.component.CompaneroSurface
import org.companerodeescuela.core.ui.component.CompaneroSurfaceRole

@Composable
fun CoordinatorHomeScreen(
    onOpenSchedule: () -> Unit,
    onOpenAttendance: () -> Unit,
    onOpenChannel: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                Text(
                    text = "Información institucional pendiente",
                    style = MaterialTheme.typography.titleLarge,
                    color = CompanionColors.onDarkSurface,
                )
                Text(
                    text = "La institución todavía no proporciona métricas verificadas de grupos, asistencia e incidencias para esta sesión. No mostramos cifras de ejemplo como si fueran reales.",
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
                    "Revisa el cumplimiento de clases y pases de lista reportados por docentes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onOpenAttendance, modifier = Modifier.fillMaxWidth()) {
                    Text("Ver reporte de asistencia")
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
