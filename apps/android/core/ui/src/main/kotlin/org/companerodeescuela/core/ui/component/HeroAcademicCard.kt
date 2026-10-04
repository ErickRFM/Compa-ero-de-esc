package org.companerodeescuela.core.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import org.companerodeescuela.core.designsystem.theme.CompaneroElevation
import org.companerodeescuela.core.designsystem.theme.CompaneroExpressive
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.motion.CompaneroMotion
import org.companerodeescuela.core.motion.LocalCompaneroMotionPreferences

@Composable
fun HeroAcademicCard(
    subject: String,
    time: String,
    location: String,
    teacher: String,
    progress: Float,
    modifier: Modifier = Modifier,
    eyebrow: String = "Ahora",
    supportingText: String? = null,
) {
    val reducedMotion = LocalCompaneroMotionPreferences.current.reducedMotion
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = if (reducedMotion) {
            CompaneroMotion.fast()
        } else {
            CompaneroMotion.standard()
        },
        label = "heroClassProgress",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = CompaneroElevation.raised,
                shape = MaterialTheme.shapes.extraLarge,
                clip = false,
            )
            .clip(MaterialTheme.shapes.extraLarge)
            .background(CompaneroExpressive.heroBrush()),
    ) {
        Column(
            modifier = Modifier.padding(CompaneroSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
        ) {
            Text(
                text = eyebrow.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.84f),
            )
            Text(
                text = subject,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
            )
            Text(
                text = time,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
            )
            Text(
                text = location,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.88f),
            )
            Text(
                text = teacher,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.76f),
            )

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = CompaneroSpacing.sm)
                    .height(CompaneroSpacing.xxs)
                    .clip(RoundedCornerShape(percent = 50)),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.24f),
                drawStopIndicator = {},
            )

            supportingText?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(top = CompaneroSpacing.xs),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
            }
        }
    }
}
