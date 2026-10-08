package org.companerodeescuela.core.designsystem.v8

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** These summaries always receive real data from feature ViewModels. */
data class V8ClassSummary(val id: String, val title: String, val start: String, val end: String, val room: String)

@Composable
fun V8DashboardStat(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
) {
    V8GlassCard(modifier = modifier.clickable(onClick = onClick), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier.size(34.dp).background(V8RedColors.DeepCrimson.copy(alpha = .45f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = when {
                        label.contains("clase", ignoreCase = true) -> Icons.Filled.School
                        label.contains("siguiente", ignoreCase = true) || label.contains("horario", ignoreCase = true) -> Icons.Filled.DateRange
                        else -> Icons.Filled.CheckCircle
                    },
                    contentDescription = null,
                    tint = V8RedColors.Crimson,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = value,
                fontSize = if (value.length > 11) 15.sp else 24.sp,
                lineHeight = if (value.length > 11) 18.sp else 27.sp,
                fontWeight = FontWeight.Bold,
                color = V8RedColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(label, fontSize = 12.sp, color = V8RedColors.TextSecondary, maxLines = 2)
            progress?.let {
                LinearProgressIndicator(
                    progress = { it.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = V8RedColors.Crimson,
                    trackColor = V8RedColors.Outline,
                )
            }
        }
    }
}

@Composable
fun V8DailyClassRow(
    classInfo: V8ClassSummary,
    isNext: Boolean,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    V8GlassCard(
        modifier = modifier.fillMaxWidth().clickable { onClick(classInfo.id) },
        highlighted = isNext,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(classInfo.start, color = if (isNext) V8RedColors.Crimson else V8RedColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(classInfo.end, color = V8RedColors.TextSecondary, fontSize = 12.sp)
            }
            Box(Modifier.size(width = 2.dp, height = 46.dp).background(if (isNext) V8RedColors.Crimson else V8RedColors.Outline))
            Column(modifier = Modifier.weight(1f)) {
                Text(classInfo.title, color = V8RedColors.TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(classInfo.room, color = V8RedColors.TextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (isNext) Text("Siguiente", color = V8RedColors.Crimson, fontSize = 11.sp)
        }
    }
}
