@file:Suppress("ktlint:standard:function-naming")

package org.companerodeescuela.feature.classroom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.companerodeescuela.core.designsystem.v8.V8GlassCard
import org.companerodeescuela.shared.contracts.AcademicGroupSummary
import org.companerodeescuela.shared.contracts.ClassroomSummary
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

/** Field-level problems the editor can explain before the server rejects a draft. */
internal enum class ScheduleDraftIssue { START_FORMAT, END_FORMAT, END_NOT_AFTER_START }

internal enum class GroupDatesIssue { START_FORMAT, END_FORMAT, END_NOT_AFTER_START, END_IN_PAST }

private val TIME = Regex("([01][0-9]|2[0-3]):[0-5][0-9]")

/**
 * Mirrors the client checks in [ClassroomAdministrationViewModel.prepareSchedule]:
 * strict HH:MM and a start strictly before the end. Blank fields are not issues yet.
 */
internal fun scheduleDraftIssue(
    start: String,
    end: String,
): ScheduleDraftIssue? {
    if (start.isNotEmpty() && !TIME.matches(start)) return ScheduleDraftIssue.START_FORMAT
    if (end.isNotEmpty() && !TIME.matches(end)) return ScheduleDraftIssue.END_FORMAT
    if (start.isEmpty() || end.isEmpty()) return null
    return if (LocalTime.parse(start) < LocalTime.parse(end)) null else ScheduleDraftIssue.END_NOT_AFTER_START
}

internal fun scheduleDraftReady(
    start: String,
    end: String,
): Boolean = start.isNotEmpty() && end.isNotEmpty() && scheduleDraftIssue(start, end) == null

/** Mirrors [ClassroomAdministrationViewModel.prepareGroup]: ISO dates, start before end, end after today. */
internal fun groupDatesIssue(
    starts: String,
    ends: String,
    today: LocalDate,
): GroupDatesIssue? {
    val start = isoDate(starts)
    val end = isoDate(ends)
    if (starts.isNotEmpty() && start == null) return GroupDatesIssue.START_FORMAT
    if (ends.isNotEmpty() && end == null) return GroupDatesIssue.END_FORMAT
    if (start != null && end != null && !start.isBefore(end)) return GroupDatesIssue.END_NOT_AFTER_START
    if (end != null && !end.isAfter(today)) return GroupDatesIssue.END_IN_PAST
    return null
}

private val ISO_DATE = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")

private fun isoDate(value: String): LocalDate? =
    value.takeIf(ISO_DATE::matches)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

internal fun groupDatesReady(
    starts: String,
    ends: String,
    today: LocalDate,
): Boolean = starts.isNotEmpty() && ends.isNotEmpty() && groupDatesIssue(starts, ends, today) == null

@Composable
internal fun scheduleIssueText(issue: ScheduleDraftIssue?): Pair<String?, String?> =
    when (issue) {
        ScheduleDraftIssue.START_FORMAT -> stringResource(R.string.academic_issue_time_format) to null
        ScheduleDraftIssue.END_FORMAT -> null to stringResource(R.string.academic_issue_time_format)
        ScheduleDraftIssue.END_NOT_AFTER_START -> null to stringResource(R.string.academic_issue_time_order)
        null -> null to null
    }

@Composable
internal fun groupDatesIssueText(issue: GroupDatesIssue?): Pair<String?, String?> =
    when (issue) {
        GroupDatesIssue.START_FORMAT -> stringResource(R.string.academic_issue_date_format) to null
        GroupDatesIssue.END_FORMAT -> null to stringResource(R.string.academic_issue_date_format)
        GroupDatesIssue.END_NOT_AFTER_START -> null to stringResource(R.string.academic_issue_date_order)
        GroupDatesIssue.END_IN_PAST -> null to stringResource(R.string.academic_issue_date_past)
        null -> null to null
    }

/** Full read-back of the block that will be sent for MFA confirmation. */
@Composable
internal fun AcademicScheduleSummary(
    classroom: ClassroomSummary,
    day: String,
    start: String,
    end: String,
) {
    val locale = LocalConfiguration.current.locales[0]
    val dayName =
        runCatching { DayOfWeek.valueOf(day).getDisplayName(TextStyle.FULL, locale) }
            .getOrDefault(day)
            .replaceFirstChar { it.titlecase(locale) }
    val minutes = Duration.between(LocalTime.parse(start), LocalTime.parse(end)).toMinutes()
    V8GlassCard(Modifier.fillMaxWidth().testTag("academic_schedule_summary").semantics(mergeDescendants = true) {}, emphasized = true) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.academic_summary_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(classroom.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.academic_summary_slot, dayName, start, end, minutes),
                style = MaterialTheme.typography.bodyLarge,
            )
            SummaryLine(R.string.academic_summary_group, classroom.groupName)
            SummaryLine(R.string.academic_summary_teacher, classroom.teacherDisplayName)
            SummaryLine(R.string.academic_summary_room, classroom.room)
        }
    }
}

@Composable
private fun SummaryLine(
    label: Int,
    value: String?,
) {
    if (value.isNullOrBlank()) return
    Text(
        stringResource(label, value),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Group row with period, dates and number of classes instead of a bare name. */
@Composable
internal fun AcademicGroupCard(
    group: AcademicGroupSummary,
    classCount: Int,
) {
    val locale = LocalConfiguration.current.locales[0]
    val dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)

    fun day(epoch: Long?) = epoch?.let { Instant.ofEpochSecond(it).atZone(ZoneOffset.UTC).toLocalDate().format(dates) }
    V8GlassCard(Modifier.fillMaxWidth().testTag("academic_group_card_" + group.id)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    group.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(if (group.active) R.string.academic_group_active else R.string.academic_group_inactive),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (group.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            group.periodId?.takeIf { it.isNotBlank() }?.let {
                Text(stringResource(R.string.academic_group_period, it), style = MaterialTheme.typography.bodyMedium)
            }
            val from = day(group.startsAtEpochSeconds)
            val until = day(group.endsAtEpochSeconds)
            if (from != null && until != null) {
                Text(
                    stringResource(R.string.academic_group_dates, from, until),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (classCount == 1) {
                    stringResource(R.string.academic_group_one_class)
                } else {
                    stringResource(R.string.academic_group_classes, classCount)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
