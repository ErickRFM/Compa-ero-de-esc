package org.companerodeescuela.feature.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import org.companerodeescuela.core.designsystem.v8.V8GlassCard
import org.companerodeescuela.core.designsystem.v8.V8RedColors
import org.companerodeescuela.shared.contracts.ScheduleEntry
import org.companerodeescuela.shared.contracts.ScheduleSource

/**
 * Native five-day week grid for V8.
 * It displays canonical classes without changing the collision/editing rules;
 * the existing day-mode editor remains the authority for movements.
 */
@Composable
internal fun V8WeekGrid(
    entries: List<ScheduleEntry>,
    selectedDay: String,
    onOpenDay: (String) -> Unit,
    onEditPersonal: (ScheduleEntry) -> Unit,
) {
    val days = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY")
    val titles = listOf("Lun", "Mar", "Mié", "Jue", "Vie")
    val valid = entries.mapNotNull { entry ->
        val start = runCatching { LocalTime.parse(entry.startsAt) }.getOrNull()
        val end = runCatching { LocalTime.parse(entry.endsAt) }.getOrNull()
        if (start == null || end == null || !start.isBefore(end)) null
        else Triple(entry, start.hour * 60 + start.minute, end.hour * 60 + end.minute)
    }
    val firstHour = ((valid.minOfOrNull { it.second } ?: 480) / 60).coerceIn(6, 18)
    val lastScheduledHour = ceil((valid.maxOfOrNull { it.third } ?: 1080) / 60.0).toInt()
    val lastHour = max(firstHour + 9, lastScheduledHour).coerceAtMost(23)
    val hourHeight = 53.dp
    val gridHeight = hourHeight * (lastHour - firstHour)
    val shape = RoundedCornerShape(12.dp)
    val gridLineColor = V8RedColors.Outline.copy(alpha = 0.52f)

    V8GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(7.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Hora", modifier = Modifier.width(42.dp), fontSize = 10.sp, color = V8RedColors.TextSecondary)
                days.forEachIndexed { index, day ->
                    Text(
                        text = titles[index],
                        modifier = Modifier.weight(1f).clickable { onOpenDay(day) },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (day == selectedDay) V8RedColors.Crimson else V8RedColors.TextPrimary,
                        maxLines = 1,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.width(42.dp).height(gridHeight)) {
                    for (hour in firstHour until lastHour) {
                        Text(
                            text = "%02d:00".format(hour),
                            modifier = Modifier.offset(y = hourHeight * (hour - firstHour).toFloat()),
                            color = V8RedColors.TextSecondary,
                            fontSize = 10.sp,
                            maxLines = 1,
                        )
                    }
                }
                days.forEach { day ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(gridHeight)
                            .drawBehind {
                                val rowPx = size.height / (lastHour - firstHour)
                                for (line in 0..(lastHour - firstHour)) {
                                    val y = line * rowPx
                                    drawLine(gridLineColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                                }
                                drawLine(gridLineColor, Offset(0f, 0f), Offset(0f, size.height), 1.dp.toPx())
                            },
                    ) {
                        valid.filter { it.first.dayOfWeek == day }.forEach { (entry, starts, ends) ->
                            val top = hourHeight * ((starts - firstHour * 60) / 60f)
                            val height = maxOf(38.dp, hourHeight * ((ends - starts) / 60f) - 3.dp)
                            val enabled = entry.source != ScheduleSource.INSTITUTIONAL
                            val emphasized = day == selectedDay
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset(y = top)
                                    .height(height)
                                    .padding(horizontal = 2.dp)
                                    .clip(shape)
                                    .background(
                                        if (emphasized) Color(0xFF48121C) else V8RedColors.Surface,
                                        shape,
                                    )
                                    .border(
                                        1.dp,
                                        if (emphasized) V8RedColors.Crimson else V8RedColors.Outline,
                                        shape,
                                    )
                                    .clickable {
                                        if (enabled) onEditPersonal(entry) else onOpenDay(day)
                                    }
                                    .padding(horizontal = 4.dp, vertical = 5.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = entry.subjectName,
                                    color = V8RedColors.TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp,
                                    lineHeight = 11.sp,
                                    maxLines = if (height > 75.dp) 3 else 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (height > 70.dp) {
                                    Text(
                                        text = entry.startsAt + "–" + entry.endsAt,
                                        color = V8RedColors.TextSecondary,
                                        fontSize = 9.sp,
                                        maxLines = 1,
                                    )
                                    entry.classroomName?.takeIf { it.isNotBlank() }?.let { room ->
                                        Text(room, color = V8RedColors.TextSecondary, fontSize = 9.sp, maxLines = 1)
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
