package org.companerodeescuela.core.designsystem.v8

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class V8Announcement(val id: String, val author: String, val title: String, val message: String, val relativeTime: String, val important: Boolean)

@Composable
fun V8AnnouncementCard(announcement: V8Announcement, onClick: (String) -> Unit, modifier: Modifier = Modifier) {
    V8GlassCard(modifier = modifier.fillMaxWidth().clickable { onClick(announcement.id) }) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(announcement.author, color = V8RedColors.TextPrimary, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(announcement.relativeTime, color = V8RedColors.TextSecondary, fontSize = 12.sp)
            }
            if (announcement.important) Text("Importante", color = V8RedColors.Crimson, fontSize = 12.sp)
            Text(announcement.title, color = V8RedColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(announcement.message, color = V8RedColors.TextSecondary, fontSize = 14.sp)
        }
    }
}

/** Student responses are predefined: no free-text composer. Validate permissions server-side too. */
@Composable
fun V8QuickReplies(options: List<String>, onReply: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            V8GlassCard(modifier = Modifier.weight(1f).clickable { onReply(option) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(9.dp)) {
                Text(option, color = V8RedColors.TextPrimary, fontSize = 12.sp, maxLines = 2)
            }
        }
    }
}
