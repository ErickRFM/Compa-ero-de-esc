package org.companerodeescuela.core.designsystem.v8

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** V8 Red Edition — fixed visual baseline for the five approved student mockups.
 *  The mockups are references, not runtime screen backgrounds.
 *  Keep semantic success/error colors independent of the crimson brand accent.
 */
object V8RedColors {
    val Background = Color(0xFF08090C)
    val Surface = Color(0xFF15171C)
    val Card = Color(0xFF252730)
    val Crimson = Color(0xFFFF303F)
    val DeepCrimson = Color(0xFFB31328)
    val TextPrimary = Color(0xFFF8F8FA)
    val TextSecondary = Color(0xFFABB0BE)
    val Outline = Color(0xFF454650)
    val Success = Color(0xFF35D98C)
    val Error = Color(0xFFFF5362)
}

object V8RedDimensions {
    val ScreenHorizontal = 20.dp
    val CardCorner = 22.dp
    val FieldCorner = 18.dp
    val ControlCorner = 16.dp
    val CardSpacing = 12.dp
}

@Composable
fun V8GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = V8RedDimensions.CardCorner,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    highlighted: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .shadow(if (highlighted) 10.dp else 2.dp, shape, ambientColor = V8RedColors.DeepCrimson)
            .background(
                // All stops are nearly opaque so the hero photograph cannot
                // interfere with small text, QR codes or schedule entries.
                brush = Brush.linearGradient(
                    if (highlighted) listOf(
                        Color(0xFF38131A), Color(0xFF1B141A), Color(0xFF270E16)
                    ) else listOf(
                        Color(0xF71D1E23), Color(0xF813151A), Color(0xF51F1118)
                    )
                ),
                shape = shape,
            )
            .border(
                BorderStroke(1.dp, if (highlighted) V8RedColors.Crimson else V8RedColors.Outline.copy(alpha = 0.72f)),
                shape,
            )
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun V8RedPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(V8RedDimensions.ControlCorner),
        colors = ButtonDefaults.buttonColors(
            containerColor = V8RedColors.Crimson,
            contentColor = Color.White,
            disabledContainerColor = V8RedColors.Outline,
        ),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 16.dp),
    ) {
        Text(text = text, fontWeight = FontWeight.SemiBold)
    }
}
