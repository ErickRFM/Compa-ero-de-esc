package org.companerodeescuela.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun AcademicTimelineItem(
    time: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    status: String? = null,
    highlighted: Boolean = false,
    subjectKey: String = title,
    continuousRail: Boolean = false,
) {
    val accent = AcademicSubjectColors.accent(
        key = subjectKey,
        surface = MaterialTheme.colorScheme.surface,
    )
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = time,
            modifier = Modifier.width(48.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            color = if (highlighted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = if (continuousRail) {
                Modifier
                    .size(if (highlighted) 12.dp else 10.dp)
                    .clip(CircleShape)
                    .background(
                        if (highlighted) MaterialTheme.colorScheme.primary else accent,
                    )
            } else {
                Modifier
                    .width(if (highlighted) 5.dp else 4.dp)
                    .height(48.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(
                        if (highlighted) MaterialTheme.colorScheme.primary else accent,
                    )
            },
        )
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Medium,
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
            )
            status?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (highlighted) MaterialTheme.colorScheme.primary else accent,
                    maxLines = 1,
                )
            }
        }
    }
}
