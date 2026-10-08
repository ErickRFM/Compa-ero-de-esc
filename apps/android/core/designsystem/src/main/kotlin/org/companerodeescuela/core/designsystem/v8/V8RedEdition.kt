package org.companerodeescuela.core.designsystem.v8

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
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
    emphasized: Boolean = highlighted,
    content: @Composable BoxScope.() -> Unit,
) {
    V8FrostedGlassPanel(
        modifier = modifier,
        cornerRadius = cornerRadius,
        contentPadding = contentPadding,
        emphasized = emphasized,
    ) {
        CompositionLocalProvider(LocalContentColor provides V8RedColors.TextPrimary) {
            content()
        }
    }
}

@Composable
fun V8RedPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(V8RedDimensions.ControlCorner)
    val accent = if (enabled) V8RedColors.Crimson else V8RedColors.DeepCrimson.copy(alpha = 0.4f)
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .shadow(if (enabled) 12.dp else 0.dp, shape, ambientColor = V8RedColors.Crimson, spotColor = V8RedColors.Crimson)
            .background(Brush.horizontalGradient(listOf(V8RedColors.DeepCrimson.copy(alpha = if (enabled) 1f else .4f), accent)), shape)
            .border(1.dp, V8RedColors.Crimson.copy(alpha = if (enabled) 1f else .5f), shape),
        shape = RoundedCornerShape(V8RedDimensions.ControlCorner),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            disabledContainerColor = Color.Transparent,
        ),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 16.dp),
    ) {
        Text(text = text, fontWeight = FontWeight.SemiBold)
    }
}
