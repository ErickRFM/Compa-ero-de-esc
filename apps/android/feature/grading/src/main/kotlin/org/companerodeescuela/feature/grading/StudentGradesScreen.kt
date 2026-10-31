@file:Suppress("ktlint:standard:function-naming")

package org.companerodeescuela.feature.grading

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.designsystem.v8.V8GlassCard
import org.companerodeescuela.core.designsystem.v8.V8GlassFilterChip
import org.companerodeescuela.core.designsystem.v8.V8HeroTitle
import org.companerodeescuela.core.designsystem.v8.V8MatteBackdrop
import org.companerodeescuela.core.designsystem.v8.V8RedPrimaryButton
import org.companerodeescuela.core.designsystem.v8.V8ScreenHeader
import org.companerodeescuela.core.ui.component.StatusNotice
import org.companerodeescuela.shared.contracts.GradebookSnapshot
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val commonGradingPeriods = listOf("Parcial 1", "Parcial 2", "Parcial 3", "Final")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentGradesScreen(viewModel: StudentGradesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    Box(modifier = Modifier.fillMaxSize()) {
        if (!org.companerodeescuela.core.designsystem.v8.LocalV8GlassEnabled.current) {
            V8MatteBackdrop(modifier = Modifier.matchParentSize())
        }
        PullToRefreshBox(
            isRefreshing = state.loadingGrades || state.loadingClassrooms,
            onRefresh = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (state.selectedClassroomId.isNotBlank() && state.gradingPeriod.isNotBlank()) {
                    viewModel.loadGrades()
                }
            },
            modifier = Modifier.fillMaxSize().align(Alignment.TopCenter),
        ) {
            Column(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .widthIn(max = CompaneroSize.homeContentMaxWidth)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = CompaneroSpacing.page, vertical = CompaneroSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.md),
            ) {
                V8ScreenHeader {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        V8HeroTitle(leading = "Mis", accent = "calificaciones")
                        Text(
                            text = "Consulta únicamente las calificaciones que tus docentes han publicado.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                V8GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Materia y periodo",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (state.loadingClassrooms) CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        state.classroomError?.let { StatusNotice(title = "No se pudieron cargar tus materias", message = it) }
                        if (state.classrooms.isEmpty() && !state.loadingClassrooms && state.classroomError == null) {
                            StatusNotice(title = "Aún no tienes materias", message = "Cuando Control Escolar te inscriba, aparecerán aquí.")
                        }
                        if (state.classrooms.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                state.classrooms.forEach { classroom ->
                                    V8GlassFilterChip(
                                        selected = state.selectedClassroomId == classroom.id,
                                        onClick = {
                                            if (!state.loadingGrades) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                viewModel.selectClassroom(classroom.id)
                                                if (state.gradingPeriod.isNotBlank()) {
                                                    viewModel.loadGrades()
                                                }
                                            }
                                        },
                                        label = { Text(classroom.name) },
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            commonGradingPeriods.forEach { period ->
                                V8GlassFilterChip(
                                    selected = state.gradingPeriod.equals(period, ignoreCase = true),
                                    onClick = {
                                        if (!state.loadingGrades) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.setGradingPeriod(period)
                                            if (state.selectedClassroomId.isNotBlank()) {
                                                viewModel.loadGrades()
                                            }
                                        }
                                    },
                                    label = { Text(period) },
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
                        V8RedPrimaryButton(
                            text = if (state.loadingGrades) "Consultando calificaciones…" else "Consultar calificaciones",
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.loadGrades()
                            },
                            enabled = !state.loadingGrades && state.selectedClassroomId.isNotBlank() && state.gradingPeriod.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                state.gradesError?.let { StatusNotice(title = "No se pudieron consultar", message = it) }
                state.snapshot?.let { PublishedGradesCard(it) }
                if (state.snapshot == null && state.hasQueried && state.gradesError == null) {
                    StatusNotice(title = "Aún no publicado", message = "No hay calificaciones publicadas para este periodo.")
                }
            }
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
