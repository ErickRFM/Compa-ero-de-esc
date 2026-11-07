@file:Suppress("ktlint:standard:function-naming")

package org.companerodeescuela.feature.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.companerodeescuela.core.designsystem.theme.LocalCompaneroHighContrast
import org.companerodeescuela.core.designsystem.theme.companeroAccentSurfaceColors
import org.companerodeescuela.core.designsystem.v8.V8LoadingCard
import org.companerodeescuela.core.motion.CompaneroMotion
import org.companerodeescuela.core.motion.CompaneroMotionDuration
import org.companerodeescuela.core.motion.LocalCompaneroMotionPreferences

/** Presentation only: attendance still requires the verified institutional state. */
@Composable
fun HomeAgendaCard(
    state: HomeUiState,
    onOpenSchedule: () -> Unit,
    onOpenAttendance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalCompaneroMotionPreferences.current.reducedMotion
    Crossfade(
        targetState = state.loading && state.overview == null,
        modifier = modifier.fillMaxWidth(),
        animationSpec = if (reduced) snap() else tween(CompaneroMotionDuration.STANDARD),
        label = "agendaAvailability",
    ) { waiting ->
        if (waiting) {
            V8LoadingCard(stringResource(org.companerodeescuela.core.designsystem.R.string.ux_loading_agenda))
        } else {
            HomeAgendaContent(state, onOpenSchedule, onOpenAttendance)
        }
    }
}

@Composable
private fun HomeAgendaContent(
    state: HomeUiState,
    onOpenSchedule: () -> Unit,
    onOpenAttendance: () -> Unit,
) {
    // Crossfade's outgoing branch receives the latest state during an error retry.
    // Stop presenting an empty agenda before the incoming loading branch takes over.
    if (state.loading && state.overview == null) return
    val reduced = LocalCompaneroMotionPreferences.current.reducedMotion
    val overview = state.overview
    val accent = companeroAccentSurfaceColors()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = accent.background,
        shadowElevation = if (LocalCompaneroHighContrast.current) 0.dp else 2.dp,
        border = if (LocalCompaneroHighContrast.current) BorderStroke(2.dp, accent.foreground) else null,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val activeOrNext = featuredHomeClass(overview)
            val institutionalReady =
                !state.personalOnly &&
                    !state.institutionalAccessDenied &&
                    !state.fromCache &&
                    state.errorMessage == null

            Text(
                text =
                    when {
                        activeOrNext == null -> "MI AGENDA"
                        overview?.current != null -> "CLASE EN CURSO"
                        else -> "PRÓXIMA CLASE"
                    },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = accent.foreground.copy(alpha = 0.88f),
            )
            Text(
                text = activeOrNext?.subjectName ?: if (state.errorMessage != null) "Agenda no disponible" else "Sin clases próximas",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = accent.foreground,
            )
            if (activeOrNext != null) {
                activeOrNext.teacherName?.takeIf { it.isNotBlank() }?.let { teacher ->
                    Text(
                        text = "Docente · $teacher",
                        style = MaterialTheme.typography.bodyMedium,
                        color = accent.foreground.copy(alpha = 0.92f),
                    )
                }
                val currentClass = overview?.current
                if (currentClass != null) {
                    val rawProgress = classProgress(currentClass)
                    val animatedProgress by animateFloatAsState(
                        targetValue = rawProgress,
                        animationSpec = if (reduced) snap() else CompaneroMotion.standard(),
                        label = "homeClassProgress",
                    )
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(percent = 50)),
                        color = accent.foreground,
                        trackColor = accent.foreground.copy(alpha = 0.24f),
                    )
                }
                HorizontalDivider(color = accent.foreground.copy(alpha = 0.24f))
                val dayPrefix =
                    if (overview?.current == null && overview?.next == null) {
                        upcomingDayLabel(overview?.nextScheduledDaysAway) + " · "
                    } else {
                        ""
                    }
                Text(
                    text = dayPrefix + "${activeOrNext.startsAt} – ${activeOrNext.endsAt} · ${locationLabel(activeOrNext)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = accent.foreground,
                )
            }
            TextButton(
                onClick =
                    if (canOpenAttendanceFromHome(overview, institutionalReady)) {
                        onOpenAttendance
                    } else {
                        onOpenSchedule
                    },
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.textButtonColors(contentColor = accent.foreground),
            ) {
                Text(
                    if (canOpenAttendanceFromHome(overview, institutionalReady)) "Pasar lista" else "Ver mi horario",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
