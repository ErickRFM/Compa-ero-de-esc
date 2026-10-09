package org.companerodeescuela.feature.admin

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
fun AdminHomeScreen(
    onOpenSchedule: () -> Unit,
    onOpenChannel: () -> Unit,
    onOpenClassrooms: () -> Unit,
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
                text = "Administración de Plataforma",
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        Text(
            text = "Organiza grupos, clases, docentes y horarios desde una sola estructura académica.",
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
                Text(
                    text = "Métricas de servicios, sesiones y auditoría todavía no conectadas. " +
                        "No se muestra un estado ficticio como si fuera información del servidor.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CompanionColors.onDarkSurfaceVariant,
                )
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
                Text("Clases y grupos", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Asigna materias a un grupo y docente. Estas asignaciones alimentan clases, canal y experiencia del profesor.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onOpenClassrooms, modifier = Modifier.fillMaxWidth()) {
                    Text("Gestionar asignaciones")
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
                Text("Gestión Académica", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Publica horarios por grupo; los alumnos y docentes reciben automáticamente la agenda que les corresponde.",
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
                    "El tablero global de asistencia está pendiente de integración. " +
                        "La consulta de sesiones y sus métricas requerirá un panel autorizado " +
                        "con datos verificados de la API.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

