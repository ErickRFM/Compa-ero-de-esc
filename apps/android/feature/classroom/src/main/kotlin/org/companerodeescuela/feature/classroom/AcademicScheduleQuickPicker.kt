@file:Suppress("ktlint:standard:function-naming")

package org.companerodeescuela.feature.classroom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * Touch-first shortcuts for the existing scoped MFA command.
 * These are proposed slots, not a claim that an institutional room is free.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AcademicScheduleQuickPicker(
    day: String,
    start: String,
    end: String,
    enabled: Boolean,
    onDay: (String) -> Unit,
    onRange: (String, String) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val parsedStart = runCatching { LocalTime.parse(start) }.getOrNull()
    val parsedEnd = runCatching { LocalTime.parse(end) }.getOrNull()
    val duration =
        if (parsedStart != null && parsedEnd != null) {
            java.time.Duration
                .between(parsedStart, parsedEnd)
                .toMinutes()
                .toInt()
        } else {
            60
        }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.academic_pick_day), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DayOfWeek.entries.forEach { d ->
                FilterChip(
                    selected = day == d.name,
                    onClick = { onDay(d.name) },
                    label = { Text(d.getDisplayName(TextStyle.SHORT, locale)) },
                    enabled = enabled,
                    modifier = Modifier.testTag("academic_day_" + d.name),
                )
            }
        }
        Text(stringResource(R.string.academic_pick_start), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (7..19).forEach { h ->
                val hour = String.format(Locale.ROOT, "%02d:00", h)
                FilterChip(
                    selected = start == hour,
                    onClick = {
                        val chosenDuration = duration.takeIf { it > 0 && h * 60 + it <= 1200 } ?: 60
                        onRange(hour, LocalTime.of(h, 0).plusMinutes(chosenDuration.toLong()).toString())
                    },
                    label = { Text(hour) },
                    enabled = enabled,
                    modifier = Modifier.testTag("academic_slot_" + hour.replace(":", "")),
                )
            }
        }
        Text(stringResource(R.string.academic_pick_duration), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(50, 60, 90, 120).forEach { minutes ->
                val fits = parsedStart != null && parsedStart.toSecondOfDay() / 60 + minutes <= 1200
                FilterChip(
                    selected = fits && duration == minutes,
                    onClick = {
                        if (fits && parsedStart != null) {
                            onRange(start, parsedStart.plusMinutes(minutes.toLong()).toString())
                        }
                    },
                    label = { Text("$minutes min") },
                    enabled = enabled && fits,
                    modifier = Modifier.testTag("academic_duration_" + minutes),
                )
            }
        }
        if (day.isNotBlank() && parsedStart != null && parsedEnd != null && parsedEnd.isAfter(parsedStart)) {
            Text(
                stringResource(
                    R.string.academic_pick_preview,
                    DayOfWeek.valueOf(day).getDisplayName(TextStyle.FULL, locale),
                    start,
                    end,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("academic_schedule_preview"),
            )
        } else {
            Text(
                stringResource(R.string.academic_pick_unselected),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
