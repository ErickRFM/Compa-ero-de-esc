package org.companerodeescuela.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.companerodeescuela.core.designsystem.v8.V8FrostedGlassPanel
import org.companerodeescuela.core.designsystem.v8.V8RedColors
import org.companerodeescuela.shared.contracts.RegistrationAccountType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RegistrationScreen(
    state: SessionUiState,
    onRegister: (String, String, String, RegistrationAccountType) -> Unit,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var accountType by remember { mutableStateOf(RegistrationAccountType.STUDENT) }
    val focusManager = LocalFocusManager.current

    val emailError = when {
        email.isEmpty() -> null
        !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email) -> "Ingresa un correo válido."
        else -> null
    }
    val passwordError = when {
        password.isEmpty() -> null
        password.length < 8 -> "Usa al menos 8 caracteres."
        else -> null
    }
    val confirmationError = when {
        confirmPassword.isEmpty() -> null
        confirmPassword != password -> "Las contraseñas no coinciden."
        else -> null
    }
    val canSubmit = !state.submitting &&
        displayName.trim().length >= 2 &&
        email.isNotBlank() &&
        emailError == null &&
        password.length >= 8 &&
        confirmationError == null &&
        confirmPassword.isNotEmpty()

    fun submit() {
        if (!canSubmit) return
        focusManager.clearFocus()
        onRegister(displayName.trim(), email.trim().lowercase(), password, accountType)
    }

    AuthV8Layout(modifier = modifier) {
        Text(
            text = "Crear cuenta",
            color = V8RedColors.TextPrimary,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Crea tu cuenta para organizar tus clases, horario y avisos.",
            modifier = Modifier.padding(top = 10.dp),
            color = V8RedColors.TextSecondary,
            fontSize = 15.sp,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(20.dp))
        V8FrostedGlassPanel(
            modifier = Modifier.fillMaxWidth(),
            emphasized = true,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Tus datos", color = V8RedColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it.take(80) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.submitting,
                    shape = RoundedCornerShape(18.dp),
                    colors = institutionalFieldColors(),
                    label = { Text("Nombre") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it.filterNot(Char::isWhitespace).take(128)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.submitting,
                    shape = RoundedCornerShape(18.dp),
                    colors = institutionalFieldColors(),
                    isError = emailError != null,
                    supportingText = emailError?.let { error -> { Text(error) } },
                    label = { Text("Correo") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(256) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.submitting,
                    shape = RoundedCornerShape(18.dp),
                    colors = institutionalFieldColors(),
                    isError = passwordError != null,
                    supportingText = passwordError?.let { error -> { Text(error) } },
                    label = { Text("Contraseña") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                    ),
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it.take(256) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.submitting,
                    shape = RoundedCornerShape(18.dp),
                    colors = institutionalFieldColors(),
                    isError = confirmationError != null,
                    supportingText = confirmationError?.let { error -> { Text(error) } },
                    label = { Text("Confirmar contraseña") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )

                Text(
                    text = "Tipo de cuenta",
                    modifier = Modifier.padding(top = 4.dp),
                    fontWeight = FontWeight.SemiBold,
                    color = V8RedColors.TextPrimary,
                )
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    // Keep the existing paired cards when both labels have room;
                    // enlarged text gets a full-width card for each choice.
                    val columns = if (maxWidth >= 280.dp * LocalDensity.current.fontScale) 2 else 1
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().selectableGroup(),
                        maxItemsInEachRow = columns,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AccountTypeOption(
                            label = "Alumno",
                            selected = accountType == RegistrationAccountType.STUDENT,
                            onClick = { accountType = RegistrationAccountType.STUDENT },
                            enabled = !state.submitting,
                            modifier = Modifier.weight(1f),
                        )
                        AccountTypeOption(
                            label = "Docente",
                            selected = accountType == RegistrationAccountType.TEACHER,
                            onClick = { accountType = RegistrationAccountType.TEACHER },
                            enabled = !state.submitting,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (accountType == RegistrationAccountType.TEACHER) {
                    Text(
                        text = "Las cuentas docentes requieren verificación para operar sus clases asignadas y publicar avisos.",
                        color = V8RedColors.TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }

                state.errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = V8RedColors.Error,
                        fontSize = 13.sp,
                    )
                }

                AuthV8PrimaryAction(
                    text = "Crear cuenta",
                    submitting = state.submitting,
                    submittingText = "Creando cuenta…",
                    onClick = ::submit,
                    enabled = canSubmit,
                    modifier = Modifier.padding(top = 6.dp),
                )
                AuthV8SecondaryAction(
                    text = "Ya tengo cuenta",
                    onClick = onBackToLogin,
                    enabled = !state.submitting,
                )
            }
        }
    }
}

@Composable
private fun AccountTypeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (selected) V8RedColors.Crimson.copy(alpha = 0.14f) else V8RedColors.Surface)
            .border(1.dp, if (selected) V8RedColors.Crimson else V8RedColors.Outline, shape)
            .selectable(selected = selected, onClick = onClick, enabled = enabled, role = Role.RadioButton)
            .heightIn(min = 56.dp)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(selectedColor = V8RedColors.Crimson, unselectedColor = V8RedColors.TextSecondary),
        )
        Text(label, modifier = Modifier.weight(1f), color = if (enabled) V8RedColors.TextPrimary else V8RedColors.TextSecondary, fontWeight = FontWeight.SemiBold)
    }
}
