package org.companerodeescuela.core.designsystem.v8

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Day selection for the V8 calendar. The calling screen controls selected day and content. */
@Composable
fun V8DaySelector(days: List<Pair<String, String>>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        days.forEachIndexed { index, day ->
            val selected = index == selectedIndex
            val shape = RoundedCornerShape(14.dp)
            Column(
                modifier = Modifier.weight(1f)
                    .background(if (selected) V8RedColors.DeepCrimson.copy(alpha = .34f) else V8RedColors.Surface, shape)
                    .border(BorderStroke(1.dp, if (selected) V8RedColors.Crimson else V8RedColors.Outline), shape)
                    .clickable { onSelect(index) }
                    .padding(vertical = 12.dp, horizontal = 3.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                Text(day.first, color = V8RedColors.TextPrimary, fontSize = 12.sp, maxLines = 1)
                Text(day.second, color = if (selected) V8RedColors.Crimson else V8RedColors.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Interactive schedule building block, not a replacement for the canonical collision-aware schedule engine. */
@Composable
fun V8ScheduleClassCard(info: V8ClassSummary, selected: Boolean, onClick: (String) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier.fillMaxWidth()
            .background(if (selected) V8RedColors.DeepCrimson.copy(alpha = .35f) else V8RedColors.Surface, shape)
            .border(BorderStroke(1.dp, if (selected) V8RedColors.Crimson else V8RedColors.Outline), shape)
            .clickable { onClick(info.id) }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(info.title, color = V8RedColors.TextPrimary, fontWeight = FontWeight.Bold, maxLines = 2)
        Text("${info.start} – ${info.end}", color = V8RedColors.TextSecondary, fontSize = 12.sp)
        Text(info.room, color = V8RedColors.TextSecondary, fontSize = 12.sp, maxLines = 1)
    }
}
