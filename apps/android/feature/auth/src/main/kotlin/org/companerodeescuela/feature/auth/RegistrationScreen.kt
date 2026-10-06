package org.companerodeescuela.feature.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import org.companerodeescuela.core.designsystem.brand.UptlaxBrand
import org.companerodeescuela.core.designsystem.theme.CompaneroElevation
import org.companerodeescuela.core.designsystem.theme.CompaneroExpressive
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.motion.CompaneroMotionDuration

@Composable
fun RegistrationScreen(
    state: SessionUiState,
    onActivate: (String, String) -> Unit,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var institutionalId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val institutionalIdError = InstitutionalCredentialValidator.identifierError(institutionalId)
    val passwordError = InstitutionalCredentialValidator.passwordError(password)
    val canSubmit = !state.submitting && institutionalIdError == null && passwordError == null

    fun submit() {
        if (!canSubmit) return
        focusManager.clearFocus()
        onActivate(institutionalId, password)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CompaneroExpressive.subduedHeroBrush())
            .imePadding()
            .padding(CompaneroSpacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = CompaneroSize.loginContentMaxWidth)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            UptlaxBrand(
                modifier = Modifier
                    .fillMaxWidth(0.68f)
                    .height(CompaneroSize.brandLogoHeight),
                tint = Color.White,
            )
            Text(
                text = "Activar acceso",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )
            Text(
                text = "Valida tu matrícula o ID directamente con la cuenta institucional. Compañero no guarda tu contraseña de la escuela.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.82f),
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                tonalElevation = CompaneroElevation.raised,
                shadowElevation = CompaneroElevation.immersive,
            ) {
                Column(
                    modifier = Modifier.padding(CompaneroSpacing.xl),
                    verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
                ) {
                    Text(
                        text = "Identidad institucional",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "Usaremos estos datos únicamente para que el proveedor institucional confirme quién eres y qué rol te corresponde.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    OutlinedTextField(
                        value = institutionalId,
                        onValueChange = {
                            institutionalId = InstitutionalCredentialValidator.sanitizeIdentifier(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.submitting,
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        label = { Text("Matrícula, correo o ID institucional") },
                        isError = institutionalId.isNotEmpty() && institutionalIdError != null,
                        supportingText = institutionalIdError
                            ?.takeIf { institutionalId.isNotEmpty() }
                            ?.let { error -> { Text(error) } },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        ),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = InstitutionalCredentialValidator.sanitizePassword(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.submitting,
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        label = { Text("Contraseña institucional") },
                        isError = password.isNotEmpty() && passwordError != null,
                        supportingText = passwordError
                            ?.takeIf { password.isNotEmpty() }
                            ?.let { error -> { Text(error) } },
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) {
                                        Icons.Filled.VisibilityOff
                                    } else {
                                        Icons.Filled.Visibility
                                    },
                                    contentDescription = if (passwordVisible) {
                                        "Ocultar contraseña"
                                    } else {
                                        "Mostrar contraseña"
                                    },
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                    )

                    state.errorMessage?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    Button(
                        onClick = ::submit,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canSubmit,
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        AnimatedContent(
                            targetState = state.submitting,
                            transitionSpec = {
                                fadeIn(tween(CompaneroMotionDuration.FAST))
                                    .togetherWith(fadeOut(tween(CompaneroMotionDuration.FAST)))
                            },
                            label = "registrationSubmitState",
                        ) { submitting ->
                            if (submitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(CompaneroSize.indicator),
                                    strokeWidth = CompaneroSize.indicatorStroke,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Text("Validar y continuar")
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onBackToLogin,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.submitting,
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Text("Ya tengo acceso")
                    }
                }
            }
        }
    }
}
