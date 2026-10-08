package org.companerodeescuela.feature.attendance

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.designsystem.v8.V8BrandHeader
import org.companerodeescuela.core.designsystem.v8.V8CampusBackdrop
import org.companerodeescuela.core.designsystem.v8.V8ClassSummary
import org.companerodeescuela.core.designsystem.v8.V8DailyClassRow
import org.companerodeescuela.core.designsystem.v8.V8DashboardStat
import org.companerodeescuela.core.designsystem.v8.V8GlassCard
import org.companerodeescuela.core.designsystem.v8.V8HeroTitle
import org.companerodeescuela.core.designsystem.v8.V8RedColors
import org.companerodeescuela.core.designsystem.v8.V8RedPrimaryButton
import org.companerodeescuela.core.ui.component.StatusNotice
import org.companerodeescuela.shared.contracts.AttendanceStatus

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
        V8CampusBackdrop(modifier = Modifier.matchParentSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = CompaneroSize.homeContentMaxWidth)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CompaneroSpacing.page, vertical = CompaneroSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.section),
        ) {
            V8BrandHeader()
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Panel docente",
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
                val upcoming = sessions.filter { it.date >= today }
                val records = state.roster?.records.orEmpty()
                val review = records.count { it.status == AttendanceStatus.REVIEW_REQUIRED }
                val active = state.teacherSession
                val campus = state.campusRoster

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    V8DashboardStat(
                        label = "Clases hoy",
                        value = sessions.count { it.date == today }.toString(),
                        onClick = onOpenSchedule,
                        modifier = Modifier.weight(1f),
                    )
                    V8DashboardStat(
                        label = "Pases activos",
                        value = state.activeSessions.size.toString(),
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
                        value = if (active == null || state.roster == null) "—" else records.size.toString(),
                        onClick = onOpenAttendance,
                        modifier = Modifier.weight(1f),
                    )
                    V8DashboardStat(
                        label = "Por revisar",
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
                            Text("${campus.students.count { it.campusEntryAtEpochSeconds != null }} de ${campus.students.size} integrantes con entrada validada",
                                style = MaterialTheme.typography.bodyMedium, color = V8RedColors.TextSecondary)
                            campus.students.take(3).forEach { student ->
                                Text("${student.studentId} · " +
                                    (if (student.campusEntryAtEpochSeconds != null) "Registró entrada" else "Sin registro activo"),
                                    style = MaterialTheme.typography.bodySmall, color = V8RedColors.TextSecondary)
                            }
                        }
                    }
                } else if (state.campusRosterError != null) {
                    StatusNotice(title = "Padrón escolar no disponible",
                        message = state.campusRosterError!!)
                }

                Text("Pase de lista", style = MaterialTheme.typography.titleLarge, color = V8RedColors.TextPrimary)
                V8GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

                Text("Herramientas docentes", style = MaterialTheme.typography.titleLarge, color = V8RedColors.TextPrimary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TeacherToolCard("Mis clases", "Grupos asignados", onOpenClassrooms, Modifier.weight(1f))
                    TeacherToolCard("Mi horario", "Agenda semanal", onOpenSchedule, Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TeacherToolCard("Canal", "Avisos y archivos", onOpenChannel, Modifier.weight(1f))
                    TeacherToolCard("Evaluación", "Excel y calificaciones", onOpenGrading, Modifier.weight(1f))
                }

                Text("Próximas clases", style = MaterialTheme.typography.titleLarge, color = V8RedColors.TextPrimary)
                if (upcoming.isEmpty()) {
                    StatusNotice(
                        title = "Sin próximas clases asignadas",
                        message = "Control escolar asigna las clases. Cuando estén disponibles, aparecerán aquí.",
                    )
                } else {
                    upcoming.take(4).forEach { occurrence ->
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    V8GlassCard(modifier = modifier.clickable(onClick = onClick)) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = V8RedColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = V8RedColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}
