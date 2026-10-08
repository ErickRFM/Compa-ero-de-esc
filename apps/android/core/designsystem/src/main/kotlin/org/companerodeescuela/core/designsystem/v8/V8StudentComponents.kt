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
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(V8RedDimensions.FieldCorner)
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
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
        ),
        singleLine = true,
        isError = isError,
        enabled = enabled,
        shape = shape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = V8RedColors.TextPrimary,
            unfocusedTextColor = V8RedColors.TextPrimary,
            focusedContainerColor = V8RedColors.Surface.copy(alpha = 0.94f),
            unfocusedContainerColor = V8RedColors.Surface.copy(alpha = 0.88f),
            focusedBorderColor = V8RedColors.Crimson,
            unfocusedBorderColor = V8RedColors.Outline,
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
fun V8StatePill(text: String, active: Boolean, modifier: Modifier = Modifier) {
    val color = if (active) V8RedColors.Success else V8RedColors.TextSecondary
    Row(modifier = modifier.semantics { contentDescription = text }.background(V8RedColors.Surface, RoundedCornerShape(50)).border(BorderStroke(1.dp, color.copy(alpha = 0.6f)), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Text(text, color = color, fontSize = 12.sp)
    }
}

@Composable
fun V8HeroTitle(leading: String, accent: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(leading, color = V8RedColors.TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text(accent, color = V8RedColors.Crimson, fontSize = 32.sp, fontWeight = FontWeight.Bold)
    }
}
