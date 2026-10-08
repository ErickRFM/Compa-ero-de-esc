package org.companerodeescuela.core.designsystem.v8

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.companerodeescuela.core.designsystem.theme.LocalCompaneroHighContrast

/** Common V8 building blocks. Every control receives real state/events from its feature. */
@Composable
fun V8AcademicField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    isError: Boolean = false,
    enabled: Boolean = true,
    errorMessage: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val shape = RoundedCornerShape(V8RedDimensions.FieldCorner)
    val highContrast = LocalCompaneroHighContrast.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = V8RedColors.Crimson) },
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña",
                    )
                }
            }
        } else null,
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        supportingText = errorMessage?.let { message -> { Text(message) } },
        singleLine = true,
        isError = isError,
        enabled = enabled,
        shape = shape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = V8RedColors.TextPrimary,
            unfocusedTextColor = V8RedColors.TextPrimary,
            focusedContainerColor = V8RedColors.Surface.copy(alpha = if (highContrast) 1f else 0.96f),
            unfocusedContainerColor = V8RedColors.Surface.copy(alpha = if (highContrast) 1f else 0.92f),
            disabledContainerColor = V8RedColors.Surface,
            errorContainerColor = V8RedColors.Surface,
            focusedBorderColor = V8RedColors.Crimson,
            unfocusedBorderColor = if (highContrast) V8RedColors.TextPrimary else V8RedColors.TextSecondary.copy(alpha = 0.50f),
            focusedLabelColor = V8RedColors.TextSecondary,
            unfocusedLabelColor = V8RedColors.TextSecondary,
            cursorColor = V8RedColors.Crimson,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun V8SectionTitle(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = V8RedColors.TextPrimary)
        if (action != null && onAction != null) {
            Text(action, color = V8RedColors.TextSecondary, modifier = Modifier.clickable(onClick = onAction))
        }
    }
}

@Composable
fun V8HeroTitle(leading: String, accent: String, modifier: Modifier = Modifier) {
    Text(
        text = androidx.compose.ui.text.buildAnnotatedString {
            append(leading + " ")
            pushStyle(androidx.compose.ui.text.SpanStyle(color = V8RedColors.Crimson))
            append(accent)
            pop()
        },
        modifier = modifier,
        color = V8RedColors.TextPrimary, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold,
    )
}
