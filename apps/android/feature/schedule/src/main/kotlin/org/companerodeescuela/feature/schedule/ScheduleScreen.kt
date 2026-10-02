package org.companerodeescuela.feature.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.companerodeescuela.shared.contracts.ScheduleEntry

@Composable
fun ScheduleScreen(
    modifier: Modifier = Modifier,
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.loading) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Horario", style = MaterialTheme.typography.headlineMedium)
        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        if (state.entries.isEmpty() && state.errorMessage == null) {
            Text("Aún no hay un horario guardado. Abre Inicio con conexión para sincronizarlo.")
        } else {
            state.entries.groupBy { it.dayOfWeek }.forEach { (day, entries) ->
                Text(dayLabel(day), style = MaterialTheme.typography.titleLarge)
                entries.forEach { ScheduleCard(it) }
            }
        }
    }
}

@Composable
private fun ScheduleCard(entry: ScheduleEntry) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(entry.subjectName, style = MaterialTheme.typography.titleLarge)
            Text(entry.startsAt + " – " + entry.endsAt)
            Text((entry.classroomName ?: "Aula por confirmar") + " · " + entry.teacherName)
        }
    }
}

private fun dayLabel(day: String): String = when (day) {
    "MONDAY" -> "Lunes"
    "TUESDAY" -> "Martes"
    "WEDNESDAY" -> "Miércoles"
    "THURSDAY" -> "Jueves"
    "FRIDAY" -> "Viernes"
    "SATURDAY" -> "Sábado"
    "SUNDAY" -> "Domingo"
    else -> day
}
