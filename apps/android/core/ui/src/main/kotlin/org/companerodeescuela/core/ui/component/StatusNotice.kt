package org.companerodeescuela.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.companerodeescuela.core.designsystem.theme.CompanionColors
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing

enum class NoticeTone {
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
}

@Composable
fun StatusNotice(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    tone: NoticeTone = NoticeTone.INFO,
) {
    val darkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val container = when (tone) {
        NoticeTone.INFO -> MaterialTheme.colorScheme.secondaryContainer
        NoticeTone.SUCCESS -> if (darkSurface) {
            CompanionColors.semanticGreenDarkContainer
        } else {
            CompanionColors.semanticGreenContainer
        }
        NoticeTone.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
        NoticeTone.ERROR -> MaterialTheme.colorScheme.errorContainer
    }
    val content = when (tone) {
        NoticeTone.INFO -> MaterialTheme.colorScheme.onSecondaryContainer
        NoticeTone.SUCCESS -> if (darkSurface) {
            CompanionColors.semanticGreenDark
        } else {
            CompanionColors.semanticGreen
        }
        NoticeTone.WARNING -> MaterialTheme.colorScheme.onTertiaryContainer
        NoticeTone.ERROR -> MaterialTheme.colorScheme.onErrorContainer
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = container,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(CompaneroSpacing.md)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = content,
            )
            Text(
                text = message,
                modifier = Modifier.padding(top = CompaneroSpacing.xxs),
                style = MaterialTheme.typography.bodyMedium,
                color = content,
            )
        }
    }
}
