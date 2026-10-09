package org.companerodeescuela.feature.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.companerodeescuela.core.designsystem.v8.*
import org.companerodeescuela.shared.contracts.ScheduleEntry
import org.companerodeescuela.shared.contracts.ScheduleSource

@Composable
internal fun WeeklyAgendaGrid(entries: List<ScheduleEntry>, onEdit: (ScheduleEntry) -> Unit, onDelete: (ScheduleEntry) -> Unit, onMoveRequest: (AgendaMoveProposal) -> Unit) {
    val days = remember(entries) {
        weeklyAgendaDays(entries)
    }
    val layouts = remember(entries, days) { days.map { day -> agendaGridLayout(entries.filter { it.dayOfWeek == day }) } }
    val placements = layouts.flatten()
    val startHour = (placements.minOfOrNull { it.startMinute / 60 } ?: 8).coerceAtMost(8)
    val endHour = ((placements.maxOfOrNull { it.startMinute + it.durationMinutes } ?: 1080) + 59) / 60
    val lastHour = endHour.coerceAtLeast(18)
    val hourHeight = if (LocalDensity.current.fontScale > 1.2f) 58.dp else 36.dp
    val rail = 38.dp
    var detail by remember { mutableStateOf<ScheduleEntry?>(null) }
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val quarterHourPx = with(density) { hourHeight.toPx() / 4 }
    val gridScroll = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var showAllDetails by remember { mutableStateOf(false) }
    LaunchedEffect(entries) { gridScroll.scrollTo(0) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Lunes a sábado · desplaza a los lados", fontSize = 11.sp, color = V8RedColors.TextSecondary)
        TextButton(onClick = {
            coroutineScope.launch { gridScroll.animateScrollTo(gridScroll.maxValue) }
        }) { Text("Ver sábado →") }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val normalWidth = ((maxWidth - rail) / days.size).coerceAtLeast(116.dp)
        val widths = layouts.map { layout ->
            val lanes = layout.maxOfOrNull { it.laneCount } ?: 1
            if (lanes > 1) (normalWidth * lanes).coerceAtLeast(96.dp * lanes) else normalWidth
        }
        val gridWidth = widths.fold(rail) { total, width -> total + width }
        val widthsPx = widths.map { with(density) { it.toPx() } }
        // The viewport must scroll, not the oversized child itself; otherwise
        // Compose can clip the final Saturday column on a narrow device.
        Box(Modifier.fillMaxWidth().horizontalScroll(gridScroll)) {
        Column(Modifier.width(gridWidth)
            .v8GlassSurface(cornerRadius = 14.dp, elevation = 0.dp)) {
            Row(Modifier.padding(vertical = 12.dp)) {
                Text("Hora", Modifier.width(rail).padding(start = 4.dp), fontSize = 10.sp, color = V8RedColors.TextSecondary)
                days.forEachIndexed { i, day -> Text(dayLabelGrid(day), Modifier.width(widths[i]).padding(start = 4.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            }
            Row {
                Column(Modifier.width(rail)) {
                    for (hour in startHour until lastHour) Text("$hour:00", Modifier.height(hourHeight).padding(start = 4.dp, top = 4.dp), fontSize = 9.sp, color = V8RedColors.TextSecondary)
                }
                layouts.forEachIndexed { dayIndex, layout ->
                    Box(Modifier.width(widths[dayIndex]).height(hourHeight * (lastHour - startHour))
                        .drawBehind {
                            for (hour in 0..(lastHour - startHour)) {
                                val y = hourHeight.toPx() * hour
                                drawLine(V8RedColors.Outline.copy(alpha = 0.4f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                            }
                            drawLine(V8RedColors.Outline.copy(alpha = 0.4f), Offset.Zero, Offset(0f, size.height), 1.dp.toPx())
                        }) {
                        layout.forEach { placement ->
                            val laneWidth = widths[dayIndex] / placement.laneCount
                            val entry = placement.entry
                            var dragOffset by remember(entry) { mutableStateOf(Offset.Zero) }
                            val dragModifier = if (entry.source == ScheduleSource.INSTITUTIONAL) Modifier else Modifier
                                .graphicsLayer { translationX = dragOffset.x; translationY = dragOffset.y }
                                .pointerInput(entry, entries, quarterHourPx, widthsPx) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                                        onDragCancel = { dragOffset = Offset.Zero },
                                        onDragEnd = {
                                            val target = AgendaEditingRules.shiftFromDrag(entry, dragOffset.y, quarterHourPx)
                                            val targetDay = weeklyDragTargetDay(days, widthsPx, dayIndex, placement.lane, placement.laneCount, dragOffset.x)
                                            if (target != null && (target != entry.startsAt || targetDay != entry.dayOfWeek)) {
                                                AgendaEditingRules.proposeMove(entry, targetDay, target, entries)?.let(onMoveRequest)
                                            }
                                            dragOffset = Offset.Zero
                                        },
                                        onDrag = { change, amount -> change.consume(); dragOffset += amount },
                                    )
                                }
                            val accent = entry.source != ScheduleSource.INSTITUTIONAL || entry.subjectCode.hashCode() % 2 == 0
                            Box(Modifier.offset(x = laneWidth * placement.lane, y = hourHeight * ((placement.startMinute - startHour * 60) / 60f))
                                .width(laneWidth).height(hourHeight * (placement.durationMinutes / 60f)).then(dragModifier).padding(2.dp)
                                .v8GlassSurface(cornerRadius = 8.dp, emphasized = accent, elevation = 0.dp)
                                .clickable { detail = entry }.padding(5.dp)) {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(entry.subjectName, fontSize = 10.sp, lineHeight = 12.sp,
                                        maxLines = if (placement.durationMinutes >= 120) 3 else 2,
                                        overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                                    if (placement.durationMinutes >= 90) {
                                        Text(entry.startsAt + "–" + entry.endsAt, fontSize = 9.sp, lineHeight = 11.sp, color = V8RedColors.TextSecondary)
                                        entry.classroomName?.let { Text(it, fontSize = 9.sp, lineHeight = 11.sp, color = V8RedColors.TextSecondary) }
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
    Text("Toca un bloque para sus detalles o desliza para ver los demás días.", fontSize = 11.sp, color = V8RedColors.TextSecondary)
    TextButton(onClick = { showAllDetails = !showAllDetails }) {
        Text(if (showAllDetails) "Ocultar lista accesible" else "Ver horario como lista")
    }
    if (showAllDetails) entries.forEach { entry ->
        TextButton(onClick = { detail = entry }, modifier = Modifier.fillMaxWidth()) {
            Text(dayLabelGrid(entry.dayOfWeek) + " · " + entry.startsAt + "–" + entry.endsAt + " · " + entry.subjectName, modifier = Modifier.fillMaxWidth())
        }
    }
    }
    detail?.let { entry ->
        AlertDialog(onDismissRequest = { detail = null },
            title = { Text(entry.subjectName) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(dayLabelGrid(entry.dayOfWeek) + " · " + entry.startsAt + "–" + entry.endsAt)
                listOfNotNull(entry.classroomName, entry.buildingName, entry.campusName,
                    entry.groupName.takeIf(String::isNotBlank)?.let { "Grupo: $it" },
                    entry.teacherName.takeIf(String::isNotBlank)?.let { "Docente: $it" }).forEach { Text(it) }
                Text("Origen: " + when (entry.source) {
                    ScheduleSource.INSTITUTIONAL -> "Institucional"
                    ScheduleSource.MANUAL -> "Personal"
                    ScheduleSource.OCR_IMPORT -> "Importado"
                })
            } },
            confirmButton = { TextButton(onClick = { detail = null }) { Text("Cerrar") } },
            dismissButton = {
                if (entry.source != ScheduleSource.INSTITUTIONAL) Row {
                    TextButton(onClick = { detail = null; onEdit(entry) }) { Text("Editar") }
                    TextButton(onClick = { detail = null; onDelete(entry) }) { Text("Eliminar") }
                }
            },
        )
    }
}

private fun dayLabelGrid(day: String): String = when(day) {
    "MONDAY" -> "Lunes"; "TUESDAY" -> "Martes"; "WEDNESDAY" -> "Miércoles"
    "THURSDAY" -> "Jueves"; "FRIDAY" -> "Viernes"; "SATURDAY" -> "Sábado"
    "SUNDAY" -> "Domingo"; else -> day
}
