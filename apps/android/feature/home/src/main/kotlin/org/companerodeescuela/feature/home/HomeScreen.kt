package org.companerodeescuela.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import org.companerodeescuela.core.designsystem.v8.V8RedColors
import org.companerodeescuela.core.designsystem.v8.V8GlassCard
import org.companerodeescuela.core.designsystem.v8.V8ClassSummary
import org.companerodeescuela.core.designsystem.v8.V8DailyClassRow
import org.companerodeescuela.core.designsystem.v8.V8DashboardStat
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.designsystem.theme.CompaneroWindowBreakpoints
import org.companerodeescuela.core.motion.CompaneroMotionDuration
import org.companerodeescuela.core.motion.LocalCompaneroMotionPreferences
import org.companerodeescuela.core.ui.component.AcademicClassCard
import org.companerodeescuela.core.ui.component.AcademicTimelineItem
import org.companerodeescuela.core.ui.component.HeroAcademicCard
import org.companerodeescuela.core.ui.component.NoticeTone
import org.companerodeescuela.core.ui.component.StatusNotice
import org.companerodeescuela.shared.contracts.ScheduleEntry

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onOpenSchedule: () -> Unit = {},
    onOpenClassrooms: () -> Unit = {},
    onOpenChannel: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
    }

    val overview = state.overview
    val reducedMotion = LocalCompaneroMotionPreferences.current.reducedMotion
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val splitLayout = maxWidth >= CompaneroWindowBreakpoints.medium
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = CompaneroSize.homeContentMaxWidth)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CompaneroSpacing.page, vertical = CompaneroSpacing.sm)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.section),
        ) {
            val firstName = overview?.studentName?.substringBefore(" ")?.takeIf { it.isNotBlank() }
            Text(
                text = buildAnnotatedString {
                    append("Hola")
                    if (firstName != null) {
                        append(", ")
                        withStyle(SpanStyle(color = V8RedColors.Crimson)) { append(firstName) }
                    }
                },
                modifier = Modifier.padding(top = 8.dp, end = 42.dp),
                fontSize = 37.sp,
                lineHeight = 43.sp,
                color = V8RedColors.TextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Un gran día para seguir construyendo tus metas. ✨",
                fontSize = 16.sp,
                lineHeight = 22.sp,
                color = V8RedColors.TextSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(todayLabel(), color = V8RedColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = onOpenClassrooms) {
                    Text("Mis clases  ›", color = V8RedColors.Crimson)
                }
            }

            if (state.loading && overview == null) {
                CircularProgressIndicator()
            }

            if (state.fromCache) {
                StatusNotice(
                    title = "Usando información guardada",
                    message = "Tu agenda sigue disponible. La integración escolar se puede actualizar después desde Configuración.",
                    tone = NoticeTone.WARNING,
                )
            }

            if (state.errorMessage != null && overview == null) {
                StatusNotice(
                    title = "No pudimos actualizar",
                    message = "Compañero puede seguir funcionando con tu agenda local. Reintenta cuando tengas conexión.",
                    tone = NoticeTone.WARNING,
                )
                Button(onClick = viewModel::refresh) {
                    Text("Reintentar")
                }
            }

            overview?.let { day ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    V8DashboardStat(
                        label = "Clases hoy",
                        value = day.classes.size.toString(),
                        onClick = onOpenSchedule,
                        modifier = Modifier.weight(1f),
                    )
                    V8DashboardStat(
                        label = "Materias hoy",
                        value = day.classes.distinctBy { entry -> entry.subjectCode.ifBlank { entry.subjectName } }.size.toString(),
                        onClick = onOpenClassrooms,
                        modifier = Modifier.weight(1f),
                    )
                    V8DashboardStat(
                        label = "Próxima clase",
                        value = day.next?.subjectName ?: "—",
                        onClick = onOpenSchedule,
                        modifier = Modifier.weight(1f),
                    )
                }
                when {
                    !day.hasSchedule -> {
                        StatusNotice(
                            title = "Todavía no tienes horario",
                            message = "Importa una imagen o PDF, crea tu agenda manualmente o conecta el sistema escolar desde Configuración.",
                        )
                        Button(
                            onClick = onOpenSchedule,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Preparar mi agenda")
                        }
                        Text(
                            text = "La agenda local funciona sin integración escolar y conserva tus bloques personales, importaciones y recordatorios.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    day.classes.isEmpty() -> {
                        StatusNotice(
                            title = "Día libre",
                            message = "Hoy no tienes clases. Tu próxima actividad académica sigue visible abajo.",
                        )
                        day.nextScheduled?.let { upcoming ->
                            Column(
                                verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
                            ) {
                                Text(
                                    text = "Próxima clase",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                AcademicClassCard(
                                    subject = upcoming.subjectName,
                                    time = upcomingDayLabel(day.nextScheduledDaysAway) +
                                        " · " + upcoming.startsAt + " – " + upcoming.endsAt,
                                    location = locationLabel(upcoming),
                                    teacher = upcoming.teacherName,
                                    subjectKey = upcoming.subjectCode.ifBlank { upcoming.subjectName },
                                )
                            }
                        }
                    }
                    else -> {
                        DayTimeline(day, onOpenSchedule)
                    }
                }

                Text("Avisos", style = MaterialTheme.typography.titleLarge, color = V8RedColors.TextPrimary, fontWeight = FontWeight.Bold)
                V8GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Canal de tus clases",
                            style = MaterialTheme.typography.titleMedium,
                            color = V8RedColors.TextPrimary,
                        )
                        Text(
                            text = "Consulta avisos y materiales publicados por tus docentes.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = V8RedColors.TextSecondary,
                        )
                        TextButton(onClick = onOpenChannel) {
                            Text("Ver canales y avisos  ›", color = V8RedColors.Crimson)
                        }
                    }
                }
                TextButton(
                    onClick = viewModel::refresh,
                    enabled = !state.loading,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(if (state.loading) "Actualizando…" else "Actualizar", color = V8RedColors.TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun DayTimeline(day: TodayOverview, onOpenSchedule: () -> Unit) {
    org.companerodeescuela.core.designsystem.v8.V8SectionTitle("Horario de hoy")
    day.classes.forEach { entry ->
        val status = classStatus(entry, day.current, day.next)
        V8DailyClassRow(
            classInfo = V8ClassSummary(
                id = entry.subjectCode.ifBlank { entry.subjectName },
                title = entry.subjectName,
                start = entry.startsAt,
                end = entry.endsAt,
                room = locationLabel(entry),
            ),
            isNext = status.highlighted || status.label == "Siguiente",
            onClick = { onOpenSchedule() },
        )
    }
}

private data class ClassStatus(
    val label: String?,
    val highlighted: Boolean,
)

private fun classStatus(
    entry: ScheduleEntry,
    current: ScheduleEntry?,
    next: ScheduleEntry?,
): ClassStatus {
    if (entry == current) return ClassStatus("Ahora", true)
    if (entry == next) return ClassStatus("Siguiente", false)

    val end = runCatching { LocalTime.parse(entry.endsAt) }.getOrNull()
    return if (end != null && LocalTime.now().isAfter(end)) {
        ClassStatus("Finalizada", false)
    } else {
        ClassStatus(null, false)
    }
}

private fun locationLabel(entry: ScheduleEntry): String =
    listOfNotNull(entry.classroomName, entry.buildingName)
        .joinToString(" · ")
        .ifBlank { "Aula por confirmar" }

private fun upcomingDayLabel(daysAway: Int?): String {
    if (daysAway == null) return "Próximamente"
    if (daysAway == 0) return "Hoy"
    if (daysAway == 1) return "Mañana"
    val formatter = DateTimeFormatter.ofPattern("EEEE", Locale("es", "MX"))
    return LocalDate.now()
        .plusDays(daysAway.toLong())
        .format(formatter)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("es", "MX")) else it.toString() }
}

private fun todayLabel(): String {
    val formatter = DateTimeFormatter.ofPattern(
        "EEEE d 'de' MMMM",
        Locale("es", "MX"),
    )
    return LocalDate.now()
        .format(formatter)
        .replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale("es", "MX")) else it.toString()
        }
}
