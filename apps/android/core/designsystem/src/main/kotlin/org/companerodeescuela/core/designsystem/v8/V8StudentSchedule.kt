package org.companerodeescuela.core.designsystem.v8

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
            Column(
                modifier = Modifier.weight(1f)
                    .v8GlassSurface(cornerRadius = 14.dp, emphasized = selected, elevation = 0.dp)
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
