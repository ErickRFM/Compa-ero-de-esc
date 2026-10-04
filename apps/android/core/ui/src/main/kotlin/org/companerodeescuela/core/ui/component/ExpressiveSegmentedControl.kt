package org.companerodeescuela.core.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.companerodeescuela.core.motion.CompaneroMotion
import org.companerodeescuela.core.motion.LocalCompaneroMotionPreferences

@Composable
fun ExpressiveSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(options.isNotEmpty()) { "options must not be empty" }
    val safeIndex = selectedIndex.coerceIn(options.indices)
    val reducedMotion = LocalCompaneroMotionPreferences.current.reducedMotion

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        val segmentWidth = maxWidth / options.size
        val targetOffset = segmentWidth * safeIndex
        val selectionOffset by animateDpAsState(
            targetValue = targetOffset,
            animationSpec = if (reducedMotion) {
                CompaneroMotion.fast()
            } else {
                CompaneroMotion.snappySpring()
            },
            label = "segmentedSelectionOffset",
        )

        Box(
            modifier = Modifier
                .offset(x = selectionOffset)
                .width(segmentWidth)
                .height(48.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.primaryContainer),
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .width(segmentWidth)
                        .height(48.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .clickable { onSelected(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        color = if (index == safeIndex) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}
