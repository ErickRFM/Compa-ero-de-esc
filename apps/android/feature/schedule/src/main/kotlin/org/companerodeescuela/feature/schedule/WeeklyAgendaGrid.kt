@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package org.companerodeescuela.feature.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.companerodeescuela.core.designsystem.v8.*
import org.companerodeescuela.shared.contracts.ScheduleEntry
import org.companerodeescuela.shared.contracts.ScheduleSource

@Composable
internal fun WeeklyAgendaGrid(
    entries: List<ScheduleEntry>,
    onEdit: (ScheduleEntry) -> Unit,
    onDelete: (ScheduleEntry) -> Unit,
    onMoveRequest: (AgendaMoveProposal) -> Unit,
    focusDay: String = "MONDAY",
) {
    val days =
        remember(entries) {
            weeklyAgendaDays(entries)
        }
    val layouts = remember(entries, days) { days.map { day -> agendaGridLayout(entries.filter { it.dayOfWeek == day }) } }
    val placements = layouts.flatten()
    val hours = agendaVisibleHours(placements)
    val startHour = hours.first
    val lastHour = hours.last + 1
    val hourHeight = (52f * LocalDensity.current.fontScale).coerceAtLeast(56f).dp
    val rail = 44.dp
    val headerHeight = if (LocalDensity.current.fontScale >= 1.5f) 56.dp else 44.dp
    var detail by remember { mutableStateOf<ScheduleEntry?>(null) }
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val quarterHourPx = with(density) { hourHeight.toPx() / 4 }
    val gridScroll = rememberScrollState()
    var showAllDetails by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val gridLineColor = colors.outline.copy(alpha = 0.4f)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val normalWidth = ((maxWidth - rail) / days.size).coerceAtLeast(132.dp)
            val widths =
                layouts.map { layout ->
                    val lanes = layout.maxOfOrNull { it.laneCount } ?: 1
                    if (lanes > 1) (normalWidth * lanes).coerceAtLeast(104.dp * lanes) else normalWidth
                }
            val gridWidth = widths.fold(0.dp) { total, width -> total + width }
            val widthsPx = widths.map { with(density) { it.toPx() } }
            LaunchedEffect(focusDay, widths) {
                withFrameNanos { }
                val index = days.indexOf(focusDay).coerceAtLeast(0)
                gridScroll.scrollTo(
                    widthsPx
                        .take(index)
                        .sum()
                        .toInt()
                        .coerceAtMost(gridScroll.maxValue),
                )
            }
            Row(Modifier.fillMaxWidth().v8GlassSurface(cornerRadius = 14.dp, elevation = 0.dp)) {
                // Hours remain visible while the day columns move horizontally.
                Column(Modifier.width(rail)) {
                    Box(Modifier.height(headerHeight).fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Text("Hora", fontSize = 10.sp, color = colors.onSurfaceVariant)
                    }
                    for (hour in startHour until lastHour) {
                        Text(
                            "%02d:00".format(hour),
                            Modifier.height(hourHeight).padding(start = 5.dp, top = 6.dp),
                            fontSize = 10.sp,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                Box(Modifier.weight(1f).horizontalScroll(gridScroll)) {
                    Column(Modifier.width(gridWidth)) {
                        Row(Modifier.height(headerHeight), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            days.forEachIndexed { i, day ->
                                Text(
                                    dayLabelGrid(day),
                                    Modifier.width(widths[i]).padding(horizontal = 8.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (day == focusDay) colors.primary else colors.onSurface,
                                )
                            }
                        }
                        Row {
                            layouts.forEachIndexed { dayIndex, layout ->
                                Box(
                                    Modifier
                                        .width(widths[dayIndex])
                                        .height(hourHeight * (lastHour - startHour))
                                        .drawBehind {
                                            for (hour in 0..(lastHour - startHour)) {
                                                val y = hourHeight.toPx() * hour
                                                drawLine(
                                                    gridLineColor,
                                                    Offset(0f, y),
                                                    Offset(size.width, y),
                                                    1.dp.toPx(),
                                                )
                                            }
                                            drawLine(gridLineColor, Offset.Zero, Offset(0f, size.height), 1.dp.toPx())
                                        },
                                ) {
                                    layout.forEach { placement ->
                                        val laneWidth = widths[dayIndex] / placement.laneCount
                                        val entry = placement.entry
                                        var dragOffset by remember(entry) { mutableStateOf(Offset.Zero) }
                                        val dragModifier =
                                            if (entry.source == ScheduleSource.INSTITUTIONAL) {
                                                Modifier
                                            } else {
                                                Modifier
                                                    .graphicsLayer {
                                                        translationX = dragOffset.x
                                                        translationY = dragOffset.y
                                                    }.pointerInput(entry, entries, quarterHourPx, widthsPx) {
                                                        detectDragGesturesAfterLongPress(
                                                            onDragStart = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                                                            onDragCancel = { dragOffset = Offset.Zero },
                                                            onDragEnd = {
                                                                val target =
                                                                    AgendaEditingRules.shiftFromDrag(
                                                                        entry,
                                                                        dragOffset.y,
                                                                        quarterHourPx,
                                                                    )
                                                                val targetDay =
                                                                    weeklyDragTargetDay(
                                                                        days,
                                                                        widthsPx,
                                                                        dayIndex,
                                                                        placement.lane,
                                                                        placement.laneCount,
                                                                        dragOffset.x,
                                                                    )
                                                                if (target != null &&
                                                                    (target != entry.startsAt || targetDay != entry.dayOfWeek)
                                                                ) {
                                                                    AgendaEditingRules
                                                                        .proposeMove(
                                                                            entry,
                                                                            targetDay,
                                                                            target,
                                                                            entries,
                                                                        )?.let(onMoveRequest)
                                                                }
                                                                dragOffset = Offset.Zero
                                                            },
                                                            onDrag = { change, amount ->
                                                                change.consume()
                                                                dragOffset += amount
                                                            },
                                                        )
                                                    }
                                            }
                                        Box(
                                            Modifier
                                                .offset(
                                                    x = laneWidth * placement.lane,
                                                    y =
                                                        hourHeight * ((placement.startMinute - startHour * 60) / 60f),
                                                ).width(laneWidth)
                                                .height(hourHeight * (placement.durationMinutes / 60f))
                                                .then(dragModifier)
                                                .padding(2.dp)
                                                .v8GlassSurface(cornerRadius = 10.dp, emphasized = true, elevation = 0.dp)
                                                .clickable { detail = entry }
                                                .padding(5.dp),
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                Text(
                                                    entry.subjectName,
                                                    fontSize = 11.sp,
                                                    lineHeight = 13.sp,
                                                    maxLines = if (placement.durationMinutes >= 120) 3 else 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                                if (placement.durationMinutes >= 60) {
                                                    Text(
                                                        entry.startsAt + "–" + entry.endsAt,
                                                        fontSize = 10.sp,
                                                        lineHeight = 12.sp,
                                                        color = colors.onSurfaceVariant,
                                                    )
                                                    entry.classroomName?.takeIf { placement.durationMinutes >= 120 }?.let {
                                                        Text(
                                                            it,
                                                            fontSize = 9.sp,
                                                            lineHeight = 11.sp,
                                                            color = colors.onSurfaceVariant,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // Keep a compact grid. The full readable list is opt-in, not 25
        // duplicated rows that push the rest of the screen below the bottom bar.
        TextButton(onClick = { showAllDetails = !showAllDetails }) {
            Text(if (showAllDetails) "Ocultar lista" else "Vista de lista")
        }
        if (showAllDetails) {
            entries.forEach { entry ->
                TextButton(onClick = { detail = entry }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        dayLabelGrid(entry.dayOfWeek) + " · " + entry.startsAt + "–" + entry.endsAt + " · " + entry.subjectName,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
    detail?.let { entry ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(entry.subjectName) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(dayLabelGrid(entry.dayOfWeek) + " · " + entry.startsAt + "–" + entry.endsAt)
                    listOfNotNull(
                        entry.classroomName,
                        entry.buildingName,
                        entry.campusName,
                        entry.groupName.takeIf(String::isNotBlank)?.let { "Grupo: $it" },
                        entry.teacherName.takeIf(String::isNotBlank)?.let { "Docente: $it" },
                    ).forEach { Text(it) }
                    Text(
                        "Origen: " +
                            when (entry.source) {
                                ScheduleSource.INSTITUTIONAL -> "Institucional"
                                ScheduleSource.MANUAL -> "Personal"
                                ScheduleSource.OCR_IMPORT -> "Importado"
                            },
                    )
                }
            },
            confirmButton = { TextButton(onClick = { detail = null }) { Text("Cerrar") } },
            dismissButton = {
                if (entry.source != ScheduleSource.INSTITUTIONAL) {
                    Row {
                        TextButton(onClick = {
                            detail = null
                            onEdit(entry)
                        }) { Text("Editar") }
                        TextButton(onClick = {
                            detail = null
                            onDelete(entry)
                        }) { Text("Eliminar") }
                    }
                }
            },
        )
    }
}

private fun dayLabelGrid(day: String): String =
    when (day) {
        "MONDAY" -> "Lunes"
        "TUESDAY" -> "Martes"
        "WEDNESDAY" -> "Miércoles"
        "THURSDAY" -> "Jueves"
        "FRIDAY" -> "Viernes"
        "SATURDAY" -> "Sábado"
        "SUNDAY" -> "Domingo"
        else -> day
    }
