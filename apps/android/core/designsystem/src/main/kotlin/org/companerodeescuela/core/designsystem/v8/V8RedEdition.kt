@file:Suppress("ktlint:standard:function-naming")

package org.companerodeescuela.core.designsystem.v8

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.companerodeescuela.core.designsystem.theme.LocalCompaneroAccentPalette

/** V8 Red Edition — fixed visual baseline for the five approved student mockups.
 *  The mockups are references, not runtime screen backgrounds.
 *  Keep semantic success/error colors independent of the crimson brand accent.
 */
object V8RedColors {
    val Background = Color(0xFF191A1C)
    val Surface = Color(0xFF222326)
    val Card = Color(0xFF2B2D30)
    val Crimson = Color(0xFF8A1538)
    val DeepCrimson = Color(0xFF8A1538)
    val TextPrimary = Color(0xFFF8F8FA)
    val TextSecondary = Color(0xFFABB0BE)
    val Outline = Color(0xFF454650)
    val Success = Color(0xFF35D98C)
    val Error = Color(0xFFFF5362)
}

object V8RedDimensions {
    val ScreenHorizontal = 16.dp
    val CardCorner = 20.dp
    val FieldCorner = 16.dp
    val ControlCorner = 14.dp
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
    val light = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val palette = LocalCompaneroAccentPalette.current
    val solidAccent = if (light) MaterialTheme.colorScheme.primary else palette.darkContainer
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(V8RedDimensions.ControlCorner),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = solidAccent,
                contentColor = Color.White,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(text = text, fontWeight = FontWeight.SemiBold)
    }
}
