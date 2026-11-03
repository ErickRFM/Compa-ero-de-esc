@file:Suppress("ktlint:standard:function-naming")

package org.companerodeescuela.core.designsystem.v8

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.companerodeescuela.core.designsystem.R
import org.companerodeescuela.core.designsystem.theme.LocalCompaneroHighContrast
import org.companerodeescuela.core.designsystem.theme.companeroAccentSurfaceColors
import org.companerodeescuela.core.motion.CompaneroMotionDuration
import org.companerodeescuela.core.motion.LocalCompaneroMotionPreferences

/** Appears only while its caller is loading; no minimum duration or synthetic percentage. */
@Composable
fun V8LoadingState(
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.ux_loading_content),
    branded: Boolean = false,
) {
    val reduced = LocalCompaneroMotionPreferences.current.reducedMotion
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }
    val entrance by animateFloatAsState(
        targetValue = if (revealed || reduced) 1f else 0f,
        animationSpec = if (reduced) snap() else tween(CompaneroMotionDuration.STANDARD),
        label = "loadingEntrance",
    )
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
                    .graphicsLayer {
                        alpha = entrance
                        scaleX = 0.96f + 0.04f * entrance
                        scaleY = scaleX
                    }.loadingSemantics(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (branded) {
                val accent = companeroAccentSurfaceColors()
                Surface(shape = RoundedCornerShape(24.dp), color = accent.background) {
                    Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.School, null, Modifier.size(40.dp), tint = accent.foreground)
                    }
                }
                Text(
                    "Compañero",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LoadingActivity(reduced)
        }
    }
}

/** Local content placeholder: shortcuts and previously loaded data stay usable. */
@Composable
fun V8LoadingCard(
    title: String,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalCompaneroMotionPreferences.current.reducedMotion
    val line = MaterialTheme.colorScheme.onSurface.copy(alpha = if (LocalCompaneroHighContrast.current) 0.24f else 0.10f)
    V8GlassCard(modifier = modifier.fillMaxWidth().loadingSemantics()) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LoadingActivity(reduced)
                Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
            Column(Modifier.clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth(0.8f).height(20.dp).background(line, RoundedCornerShape(6.dp)))
                Box(Modifier.fillMaxWidth(0.5f).height(12.dp).background(line, RoundedCornerShape(4.dp)))
            }
        }
    }
}

private fun Modifier.loadingSemantics(): Modifier =
    semantics(mergeDescendants = true) {
        liveRegion = LiveRegionMode.Polite
        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
    }

@Composable
private fun LoadingActivity(reduced: Boolean) {
    if (reduced) {
        Row(Modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) { Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape)) }
        }
    } else {
        CircularProgressIndicator(Modifier.size(24.dp).clearAndSetSemantics {}, strokeWidth = 2.dp)
    }
}
