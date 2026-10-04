package org.companerodeescuela.feature.schedule

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.motion.CompaneroMotionDuration
import org.companerodeescuela.core.motion.LocalCompaneroMotionPreferences
import org.companerodeescuela.core.ui.component.AcademicTimelineItem
import org.companerodeescuela.core.ui.component.ExpressiveSegmentedControl
import org.companerodeescuela.core.ui.component.NoticeTone
import org.companerodeescuela.core.ui.component.StatusNotice
import org.companerodeescuela.shared.contracts.ScheduleEntry

private enum class AgendaMode {
    DAY,
    WEEK,
}

@Composable
fun ScheduleScreen(
    modifier: Modifier = Modifier,
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.loading && state.entries.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    var mode by remember { mutableStateOf(AgendaMode.DAY) }
    var selectedDay by remember { mutableStateOf(LocalDate.now().dayOfWeek.name) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CompaneroSpacing.lg, vertical = CompaneroSpacing.md),
        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.md),
    ) {
        Text(
            text = "Agenda",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "Tu horario académico, disponible incluso cuando pierdes conexión.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ExpressiveSegmentedControl(
            options = listOf("Día", "Semana"),
            selectedIndex = if (mode == AgendaMode.DAY) 0 else 1,
            onSelected = { index ->
                mode = if (index == 0) AgendaMode.DAY else AgendaMode.WEEK
            },
        )

        if (state.fromCache) {
            StatusNotice(
                title = "Agenda guardada",
                message = "Estás viendo la última versión disponible en este dispositivo.",
                tone = NoticeTone.WARNING,
            )
        }

        state.errorMessage?.let {
            StatusNotice(
                title = "No pudimos actualizar la agenda",
                message = it,
                tone = NoticeTone.ERROR,
            )
            Button(onClick = viewModel::load) {
                Text("Reintentar")
            }
        }

        if (state.entries.isEmpty() && state.errorMessage == null) {
            StatusNotice(
                title = "Horario pendiente",
                message = "Todavía no recibimos un horario para tu cuenta.",
            )
        } else {
            val reducedMotion = LocalCompaneroMotionPreferences.current.reducedMotion
            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    val duration = if (reducedMotion) {
                        CompaneroMotionDuration.FAST
                    } else {
                        CompaneroMotionDuration.STANDARD
                    }
                    val enter = fadeIn(tween(duration)) +
                        if (reducedMotion) {
                            slideInHorizontally(tween(0)) { 0 }
                        } else {
                            slideInHorizontally(tween(duration)) { it / 8 }
                        }
                    val exit = fadeOut(tween(duration)) +
                        if (reducedMotion) {
                            slideOutHorizontally(tween(0)) { 0 }
                        } else {
                            slideOutHorizontally(tween(duration)) { -it / 8 }
                        }
                    enter.togetherWith(exit)
                },
                label = "agendaMode",
            ) { currentMode ->
                when (currentMode) {
                    AgendaMode.DAY -> DayAgenda(
                        entries = state.entries,
                        selectedDay = selectedDay,
                        onSelectedDay = { selectedDay = it },
                    )
                    AgendaMode.WEEK -> WeekAgenda(state.entries)
                }
            }
        }

        if (state.entries.isNotEmpty()) {
            TextButton(
                onClick = viewModel::load,
                enabled = !state.loading,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(if (state.loading) "Actualizando…" else "Actualizar")
            }
        }
    }
}

@Composable
private fun DayAgenda(
    entries: List<ScheduleEntry>,
    selectedDay: String,
    onSelectedDay: (String) -> Unit,
) {
    val days = entries.map { it.dayOfWeek }.distinct()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
    ) {
        days.forEach { day ->
            FilterChip(
                selected = selectedDay == day,
                onClick = { onSelectedDay(day) },
                label = { Text(dayShortLabel(day)) },
            )
        }
    }

    val dayEntries = entries.filter { it.dayOfWeek == selectedDay }
    if (dayEntries.isEmpty()) {
        StatusNotice(
            title = dayLabel(selectedDay),
            message = "No tienes clases programadas este día.",
        )
    } else {
        Text(
            text = dayLabel(selectedDay),
            style = MaterialTheme.typography.titleLarge,
        )
        dayEntries.forEach { entry ->
            AcademicTimelineItem(
                time = entry.startsAt,
                title = entry.subjectName,
                subtitle = locationAndTeacher(entry),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = CompaneroSpacing.xxs),
            )
        }
    }
}

@Composable
private fun WeekAgenda(entries: List<ScheduleEntry>) {
    entries.groupBy { it.dayOfWeek }.forEach { (day, dayEntries) ->
        Text(
            text = dayLabel(day),
            style = MaterialTheme.typography.titleLarge,
        )
        dayEntries.forEach { entry ->
            AcademicTimelineItem(
                time = entry.startsAt,
                title = entry.subjectName,
                subtitle = locationAndTeacher(entry),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = CompaneroSpacing.xxs),
            )
        }
    }
}

private fun locationAndTeacher(entry: ScheduleEntry): String {
    val location = listOfNotNull(entry.classroomName, entry.buildingName)
        .joinToString(" · ")
        .ifBlank { "Aula por confirmar" }
    return "$location · ${entry.teacherName}"
}

private fun dayShortLabel(day: String): String = when (day) {
    "MONDAY" -> "Lun"
    "TUESDAY" -> "Mar"
    "WEDNESDAY" -> "Mié"
    "THURSDAY" -> "Jue"
    "FRIDAY" -> "Vie"
    "SATURDAY" -> "Sáb"
    "SUNDAY" -> "Dom"
    else -> day.take(3)
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
