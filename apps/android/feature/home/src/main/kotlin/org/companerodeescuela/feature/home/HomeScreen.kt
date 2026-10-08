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
import androidx.compose.ui.unit.sp
import org.companerodeescuela.core.designsystem.v8.V8RedColors
import org.companerodeescuela.core.designsystem.v8.V8GlassCard
import org.companerodeescuela.core.designsystem.v8.V8ClassSummary
import org.companerodeescuela.core.designsystem.v8.V8DailyClassRow
import org.companerodeescuela.core.designsystem.v8.V8CampusBackdrop
import org.companerodeescuela.core.designsystem.v8.V8BrandHeader
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
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val overview = state.overview
    val reducedMotion = LocalCompaneroMotionPreferences.current.reducedMotion
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        V8CampusBackdrop(modifier = Modifier.matchParentSize())
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
            V8BrandHeader()
            Text(
                text = overview?.studentName
                    ?.substringBefore(" ")
                    ?.let { "Hola, $it" }
                    ?: "Hoy",
                modifier = Modifier.padding(end = 52.dp),
                style = MaterialTheme.typography.headlineLarge,
                color = V8RedColors.TextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = todayLabel(),
                style = MaterialTheme.typography.bodyLarge,
                color = V8RedColors.TextSecondary,
            )

            Button(
                onClick = onOpenClassrooms,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Mis clases")
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
                    V8DashboardStat(label = "Clases hoy", value = day.classes.size.toString(), onClick = onOpenSchedule, modifier = Modifier.weight(1f))
                    V8DashboardStat(label = "Siguiente", value = day.next?.subjectName ?: "—", onClick = onOpenSchedule, modifier = Modifier.weight(1f))
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

                TextButton(
                    onClick = viewModel::refresh,
                    enabled = !state.loading,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(if (state.loading) "Actualizando…" else "Actualizar")
                }
            }
        }
    }
}

@Composable
private fun TodayContextPanel(
    day: TodayOverview,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val current = day.current
    if (current == null) {
        val next = day.next
        StatusNotice(
            title = if (next != null) "Entre clases" else "Terminaste tus clases de hoy",
            message = if (next != null) {
                "Tu siguiente clase comienza a las " + next.startsAt + "."
            } else {
                "No tienes más clases programadas hoy."
            },
            modifier = modifier,
        )
        return
    }

    AnimatedContent(
        modifier = modifier,
        targetState = current,
        transitionSpec = {
            val duration = if (reducedMotion) {
                CompaneroMotionDuration.FAST
            } else {
                CompaneroMotionDuration.STANDARD
            }
            (
                fadeIn(tween(duration)) +
                    scaleIn(
                        initialScale = if (reducedMotion) 1f else 0.99f,
                        animationSpec = tween(duration),
                    )
                ).togetherWith(
                    fadeOut(tween(duration)) +
                        scaleOut(
                            targetScale = if (reducedMotion) 1f else 0.99f,
                            animationSpec = tween(duration),
                        ),
                )
        },
        label = "currentClassHero",
    ) { classEntry ->
        HeroAcademicCard(
            subject = classEntry.subjectName,
            time = classEntry.startsAt + " – " + classEntry.endsAt,
            location = locationLabel(classEntry),
            teacher = classEntry.teacherName,
            progress = classProgress(classEntry),
            supportingText = remainingLabel(classEntry.endsAt, "Termina"),
        )
    }
}

@Composable
private fun NextClassPanel(
    day: TodayOverview,
    modifier: Modifier = Modifier,
) {
    val next = day.next?.takeIf { it != day.current } ?: return
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
    ) {
        Text(
            text = "Siguiente",
            style = MaterialTheme.typography.titleLarge,
        )
        AcademicClassCard(
            subject = next.subjectName,
            time = next.startsAt + " – " + next.endsAt,
            location = locationLabel(next),
            teacher = next.teacherName,
            supportingText = remainingLabel(next.startsAt, "Comienza"),
            subjectKey = next.subjectCode.ifBlank { next.subjectName },
        )
    }
}

@Composable
private fun DayTimeline(day: TodayOverview, onOpenSchedule: () -> Unit) {
    Text(
        text = "Horario de hoy",
        style = MaterialTheme.typography.titleLarge,
        color = V8RedColors.TextPrimary,
        fontWeight = FontWeight.Bold,
    )
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

private fun remainingLabel(time: String, verb: String): String? {
    val target = runCatching { LocalTime.parse(time) }.getOrNull() ?: return null
    val minutes = java.time.Duration.between(LocalTime.now(), target).toMinutes()
    return when {
        minutes > 1 -> verb + " en " + minutes + " min"
        minutes == 1L -> verb + " en 1 min"
        minutes == 0L -> verb + " ahora"
        else -> null
    }
}

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
