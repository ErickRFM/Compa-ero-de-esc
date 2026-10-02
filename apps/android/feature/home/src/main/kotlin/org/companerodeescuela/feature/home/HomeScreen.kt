package org.companerodeescuela.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.loading && state.overview == null) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val overview = state.overview
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = overview?.let { "Hola, " + it.studentName } ?: "Hoy",
            style = MaterialTheme.typography.headlineMedium,
        )

        if (state.fromCache) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Sin conexión. Mostrando el horario guardado en este dispositivo.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        state.errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
            Button(onClick = viewModel::refresh) { Text("Reintentar") }
        }

        overview?.let {
            SectionTitle("Ahora")
            it.current?.let { entry -> ClassCard(entry) }
                ?: Text("No tienes clase en este momento.")

            SectionTitle("Siguiente")
            it.next?.let { entry -> ClassCard(entry) }
                ?: Text("No hay otra clase programada hoy.")

            SectionTitle("Tu jornada")
            if (it.classes.isEmpty()) {
                Text("No tienes clases programadas para hoy.")
            } else {
                it.classes.forEach { entry -> ClassCard(entry) }
            }

            Button(
                onClick = viewModel::refresh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Actualizar")
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun ClassCard(entry: ScheduleEntry) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = entry.subjectName, style = MaterialTheme.typography.titleLarge)
            Text(entry.startsAt + " – " + entry.endsAt)
            Text(entry.classroomName ?: "Aula por confirmar")
            Text(entry.teacherName)
        }
    }
}
