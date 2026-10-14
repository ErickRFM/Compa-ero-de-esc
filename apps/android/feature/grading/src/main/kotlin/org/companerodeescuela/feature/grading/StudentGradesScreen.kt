@file:Suppress("ktlint:standard:function-naming")

package org.companerodeescuela.feature.grading

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.companerodeescuela.core.designsystem.v8.V8GlassCard
import org.companerodeescuela.core.ui.component.StatusNotice
import org.companerodeescuela.shared.contracts.GradebookSnapshot
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun StudentGradesScreen(viewModel: StudentGradesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Mis calificaciones", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Consulta únicamente las calificaciones que tus docentes han publicado.", style = MaterialTheme.typography.bodyMedium)

        V8GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Materia y periodo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (state.loadingClassrooms) CircularProgressIndicator()
                state.classroomError?.let { StatusNotice(title = "No se pudieron cargar tus materias", message = it) }
                if (state.classrooms.isEmpty() && !state.loadingClassrooms && state.classroomError == null) {
                    StatusNotice(title = "Aún no tienes materias", message = "Cuando Control Escolar te inscriba, aparecerán aquí.")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.classrooms.forEach { classroom ->
                        FilterChip(
                            selected = state.selectedClassroomId == classroom.id,
                            enabled = !state.loadingGrades,
                            onClick = { viewModel.selectClassroom(classroom.id) },
                            label = { Text(classroom.name) },
                        )
                    }
                }
                OutlinedTextField(
                    value = state.gradingPeriod,
                    onValueChange = viewModel::setGradingPeriod,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.loadingGrades,
                    label = { Text("Periodo de evaluación") },
                    placeholder = { Text("Ej. Parcial 1") },
                    singleLine = true,
                )
                Button(
                    onClick = viewModel::loadGrades,
                    enabled = !state.loadingGrades && state.selectedClassroomId.isNotBlank() && state.gradingPeriod.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.loadingGrades) {
                        CircularProgressIndicator()
                    } else {
                        Text("Consultar calificaciones")
                    }
                }
            }
        }

        state.gradesError?.let { StatusNotice(title = "No se pudieron consultar", message = it) }
        state.snapshot?.let { PublishedGradesCard(it) }
        if (state.snapshot == null && state.hasQueried && state.gradesError == null) {
            StatusNotice(title = "Aún no publicado", message = "No hay calificaciones publicadas para este periodo.")
        }
    }
}

@Composable
private fun PublishedGradesCard(snapshot: GradebookSnapshot) {
    val row = snapshot.rows.firstOrNull()
    V8GlassCard(modifier = Modifier.fillMaxWidth(), emphasized = true) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(snapshot.gradingPeriod, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Publicado · revisión ${snapshot.revision}", style = MaterialTheme.typography.bodyMedium)
            if (row == null) {
                Text("No hay calificaciones capturadas para tu matrícula en este periodo.")
            } else {
                snapshot.categories.forEach { category ->
                    val score =
                        row.scores.entries
                            .firstOrNull { it.key.equals(category.id, ignoreCase = true) }
                            ?.value
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${category.name} · ${category.weightPercent.toInt()}%")
                        Text(score?.let { "${"%.2f".format(it)} / 10" } ?: "Pendiente")
                    }
                }
                val average = weightedAverage(snapshot, row.scores)
                if (average != null) {
                    Text(
                        "Promedio: ${"%.2f".format(average)} / 10",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            snapshot.publishedAtEpochSeconds?.let { epoch ->
                val publishedAt = Instant.ofEpochSecond(epoch).atZone(ZoneId.systemDefault())
                Text(
                    "Fecha de publicación: ${publishedAt.format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"))}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

internal fun weightedAverage(
    snapshot: GradebookSnapshot,
    scores: Map<String, Double?>,
): Double? {
    if (snapshot.categories.isEmpty()) return null
    var total = 0.0
    snapshot.categories.forEach { category ->
        val value =
            scores.entries
                .firstOrNull { it.key.equals(category.id, ignoreCase = true) }
                ?.value ?: return null
        if (value !in 0.0..10.0) return null
        total += value * category.weightPercent / 100.0
    }
    return kotlin.math.round(total * 100) / 100
}
