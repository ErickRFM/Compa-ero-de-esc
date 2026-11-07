@file:Suppress("ktlint:standard:function-naming")

package org.companerodeescuela.core.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.designsystem.theme.CompanionColors
import org.companerodeescuela.core.designsystem.theme.LocalCompaneroHighContrast
import org.companerodeescuela.core.designsystem.v8.LocalV8GlassEnabled
import org.companerodeescuela.core.designsystem.v8.v8GlassSurface
import org.companerodeescuela.core.motion.CompaneroMotionDuration
import org.companerodeescuela.core.motion.LocalCompaneroMotionPreferences

enum class NoticeTone {
    NEUTRAL,
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
    tone: NoticeTone = NoticeTone.NEUTRAL,
) {
    val glass = LocalV8GlassEnabled.current && tone == NoticeTone.NEUTRAL
    val darkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val targetContainer =
        when (tone) {
            NoticeTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceContainerLow
            NoticeTone.INFO -> MaterialTheme.colorScheme.secondaryContainer
            NoticeTone.SUCCESS ->
                if (darkSurface) {
                    CompanionColors.semanticGreenDarkContainer
                } else {
                    CompanionColors.semanticGreenContainer
                }
            NoticeTone.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
            NoticeTone.ERROR -> MaterialTheme.colorScheme.errorContainer
        }
    val targetContent =
        when (tone) {
            NoticeTone.NEUTRAL -> MaterialTheme.colorScheme.onSurface
            NoticeTone.INFO -> MaterialTheme.colorScheme.onSecondaryContainer
            NoticeTone.SUCCESS ->
                if (darkSurface) {
                    CompanionColors.semanticGreenDark
                } else {
                    CompanionColors.semanticGreen
                }
            NoticeTone.WARNING -> MaterialTheme.colorScheme.onTertiaryContainer
            NoticeTone.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        }

    val reducedMotion = LocalCompaneroMotionPreferences.current.reducedMotion
    val colorSpec = if (reducedMotion) snap() else tween<Color>(CompaneroMotionDuration.STANDARD)
    val animatedContainer by animateColorAsState(
        targetValue = targetContainer,
        animationSpec = colorSpec,
        label = "statusNoticeContainer",
    )
    val animatedContent by animateColorAsState(
        targetValue = targetContent,
        animationSpec = colorSpec,
        label = "statusNoticeContent",
    )

    val border =
        if (LocalCompaneroHighContrast.current && !glass) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface)
        } else {
            null
        }

    Surface(
        modifier = modifier.fillMaxWidth().then(if (glass) Modifier.v8GlassSurface() else Modifier),
        color = if (glass) Color.Transparent else animatedContainer,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 0.dp,
        shadowElevation = if (glass || LocalCompaneroHighContrast.current) 0.dp else 1.dp,
        border = if (glass) null else border,
    ) {
        Column(modifier = Modifier.padding(CompaneroSpacing.sm)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = animatedContent,
            )
            Text(
                text = message,
                modifier = Modifier.padding(top = CompaneroSpacing.xxs),
                style = MaterialTheme.typography.bodySmall,
                color = animatedContent,
            )
        }
    }
}
