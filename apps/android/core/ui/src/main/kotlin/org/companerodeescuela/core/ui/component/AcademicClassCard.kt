package org.companerodeescuela.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.companerodeescuela.core.designsystem.theme.CompaneroElevation

@Composable
fun AcademicClassCard(
    subject: String,
    time: String,
    location: String,
    teacher: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    supportingText: String? = null,
    emphasized: Boolean = false,
    subjectKey: String = subject,
) {
    val containerColor = if (emphasized) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val accent = AcademicSubjectColors.accent(
        key = subjectKey,
        surface = MaterialTheme.colorScheme.surface,
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (emphasized) CompaneroElevation.raised else CompaneroElevation.card,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            eyebrow?.let {
                Text(
                    text = it.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (emphasized) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        accent
                    },
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(accent, CircleShape),
                )
                Text(
                    text = subject,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = time,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = location,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = teacher,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            supportingText?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (emphasized) MaterialTheme.colorScheme.primary else accent,
                )
            }
        }
    }
}
