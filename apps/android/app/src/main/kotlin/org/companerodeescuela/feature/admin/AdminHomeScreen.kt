package org.companerodeescuela.feature.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.companerodeescuela.core.designsystem.theme.CompanionColors
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.ui.component.CompaneroHeroSurface
import org.companerodeescuela.core.ui.component.CompaneroSurface
import org.companerodeescuela.core.ui.component.CompaneroSurfaceRole

@Composable
fun AdminHomeScreen(
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
        Text(
            text = "Administración de Plataforma",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Gestión institucional de usuarios, configuración del sistema y auditoría.",
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
                    text = "ESTADO DEL SISTEMA",
                    style = MaterialTheme.typography.labelLarge,
                    color = CompanionColors.crimsonContainer,
                )
                Text(
                    text = "Plataforma Institucional",
                    style = MaterialTheme.typography.titleLarge,
                    color = CompanionColors.onDarkSurface,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.md),
                ) {
                    Metric("Servicios", "OK", Modifier.weight(1f))
                    Metric("Sesiones", "Activas", Modifier.weight(1f))
                    Metric("Auditoría", "0 alertas", Modifier.weight(1f))
                }
            }
        }

        Text("Herramientas administrativas", style = MaterialTheme.typography.titleMedium)

        CompaneroSurface(
            modifier = Modifier.fillMaxWidth(),
            role = CompaneroSurfaceRole.CARD,
        ) {
            Column(
                modifier = Modifier.padding(CompaneroSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
            ) {
                Text("Gestión Académica", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Supervisa horarios, grupos y configuraciones del ciclo activo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onOpenSchedule, modifier = Modifier.fillMaxWidth()) {
                    Text("Abrir control escolar")
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
                Text("Asistencia Global", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Monitorea el flujo completo de asistencias e incidencias registradas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onOpenAttendance, modifier = Modifier.fillMaxWidth()) {
                    Text("Ver métricas de asistencia")
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
                Text("Comunicación e Incidencias", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Transmite comunicados institucionales urgentes a todos los roles.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onOpenChannel, modifier = Modifier.fillMaxWidth()) {
                    Text("Abrir canal institucional")
                }
            }
        }
    }
}

@Composable
private fun Metric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xxs),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = CompanionColors.onDarkSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = CompanionColors.onDarkSurfaceVariant,
        )
    }
}
