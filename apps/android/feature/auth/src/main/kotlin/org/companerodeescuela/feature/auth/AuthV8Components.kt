package org.companerodeescuela.feature.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.LocalCompaneroHighContrast
import org.companerodeescuela.core.designsystem.v8.*
import org.companerodeescuela.core.motion.*

@Composable internal fun authDark() = MaterialTheme.colorScheme.background.luminance() < 0.5f
@Composable internal fun authInk() = if (authDark()) {
    if (LocalCompaneroHighContrast.current) Color.White else V8RedColors.TextPrimary
} else MaterialTheme.colorScheme.onSurface
@Composable internal fun authMuted() = if (authDark()) {
    if (LocalCompaneroHighContrast.current) Color(0xFFEEEEEE) else V8RedColors.TextSecondary
} else MaterialTheme.colorScheme.onSurfaceVariant
@Composable internal fun authAccent() = if (authDark()) V8RedColors.Crimson else V8RedColors.DeepCrimson

/** Same campus, geometry and tokens; colors come from the existing CompaneroTheme. */
@Composable
internal fun AuthV8Layout(
    modifier: Modifier = Modifier,
    themeAware: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (themeAware) {
        AuthV8Frame(modifier, content)
    } else {
        // Compatibility for any caller that explicitly requests the fixed V8 palette.
        MaterialTheme(colorScheme = V8ColorScheme) { AuthV8Frame(modifier, content) }
    }
}

@Composable
private fun AuthV8Frame(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val background = if (authDark()) {
        if (LocalCompaneroHighContrast.current) Color.Black else V8RedColors.Background
    } else MaterialTheme.colorScheme.background
    Box(modifier.fillMaxSize().background(background)) {
        V8CampusBackdrop(Modifier.matchParentSize(), login = true, backgroundColor = background)
        CompositionLocalProvider(LocalContentColor provides authInk()) {
            Column(Modifier.widthIn(max = CompaneroSize.loginContentMaxWidth).fillMaxWidth().align(Alignment.TopCenter)
                .statusBarsPadding().navigationBarsPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp)) {
                V8BrandHeader(contentColor = authInk(), secondaryColor = authMuted(), tagline = stringResource(R.string.auth_tagline))
                Spacer(Modifier.height(20.dp))
                content()
            }
        }
    }
}

@Composable
internal fun institutionalFieldColors(): TextFieldColors {
    val ink = authInk()
    val muted = authMuted()
    val accent = authAccent()
    val container = if (authDark()) Color(0xF31A1118) else MaterialTheme.colorScheme.surface
    val outline = if (LocalCompaneroHighContrast.current) ink else if (authDark()) Color(0xFF71616C) else MaterialTheme.colorScheme.outline
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = ink, unfocusedTextColor = ink, disabledTextColor = muted, errorTextColor = ink,
        focusedContainerColor = container, unfocusedContainerColor = container, disabledContainerColor = container, errorContainerColor = container,
        focusedBorderColor = accent, unfocusedBorderColor = outline,
        focusedLabelColor = accent, unfocusedLabelColor = muted, disabledLabelColor = muted,
        cursorColor = accent, errorCursorColor = MaterialTheme.colorScheme.error,
        errorBorderColor = MaterialTheme.colorScheme.error, errorLabelColor = MaterialTheme.colorScheme.error,
        errorSupportingTextColor = MaterialTheme.colorScheme.error,
    )
}

@Composable
internal fun AuthV8PrimaryAction(
    text: String, onClick: () -> Unit, enabled: Boolean, submitting: Boolean,
    modifier: Modifier = Modifier, submittingText: String? = null,
) {
    val duration = CompaneroMotionPolicy.resolve(MotionRole.STATE_CHANGE, LocalCompaneroMotionPreferences.current.reducedMotion).durationMillis
    Button(onClick, enabled = enabled, modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(
            containerColor = V8RedColors.DeepCrimson, contentColor = Color.White,
            disabledContainerColor = V8RedColors.DeepCrimson.copy(alpha = 0.3f), disabledContentColor = authMuted())) {
        AnimatedContent(submitting, transitionSpec = {
            fadeIn(tween(duration)).togetherWith(fadeOut(tween(duration)))
        }, label = "authSubmitState") { busy ->
            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    submittingText?.let { Text(it, fontWeight = FontWeight.SemiBold) }
                }
            } else Text(text, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun AuthV8SecondaryAction(text: String, onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    OutlinedButton(onClick, enabled = enabled, modifier = modifier.fillMaxWidth().heightIn(min = 54.dp),
        shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, authAccent().copy(alpha = 0.53f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (authDark()) Color(0xEF1A121A) else MaterialTheme.colorScheme.surface,
            contentColor = authInk(), disabledContentColor = authMuted())) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}
