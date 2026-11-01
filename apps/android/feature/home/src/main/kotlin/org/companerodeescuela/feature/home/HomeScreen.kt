@file:Suppress("ktlint:standard:function-naming")

package org.companerodeescuela.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.designsystem.theme.CompaneroWindowBreakpoints
import org.companerodeescuela.core.designsystem.v8.V8ClassSummary
import org.companerodeescuela.core.designsystem.v8.V8DailyClassRow
import org.companerodeescuela.core.designsystem.v8.V8DashboardStat
import org.companerodeescuela.core.designsystem.v8.V8GlassCard
import org.companerodeescuela.core.designsystem.v8.V8RedPrimaryButton
import org.companerodeescuela.core.designsystem.v8.V8ScreenHeader
import org.companerodeescuela.core.designsystem.v8.V8SectionTitle
import org.companerodeescuela.core.motion.LocalCompaneroMotionPreferences
import org.companerodeescuela.core.ui.component.AcademicClassCard
import org.companerodeescuela.core.ui.component.AcademicSyncStatusCard
import org.companerodeescuela.core.ui.component.StatusNotice
import org.companerodeescuela.shared.contracts.ScheduleEntry
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onOpenSchedule: () -> Unit = {},
    onOpenClassrooms: () -> Unit = {},
    onOpenChannel: () -> Unit = {},
    onOpenAttendance: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
    }

    val overview = state.overview
    val reducedMotion = LocalCompaneroMotionPreferences.current.reducedMotion
    val haptic = LocalHapticFeedback.current
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val splitLayout = maxWidth >= CompaneroWindowBreakpoints.medium
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.refresh()
            },
            modifier = Modifier.fillMaxSize().align(Alignment.TopCenter),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .widthIn(max = CompaneroSize.homeContentMaxWidth)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = CompaneroSpacing.page, vertical = CompaneroSpacing.sm)
                        .align(Alignment.TopCenter),
                verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.section),
            ) {
                val firstName =
                    overview
                        ?.studentName
                        ?.trim()
                        ?.substringBefore(" ")
                        ?.takeIf { it.isNotBlank() }
                        ?.replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(Locale("es", "MX")) else it.toString()
                        }
                V8ScreenHeader {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text =
                                buildAnnotatedString {
                                    append("Hola")
                                    if (firstName != null) {
                                        append(", ")
                                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append(firstName) }
                                    }
                                },
                            modifier = Modifier.padding(top = 8.dp),
                            fontSize = 32.sp,
                            lineHeight = 38.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                V8GlassCard(modifier = Modifier.fillMaxWidth(), emphasized = true) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                todayLabel(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium,
                            )
                            TextButton(onClick = onOpenClassrooms) {
                                Text("Mis clases  ›", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        val activeOrNext = overview?.current ?: overview?.next
                        if (activeOrNext != null) {
                            Text(
                                text =
                                    if (overview?.current !=
                                        null
                                    ) {
                                        "EN CURSO · ${activeOrNext.subjectName}"
                                    } else {
                                        "PRÓXIMA · ${activeOrNext.subjectName}"
                                    },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "${activeOrNext.startsAt} – ${activeOrNext.endsAt} · ${locationLabel(activeOrNext)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        V8RedPrimaryButton(
                            text = if (state.personalOnly || state.institutionalAccessDenied) "Ver mi horario" else "Ver pase de lista",
                            onClick = if (state.personalOnly || state.institutionalAccessDenied) onOpenSchedule else onOpenAttendance,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                V8QuickAccessGrid(
                    onOpenSchedule = onOpenSchedule,
                    onOpenAttendance = onOpenAttendance,
                    onOpenChannel = onOpenChannel,
                    onOpenClassrooms = onOpenClassrooms,
                )

                if (state.loading && overview == null) CircularProgressIndicator()
                if (state.fromCache || state.personalOnly || state.errorMessage != null) {
                    AcademicSyncStatusCard(
                        title =
                            when {
                                state.institutionalAccessDenied -> "Vinculación escolar pendiente"
                                state.errorMessage != null -> "Sincronización pendiente"
                                state.personalOnly -> "Agenda personal"
                                state.fromCache -> cacheStatusLabel(state.updatedAtEpochSeconds)
                                else -> "No pudimos actualizar"
                            },
                        message =
                            when {
                                state.institutionalAccessDenied -> "Vincula tu cuenta con SIII para consultar las clases oficiales."
                                state.errorMessage != null && overview != null ->
                                    "No pudimos consultar SIII. Tu agenda guardada sigue disponible."
                                state.errorMessage != null -> "Comprueba tu sesión o conexión y vuelve a intentarlo."
                                state.personalOnly -> "No hay clases oficiales asignadas a esta cuenta."
                                state.fromCache -> "Tu agenda está disponible mientras recuperas la conexión."
                                else -> "Comprueba tu sesión o conexión y vuelve a intentarlo."
                            },
                        refreshing = state.loading,
                        onRefresh = viewModel::refresh,
                    )
                }

                overview?.let { day ->
                    val distinctSubjectCount =
                        day.classes
                            .distinctBy(::scheduleSubjectKey)
                            .size

                    if (splitLayout) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.section),
                        ) {
                            Column(
                                modifier = Modifier.weight(1.2f),
                                verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.section),
                            ) {
                                HomeDailyStats(day, distinctSubjectCount, onOpenSchedule, onOpenClassrooms)
                                when {
                                    !day.hasSchedule -> {
                                        StatusNotice(
                                            title = "Aún no hay clases en tu horario",
                                            message =
                                                "Importa una imagen o PDF o crea tu agenda personal; " +
                                                    "las clases asignadas desde SIII aparecerán al sincronizar.",
                                        )
                                        V8RedPrimaryButton(
                                            text = "Preparar mi agenda",
                                            onClick = onOpenSchedule,
                                            modifier = Modifier.fillMaxWidth(),
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
                                                    time =
                                                        upcomingDayLabel(day.nextScheduledDaysAway) +
                                                            " · " + upcoming.startsAt + " – " + upcoming.endsAt,
                                                    location = locationLabel(upcoming),
                                                    teacher = upcoming.teacherName,
                                                    subjectKey = scheduleSubjectKey(upcoming),
                                                )
                                            }
                                        }
                                    }
                                    else -> {
                                        DayTimeline(day, onOpenSchedule)
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.section),
                            ) {
                                Text(
                                    "Avisos y accesos",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                )
                                V8GlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "Canal de tus clases",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = "Consulta avisos y materiales publicados por tus docentes.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        TextButton(onClick = onOpenChannel) {
                                            Text("Ver canales y avisos  ›", color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                                TextButton(
                                    onClick = viewModel::refresh,
                                    enabled = !state.loading,
                                    modifier = Modifier.align(Alignment.End),
                                ) {
                                    Text(
                                        if (state.loading) "Actualizando…" else "Actualizar",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    } else {
                        HomeDailyStats(day, distinctSubjectCount, onOpenSchedule, onOpenClassrooms)
                        when {
                            !day.hasSchedule -> {
                                StatusNotice(
                                    title = "Aún no hay clases en tu horario",
                                    message =
                                        "Importa una imagen o PDF o crea tu agenda personal; " +
                                            "las clases asignadas desde SIII aparecerán al sincronizar.",
                                )
                                V8RedPrimaryButton(
                                    text = "Preparar mi agenda",
                                    onClick = onOpenSchedule,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    text =
                                        "La agenda local funciona sin integración escolar y conserva " +
                                            "tus bloques personales, importaciones y recordatorios.",
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
                                            time =
                                                upcomingDayLabel(day.nextScheduledDaysAway) +
                                                    " · " + upcoming.startsAt + " – " + upcoming.endsAt,
                                            location = locationLabel(upcoming),
                                            teacher = upcoming.teacherName,
                                            subjectKey = scheduleSubjectKey(upcoming),
                                        )
                                    }
                                }
                            }
                            else -> {
                                DayTimeline(day, onOpenSchedule)
                            }
                        }

                        Text(
                            "Avisos",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                        V8GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Canal de tus clases",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "Consulta avisos y materiales publicados por tus docentes.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(onClick = onOpenChannel) {
                                    Text("Ver canales y avisos  ›", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        TextButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.refresh()
                            },
                            enabled = !state.loading,
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text(if (state.loading) "Actualizando…" else "Actualizar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayTimeline(
    day: TodayOverview,
    onOpenSchedule: () -> Unit,
) {
    V8SectionTitle("Horario de hoy")
    day.classes.forEach { entry ->
        val status = classStatus(entry, day.current, day.next)
        V8DailyClassRow(
            classInfo =
                V8ClassSummary(
                    id = scheduleSubjectKey(entry) + ":" + entry.dayOfWeek + ":" + entry.startsAt,
                    title = entry.subjectName,
                    start = entry.startsAt,
                    end = entry.endsAt,
                    room = locationLabel(entry),
                ),
            isNext = status.highlighted || status.label == "Siguiente",
            statusLabel = status.label,
            onClick = { onOpenSchedule() },
        )
    }
}

@Composable
private fun HomeDailyStats(
    day: TodayOverview,
    subjectCount: Int,
    onSchedule: () -> Unit,
    onClasses: () -> Unit,
) {
    val largeText = LocalDensity.current.fontScale >= 1.3f
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            V8DashboardStat("Clases hoy", day.classes.size.toString(), onSchedule, Modifier.weight(1f).fillMaxHeight())
            V8DashboardStat("Materias hoy", subjectCount.toString(), onClasses, Modifier.weight(1f).fillMaxHeight())
            if (!largeText) V8DashboardStat("Próxima", nextClassStatLabel(day), onSchedule, Modifier.weight(1f).fillMaxHeight())
        }
        if (largeText) V8DashboardStat("Próxima clase", nextClassStatLabel(day), onSchedule, Modifier.fillMaxWidth(), compact = true)
    }
}

internal data class ClassStatus(
    val label: String?,
    val highlighted: Boolean,
)

internal fun classStatus(
    entry: ScheduleEntry,
    current: ScheduleEntry?,
    next: ScheduleEntry?,
    now: LocalTime = LocalTime.now(),
): ClassStatus {
    if (entry == current) return ClassStatus("Ahora", true)
    if (entry == next) return ClassStatus("Siguiente", false)

    val end = runCatching { LocalTime.parse(entry.endsAt) }.getOrNull()
    return if (end != null && !now.isBefore(end)) {
        ClassStatus("Finalizada", false)
    } else {
        ClassStatus(null, false)
    }
}

/** Local timestamp for an existing cached snapshot, never a fabricated sync time. */
internal fun cacheStatusLabel(
    updatedAtEpochSeconds: Long?,
    zone: ZoneId = ZoneId.systemDefault(),
    today: LocalDate = LocalDate.now(zone),
): String {
    val lastUpdated =
        updatedAtEpochSeconds
            ?.takeIf { it > 0L }
            ?.let { Instant.ofEpochSecond(it).atZone(zone) }
            ?: return "Agenda guardada"
    val pattern = if (lastUpdated.toLocalDate() == today) "HH:mm" else "d MMM · HH:mm"
    return "Agenda guardada · " +
        lastUpdated.format(DateTimeFormatter.ofPattern(pattern, Locale("es", "MX")))
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
    val date = LocalDate.now().plusDays(daysAway.toLong()).format(formatter)
    return date.replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale("es", "MX")) else it.toString()
    }
}

private fun todayLabel(): String {
    val formatter =
        DateTimeFormatter.ofPattern(
            "EEEE d 'de' MMMM",
            Locale("es", "MX"),
        )
    val date = LocalDate.now().format(formatter)
    return date.replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale("es", "MX")) else it.toString()
    }
}

@Composable
private fun V8QuickAccessGrid(
    onOpenSchedule: () -> Unit,
    onOpenAttendance: () -> Unit,
    onOpenChannel: () -> Unit,
    onOpenClassrooms: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val items = listOf(
            Triple("Horario", Icons.Filled.DateRange, onOpenSchedule),
            Triple("Asistencia", Icons.Filled.CheckCircle, onOpenAttendance),
            Triple("Avisos", Icons.Filled.Campaign, onOpenChannel),
            Triple("Tareas", Icons.AutoMirrored.Filled.Assignment, onOpenClassrooms),
        )
        items.forEach { (label, icon, onClick) ->
            Surface(
                modifier = Modifier.weight(1f).clickable(onClick = onClick),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
