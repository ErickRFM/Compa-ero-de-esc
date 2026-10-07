package org.companerodeescuela.feature.settings

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.ui.component.NoticeTone
import org.companerodeescuela.core.ui.component.StatusNotice
import org.companerodeescuela.feature.schedule.ScheduleViewModel

@Composable
fun IntegrationSettingsScreen(
    onOpenSchedule: () -> Unit,
    modifier: Modifier = Modifier,
    scheduleViewModel: ScheduleViewModel = hiltViewModel(),
) {
    val state by scheduleViewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = CompaneroSize.homeContentMaxWidth)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CompaneroSpacing.page, vertical = CompaneroSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.section),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "Sistema escolar",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "La integración es opcional. Compañero puede funcionar con horario importado o manual aunque el sistema escolar no esté disponible.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        StatusNotice(
            title = when {
                state.loading -> "Comprobando integración"
                state.fromCache -> "Usando información guardada"
                state.errorMessage != null -> "Integración no disponible"
                else -> "Integración disponible"
            },
            message = when {
                state.loading -> "Estamos comprobando si hay datos académicos disponibles."
                state.fromCache -> state.syncMessage
                    ?: "La agenda local sigue disponible aunque la integración no responda."
                state.errorMessage != null ->
                    "No pudimos obtener datos institucionales ahora. Esto no bloquea tu agenda local."
                else -> "Los datos académicos se actualizaron correctamente."
            },
            tone = when {
                state.loading -> NoticeTone.INFO
                state.fromCache || state.errorMessage != null -> NoticeTone.WARNING
                else -> NoticeTone.SUCCESS
            },
        )

        state.lastUpdatedAtEpochSeconds?.let { epoch ->
            Text(
                text = "Última actualización: " + formatTimestamp(epoch),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Button(
            onClick = scheduleViewModel::load,
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.loading) "Actualizando…" else "Sincronizar ahora")
        }

        Button(
            onClick = onOpenSchedule,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Abrir agenda")
        }

        Text(
            text = "Si tu escuela habilita una API institucional, aquí se mostrará su estado. Si no existe, puedes seguir importando PDF o imágenes y editar tu horario desde Agenda.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatTimestamp(epochSeconds: Long): String =
    runCatching {
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochSecond(epochSeconds))
    }.getOrDefault("sin fecha")
