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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import org.companerodeescuela.shared.contracts.AttendanceStatus

@Composable
fun TeacherHomeScreen(
    onOpenAttendance: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AttendanceViewModel = hiltViewModel(),
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
        Text(
            text = "Hoy",
            modifier = Modifier.padding(end = 52.dp),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Tu operación académica y pase de lista en un solo lugar.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.loading && state.mode == AttendanceMode.LOADING) {
            CircularProgressIndicator()
            return@Column
        }

        state.errorMessage?.let {
            StatusNotice(
                title = "No pudimos actualizar",
                message = it,
            )
        }

        val active = state.teacherSession
        if (active != null) {
            val occurrence = state.occurrences.firstOrNull { it.id == active.occurrenceId }
            val records = state.roster?.records.orEmpty()
            val verified = records.count { it.status == AttendanceStatus.VERIFIED }
            val review = records.count { it.status == AttendanceStatus.REVIEW_REQUIRED }

            CompaneroHeroSurface(
                modifier = Modifier.fillMaxWidth(),
                containerColor = CompanionColors.graphite,
            ) {
                Column(
                    modifier = Modifier.padding(CompaneroSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
                ) {
                    Text(
                        text = "CLASE ACTUAL  ● EN VIVO",
                        style = MaterialTheme.typography.labelLarge,
                        color = CompanionColors.crimsonContainer,
                    )
                    Text(
                        text = occurrence?.subjectName ?: "Grupo ${active.groupName}",
                        style = MaterialTheme.typography.titleLarge,
                        color = CompanionColors.onDarkSurface,
                    )
                    Text(
                        text = "${active.scheduledStartsAt} – ${active.scheduledEndsAt} · ${active.groupName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CompanionColors.onDarkSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.md),
                    ) {
                        Metric("Verificados", verified.toString(), Modifier.weight(1f))
                        Metric("Revisión", review.toString(), Modifier.weight(1f))
                        Metric("Recibidos", records.size.toString(), Modifier.weight(1f))
                    }
                    Button(
                        onClick = onOpenAttendance,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Gestionar pase")
                    }
                }
            }
        } else {
            StatusNotice(
                title = "Sin pase abierto",
                message = "Abre Asistencia cuando quieras iniciar el pase de una clase programada.",
            )
            Button(
                onClick = onOpenAttendance,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Ir a asistencia")
            }
        }

        Text("Próximas clases", style = MaterialTheme.typography.titleMedium)
        val visible = state.occurrences.take(3)
        if (visible.isEmpty()) {
            StatusNotice(
                title = "Sin clases disponibles",
                message = "No encontramos clases próximas para mostrar.",
            )
        } else {
            visible.forEach { occurrence ->
                CompaneroSurface(
                    modifier = Modifier.fillMaxWidth(),
                    role = CompaneroSurfaceRole.CARD,
                ) {
                    Column(
                        modifier = Modifier.padding(CompaneroSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xxs),
                    ) {
                        Text(
                            text = occurrence.subjectName,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "${occurrence.date} · ${occurrence.startsAt} – ${occurrence.endsAt}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = listOfNotNull(occurrence.classroomName, occurrence.buildingName)
                                .joinToString(" · ")
                                .ifBlank { "Aula por confirmar" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
