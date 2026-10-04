package org.companerodeescuela.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
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
    val reducedMotion = LocalCompaneroMotionPreferences.current.reducedMotion
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CompaneroSpacing.lg, vertical = CompaneroSpacing.md),
        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.md),
    ) {
        Text(
            text = overview?.studentName
                ?.substringBefore(" ")
                ?.let { "Hola, $it" }
                ?: "Hoy",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = todayLabel(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.fromCache) {
            StatusNotice(
                title = "Información guardada",
                message = "No pudimos actualizar ahora. Tu jornada sigue disponible sin conexión.",
                tone = NoticeTone.WARNING,
            )
        }

        state.errorMessage?.let { message ->
            StatusNotice(
                title = "No pudimos actualizar",
                message = message,
                tone = NoticeTone.ERROR,
            )
            Button(onClick = viewModel::refresh) {
                Text("Reintentar")
            }
        }

        overview?.let { day ->
            AnimatedContent(
                targetState = day.current,
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
            ) { current ->
                if (current != null) {
                    HeroAcademicCard(
                        subject = current.subjectName,
                        time = "${current.startsAt} – ${current.endsAt}",
                        location = locationLabel(current),
                        teacher = current.teacherName,
                        progress = classProgress(current),
                        supportingText = remainingLabel(current.endsAt, "Termina"),
                    )
                } else {
                    StatusNotice(
                        title = "Sin clase en este momento",
                        message = day.next?.let {
                            "Tu siguiente clase comienza a las ${it.startsAt}."
                        } ?: "Tu jornada académica de hoy no tiene otra clase programada.",
                    )
                }
            }

            day.next?.takeIf { next -> next != day.current }?.let { next ->
                Text(
                    text = "Siguiente",
                    style = MaterialTheme.typography.titleLarge,
                )
                AcademicClassCard(
                    subject = next.subjectName,
                    time = "${next.startsAt} – ${next.endsAt}",
                    location = locationLabel(next),
                    teacher = next.teacherName,
                    supportingText = remainingLabel(next.startsAt, "Comienza"),
                )
            }

            Text(
                text = "Tu día",
                style = MaterialTheme.typography.titleLarge,
            )

            if (day.classes.isEmpty()) {
                StatusNotice(
                    title = "Día libre",
                    message = "No tienes clases programadas para hoy.",
                )
            } else {
                day.classes.forEach { entry ->
                    val status = classStatus(entry, day.current, day.next)
                    AcademicTimelineItem(
                        time = entry.startsAt,
                        title = entry.subjectName,
                        subtitle = locationLabel(entry),
                        status = status.label,
                        highlighted = status.highlighted,
                        modifier = Modifier.fillMaxWidth(),
                    )
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
        minutes > 1 -> "$verb en $minutes min"
        minutes == 1L -> "$verb en 1 min"
        minutes == 0L -> "$verb ahora"
        else -> null
    }
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
