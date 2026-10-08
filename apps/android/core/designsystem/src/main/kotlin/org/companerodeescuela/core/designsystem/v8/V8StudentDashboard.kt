package org.companerodeescuela.core.designsystem.v8

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Data comes from feature ViewModels; mockup percentages and course names must never be hardcoded. */
data class V8ClassSummary(val id: String, val title: String, val start: String, val end: String, val room: String)

@Composable
fun V8DashboardStat(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier, progress: Float? = null) {
    V8GlassCard(modifier = modifier.clickable(onClick = onClick)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(value, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = V8RedColors.TextPrimary)
            Text(label, fontSize = 13.sp, color = V8RedColors.TextSecondary)
            progress?.let {
                LinearProgressIndicator(
                    progress = { it.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = V8RedColors.Crimson,
                    trackColor = V8RedColors.Outline,
                )
            }
        }
    }
}

@Composable
fun V8DailyClassRow(classInfo: V8ClassSummary, isNext: Boolean, onClick: (String) -> Unit, modifier: Modifier = Modifier) {
    V8GlassCard(modifier = modifier.fillMaxWidth().clickable { onClick(classInfo.id) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column {
                Text(classInfo.start, color = if (isNext) V8RedColors.Crimson else V8RedColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(classInfo.end, color = V8RedColors.TextSecondary, fontSize = 12.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(classInfo.title, color = V8RedColors.TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Text(classInfo.room, color = V8RedColors.TextSecondary, fontSize = 12.sp)
            }
            if (isNext) Text("Siguiente", color = V8RedColors.Crimson, fontSize = 12.sp)
        }
    }
}
