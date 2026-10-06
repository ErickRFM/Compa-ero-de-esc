package org.companerodeescuela.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.shared.contracts.RegistrationAccountType

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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090D13))
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = CompaneroSize.loginContentMaxWidth)
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Crear cuenta",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Compañero funciona incluso si tu escuela no tiene una API conectada.",
                color = Color(0xFFBBC2CC),
                fontSize = 14.sp,
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F6F2)),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it.take(80) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !state.submitting,
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
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        AccountTypeOption(
                            label = "Alumno",
                            selected = accountType == RegistrationAccountType.STUDENT,
                            onClick = { accountType = RegistrationAccountType.STUDENT },
                        )
                        AccountTypeOption(
                            label = "Docente",
                            selected = accountType == RegistrationAccountType.TEACHER,
                            onClick = { accountType = RegistrationAccountType.TEACHER },
                        )
                    }
                    if (accountType == RegistrationAccountType.TEACHER) {
                        Text(
                            text = "Las cuentas docentes requieren verificación antes de crear clases o publicar.",
                            color = Color(0xFF69707A),
                            fontSize = 12.sp,
                        )
                    }

                    state.errorMessage?.let { message ->
                        Text(
                            text = message,
                            color = Color(0xFFB42318),
                            fontSize = 13.sp,
                        )
                    }

                    Button(
                        onClick = ::submit,
                        enabled = canSubmit,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state.submitting) "Creando cuenta…" else "Crear cuenta")
                    }
                    OutlinedButton(
                        onClick = onBackToLogin,
                        enabled = !state.submitting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Ya tengo cuenta")
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountTypeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text = label)
    }
}
