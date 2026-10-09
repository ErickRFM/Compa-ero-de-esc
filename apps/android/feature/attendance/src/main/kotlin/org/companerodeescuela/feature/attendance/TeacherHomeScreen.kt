package org.companerodeescuela.feature.attendance

import org.companerodeescuela.core.designsystem.v8.V8ScreenHeader
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.ui.graphics.vector.ImageVector
import org.companerodeescuela.core.designsystem.v8.V8IconTile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.LocalTime
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.designsystem.v8.V8CampusBackdrop
import org.companerodeescuela.core.designsystem.v8.V8ClassSummary
import org.companerodeescuela.core.designsystem.v8.V8DailyClassRow
import org.companerodeescuela.core.designsystem.v8.V8DashboardStat
import org.companerodeescuela.core.designsystem.v8.V8GlassCard
import org.companerodeescuela.core.designsystem.v8.V8HeroTitle
import org.companerodeescuela.core.designsystem.v8.V8RedColors
import org.companerodeescuela.core.designsystem.v8.V8RedPrimaryButton
import org.companerodeescuela.core.ui.component.StatusNotice

/**
 * Teacher dashboard backed by the authorized teacher's occurrences/active roster.
 * Class assignment stays with school administration; teachers cannot create classes here.
 */
@Composable
fun TeacherHomeScreen(
    onOpenAttendance: () -> Unit,
    onOpenChannel: () -> Unit,
    onOpenClassrooms: () -> Unit = {},
    onOpenGrading: () -> Unit = {},
    onOpenSchedule: () -> Unit = {},
    displayName: String? = null,
    modifier: Modifier = Modifier,
    viewModel: AttendanceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Bootstrap can select STUDENT for a dual-role account; switch only when needed.
    // Existing session-claim verification still guards teacher mode.
    LaunchedEffect(state.mode) {
        if (state.mode == AttendanceMode.STUDENT) {
            viewModel.selectMode(AttendanceMode.TEACHER)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (!org.companerodeescuela.core.designsystem.v8.LocalV8GlassEnabled.current) {
            V8CampusBackdrop(modifier = Modifier.matchParentSize())
        }
        Column(
            modifier = Modifier.align(Alignment.TopCenter)
                .widthIn(max = CompaneroSize.homeContentMaxWidth).fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CompaneroSpacing.page, vertical = CompaneroSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.section),
        ) {
            V8ScreenHeader {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Inicio docente",
                        style = MaterialTheme.typography.headlineLarge,
                        color = V8RedColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    val greeting = displayName?.trim()?.takeIf { it.isNotEmpty() }
                    Text(
                        text = greeting?.let { "Hola, $it · Tus clases asignadas" }
                            ?: "Consulta tus clases asignadas y organiza la jornada.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = V8RedColors.TextSecondary,
                    )
                }
            }

            if (state.mode == AttendanceMode.LOADING || state.loading && state.occurrences.isEmpty()) {
                CircularProgressIndicator(color = V8RedColors.Crimson)
            } else if (state.mode != AttendanceMode.TEACHER) {
                StatusNotice(
                    title = "Acceso docente pendiente",
                    message = "Tu cuenta todavía no tiene autorización docente activa. Solicita a control escolar la validación de tu perfil.",
                )
            } else {
                state.errorMessage?.let {
                    StatusNotice(title = "Datos académicos no actualizados", message = it)
                }

                val today = LocalDate.now().toString()
                val sessions = state.occurrences.sortedWith(compareBy({ it.date }, { it.startsAt }))
                val upcoming = upcomingTeacherClasses(sessions, java.time.LocalDateTime.now())
                val records = state.roster?.records.orEmpty()
                val review = attendanceRecordsRequiringReviewCount(records)
                val active = state.teacherSession?.takeIf {
                    it.closedAtEpochSeconds == null && it.closesAtEpochSeconds > System.currentTimeMillis() / 1000
                }
                val campus = state.campusRoster

                V8GlassCard(modifier = Modifier.fillMaxWidth(), emphasized = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Pase de lista", style = MaterialTheme.typography.titleLarge, color = V8RedColors.TextPrimary, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (active != null) "EN VIVO · ${active.groupName}" else "Sin pase abierto",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (active != null) V8RedColors.Success else V8RedColors.TextSecondary,
                        )
                        Text(
                            text = if (active != null) {
                                sessions.firstOrNull { it.id == active.occurrenceId }?.subjectName
                                    ?: "Sesión de ${active.groupName}"
                            } else "Inicia el pase de una clase asignada",
                            style = MaterialTheme.typography.titleLarge,
                            color = V8RedColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = if (active != null) {
                                "${active.scheduledStartsAt} – ${active.scheduledEndsAt} · " +
                                    (if (state.roster == null) "Actualizando registros…" else "${records.size} registros recibidos")
                            } else "El QR se genera desde el servidor y se renueva automáticamente.",
                            color = V8RedColors.TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        V8RedPrimaryButton(
                            text = if (active == null) "Iniciar pase de lista" else "Gestionar pase activo",
                            onClick = onOpenAttendance,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                val nextClass = upcoming.firstOrNull { it.id != active?.occurrenceId }
                if (nextClass != null) {
                    V8GlassCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenSchedule)) {
                        Column(verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs)) {
                            Text("SIGUIENTE CLASE", color = V8RedColors.Crimson,
                                style = MaterialTheme.typography.labelLarge)
                            Text(nextClass.subjectName, color = V8RedColors.TextPrimary,
                                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                listOfNotNull(
                                    nextClass.date,
                                    nextClass.startsAt + " – " + nextClass.endsAt,
                                    nextClass.classroomName?.takeIf(String::isNotBlank),
                                ).joinToString(" · "),
                                color = V8RedColors.TextSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text("Ver detalles en Mi horario", color = V8RedColors.TextSecondary,
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                } else if (state.occurrencesLoaded) {
                    StatusNotice(title = "Sin siguiente clase", message = "No hay más clases vigentes en el horario consultado.")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    V8DashboardStat(
                        label = "Clases hoy",
                        compact = true,
                        value = if (state.occurrencesLoaded) sessions.count { it.date == today }.toString() else "—",
                        onClick = onOpenSchedule,
                        modifier = Modifier.weight(1f),
                    )
                    V8DashboardStat(
                        label = "Pases activos",
                        compact = true,
                        value = if (state.sessionsLoaded) state.activeSessions.size.toString() else "—",
                        onClick = onOpenAttendance,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    V8DashboardStat(
                        label = "Registros recibidos",
                        compact = true,
                        value = if (active == null || state.roster == null) "—" else records.size.toString(),
                        onClick = onOpenAttendance,
                        modifier = Modifier.weight(1f),
                    )
                    V8DashboardStat(
                        label = "Por revisar",
                        compact = true,
                        value = if (active == null || state.roster == null) "—" else review.toString(),
                        onClick = onOpenAttendance,
                        modifier = Modifier.weight(1f),
                    )
                }

                if (campus != null) {
                    V8GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Entrada escolar · ${campus.groupName}",
                                style = MaterialTheme.typography.titleMedium, color = V8RedColors.TextPrimary)
                            Text("${campus.students.count { it.campusEntryAtEpochSeconds != null }} de ${campus.students.size} integrantes con ingreso registrado",
                                style = MaterialTheme.typography.bodyMedium, color = V8RedColors.TextSecondary)
                            Text(
                                "Consulta el padrón completo y los retardos en Asistencia.",
                                style = MaterialTheme.typography.bodySmall,
                                color = V8RedColors.TextSecondary,
                            )
                            V8RedPrimaryButton(
                                text = "Abrir padrón",
                                onClick = onOpenAttendance,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                } else if (state.campusRosterError != null) {
                    StatusNotice(title = "Padrón escolar no disponible",
                        message = state.campusRosterError!!)
                }

                Text("Accesos rápidos", style = MaterialTheme.typography.titleLarge, color = V8RedColors.TextPrimary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TeacherToolCard("Mis clases", "Grupos asignados", Icons.Filled.MenuBook, onOpenClassrooms, Modifier.weight(1f))
                    TeacherToolCard("Mi horario", "Agenda semanal", Icons.Filled.CalendarMonth, onOpenSchedule, Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TeacherToolCard("Canal", "Avisos y archivos", Icons.Filled.Campaign, onOpenChannel, Modifier.weight(1f))
                    TeacherToolCard("Evaluación", "Excel y calificaciones", Icons.Filled.BarChart, onOpenGrading, Modifier.weight(1f))
                }

                Text("Tu agenda", style = MaterialTheme.typography.titleLarge, color = V8RedColors.TextPrimary)
                if (upcoming.isEmpty()) {
                    StatusNotice(
                        title = "Sin próximas clases asignadas",
                        message = "Control escolar asigna las clases. Cuando estén disponibles, aparecerán aquí.",
                    )
                } else {
                    upcoming.take(3).forEach { occurrence ->
                        V8DailyClassRow(
                            classInfo = V8ClassSummary(
                                id = occurrence.id,
                                title = occurrence.subjectName,
                                start = occurrence.startsAt,
                                end = occurrence.endsAt,
                                room = buildString {
                                    append(occurrence.date)
                                    val room = listOfNotNull(occurrence.classroomName, occurrence.buildingName)
                                        .filter { it.isNotBlank() }.joinToString(" · ")
                                    if (room.isNotEmpty()) append(" · $room")
                                },
                            ),
                            isNext = occurrence.id == upcoming.first().id,
                            onClick = { onOpenSchedule() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TeacherToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    V8GlassCard(modifier = modifier.heightIn(min = 76.dp).clickable(onClick = onClick)) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            V8IconTile(icon)
            Text(title, color = V8RedColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = V8RedColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}
