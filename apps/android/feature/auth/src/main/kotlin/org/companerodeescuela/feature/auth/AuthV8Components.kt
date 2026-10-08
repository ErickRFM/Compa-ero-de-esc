package org.companerodeescuela.feature.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.v8.V8BrandHeader
import org.companerodeescuela.core.designsystem.v8.V8CampusBackdrop
import org.companerodeescuela.core.designsystem.v8.V8RedColors
import org.companerodeescuela.core.motion.CompaneroMotionDuration

/** Login and registration share the existing V8 assets and accessible scroll frame. */
@Composable
internal fun AuthV8Layout(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().background(V8RedColors.Background)) {
        V8CampusBackdrop(Modifier.matchParentSize(), login = true)
        CompositionLocalProvider(LocalContentColor provides V8RedColors.TextPrimary) {
            Column(
                modifier = Modifier
                    .widthIn(max = CompaneroSize.loginContentMaxWidth)
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                V8BrandHeader()
                Spacer(Modifier.height(20.dp))
                content()
            }
        }
    }
}

/** Extracted from Login so registration uses the same fields and error contrast. */
@Composable
internal fun institutionalFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = V8RedColors.TextPrimary,
    unfocusedTextColor = V8RedColors.TextPrimary,
    disabledTextColor = V8RedColors.TextSecondary,
    errorTextColor = V8RedColors.TextPrimary,
    focusedContainerColor = Color(0xF31A1118),
    unfocusedContainerColor = Color(0xED171419),
    disabledContainerColor = Color(0xED171419),
    errorContainerColor = Color(0xED171419),
    focusedBorderColor = V8RedColors.Crimson,
    unfocusedBorderColor = Color(0xFF71616C),
    focusedLabelColor = V8RedColors.Crimson,
    unfocusedLabelColor = V8RedColors.TextSecondary,
    disabledLabelColor = V8RedColors.TextSecondary,
    cursorColor = V8RedColors.Crimson,
    errorCursorColor = V8RedColors.Error,
    errorBorderColor = V8RedColors.Error,
    errorLabelColor = V8RedColors.Error,
    errorSupportingTextColor = V8RedColors.Error,
)

/** The existing Login action, shared without imposing a fixed text height. */
@Composable
internal fun AuthV8PrimaryAction(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    submitting: Boolean,
    modifier: Modifier = Modifier,
    submittingText: String? = null,
) {
    val shape = RoundedCornerShape(18.dp)
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).shadow(
            elevation = if (enabled) 11.dp else 0.dp,
            shape = shape,
            ambientColor = V8RedColors.Crimson.copy(alpha = 0.48f),
            spotColor = V8RedColors.Crimson.copy(alpha = 0.40f),
        ),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = V8RedColors.Crimson,
            contentColor = Color.White,
            disabledContainerColor = V8RedColors.Crimson.copy(alpha = 0.30f),
            disabledContentColor = Color.White.copy(alpha = 0.82f),
        ),
    ) {
        AnimatedContent(
            targetState = submitting,
            transitionSpec = {
                fadeIn(tween(CompaneroMotionDuration.FAST))
                    .togetherWith(fadeOut(tween(CompaneroMotionDuration.FAST)))
            },
            label = "authSubmitState",
        ) { busy ->
            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    submittingText?.let { Text(it, fontWeight = FontWeight.SemiBold) }
                }
            } else {
                Text(text, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun AuthV8SecondaryAction(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 54.dp),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, V8RedColors.Crimson.copy(alpha = 0.53f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color(0xEF1A121A),
            contentColor = V8RedColors.TextPrimary,
            disabledContentColor = V8RedColors.TextSecondary,
        ),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}
