package org.companerodeescuela.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.v8.*

@Composable
fun LoginScreen(
    state: SessionUiState,
    onLogin: (String, String) -> Unit,
    onCreateAccount: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var recoveryHelp by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val usernameError = InstitutionalCredentialValidator.identifierError(username)
    val passwordError = InstitutionalCredentialValidator.passwordError(password)
    val canSubmit = !state.submitting && usernameError == null && passwordError == null
    fun submit() {
        if (canSubmit) {
            focus.clearFocus()
            onLogin(username, password)
        }
    }
    MaterialTheme(colorScheme = V8ColorScheme) {
        Box(modifier.fillMaxSize().background(V8RedColors.Background)) {
            V8CampusBackdrop(Modifier.matchParentSize(), login = true)
            Column(
                modifier = Modifier.widthIn(max = CompaneroSize.loginContentMaxWidth)
                    .fillMaxWidth().align(Alignment.TopCenter)
                    .safeDrawingPadding().imePadding().verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 32.dp),
            ) {
                V8BrandHeader(stacked = true)
                Spacer(Modifier.height(32.dp))
                Text("Tu vida\nuniversitaria,", color = V8RedColors.TextPrimary,
                    fontSize = 32.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
                Text("más simple.", color = V8RedColors.Crimson,
                    fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
                Text("Organiza tus clases, mantente al día y alcanza tus metas. Todo en un solo lugar.",
                    color = V8RedColors.TextSecondary, fontSize = 14.sp, lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 12.dp))
                Box(Modifier.padding(top = 12.dp, bottom = 24.dp).size(32.dp, 2.dp).background(V8RedColors.Crimson))
                state.noticeMessage?.let { Text(it, color = V8RedColors.TextSecondary, modifier = Modifier.padding(bottom = 12.dp)) }
                V8GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        V8AcademicField(
                            value = username,
                            onValueChange = { username = InstitutionalCredentialValidator.sanitizeIdentifier(it) },
                            label = "Correo electrónico o matrícula", icon = Icons.Outlined.Email,
                            enabled = !state.submitting,
                            isError = username.isNotEmpty() && usernameError != null,
                            errorMessage = usernameError?.takeIf { username.isNotEmpty() },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        )
                        V8AcademicField(
                            value = password,
                            onValueChange = { password = InstitutionalCredentialValidator.sanitizePassword(it) },
                            label = "Contraseña", icon = Icons.Outlined.Lock, isPassword = true,
                            enabled = !state.submitting,
                            isError = password.isNotEmpty() && passwordError != null,
                            errorMessage = passwordError?.takeIf { password.isNotEmpty() },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                        )
                        TextButton(onClick = { recoveryHelp = true }, modifier = Modifier.align(Alignment.End)) {
                            Text("¿Olvidaste tu contraseña?", color = V8RedColors.TextSecondary)
                        }
                        state.errorMessage?.let { Text(it, color = V8RedColors.Error) }
                        V8RedPrimaryButton(
                            text = if (state.submitting) "Iniciando sesión…" else "Iniciar sesión",
                            onClick = ::submit, enabled = canSubmit, modifier = Modifier.fillMaxWidth(),
                        )
                        if (state.submitting) CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.CenterHorizontally))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            HorizontalDivider(Modifier.weight(1f))
                            Text("o", color = V8RedColors.TextSecondary)
                            HorizontalDivider(Modifier.weight(1f))
                        }
                        OutlinedButton(onClick = onCreateAccount, enabled = !state.submitting,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("Crear cuenta", color = V8RedColors.TextPrimary)
                        }
                    }
                }
                Text("DISCIPLINA HOY,\nMEJORES MAÑANAS", color = V8RedColors.TextSecondary,
                    fontSize = 10.sp, letterSpacing = 2.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 28.dp, bottom = 16.dp))
            }
        }
        if (recoveryHelp) AlertDialog(
            onDismissRequest = { recoveryHelp = false },
            title = { Text("Recuperar acceso") },
            text = { Text("La recuperación automática todavía no está disponible. Solicita ayuda al administrador de tu escuela para recuperar tu cuenta.") },
            confirmButton = { TextButton(onClick = { recoveryHelp = false }) { Text("Entendido") } },
        )
    }
}
