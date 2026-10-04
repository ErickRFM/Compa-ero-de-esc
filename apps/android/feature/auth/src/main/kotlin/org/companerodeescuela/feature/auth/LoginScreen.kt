package org.companerodeescuela.feature.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.companerodeescuela.core.designsystem.theme.CompaneroExpressive
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.motion.CompaneroMotionDuration
import org.companerodeescuela.core.motion.LocalCompaneroMotionPreferences

@Composable
fun LoginScreen(
    state: SessionUiState,
    onLogin: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var entered by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val reducedMotion = LocalCompaneroMotionPreferences.current.reducedMotion
    val canSubmit = !state.submitting && username.isNotBlank() && password.isNotBlank()

    LaunchedEffect(Unit) {
        entered = true
    }

    fun submit() {
        if (!canSubmit) return
        focusManager.clearFocus()
        onLogin(username.trim(), password)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CompaneroExpressive.subduedHeroBrush())
            .padding(CompaneroSpacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = entered,
            enter = fadeIn(
                tween(
                    if (reducedMotion) {
                        CompaneroMotionDuration.FAST
                    } else {
                        CompaneroMotionDuration.EMPHASIZED
                    },
                ),
            ) + slideInVertically(
                animationSpec = tween(
                    if (reducedMotion) 0 else CompaneroMotionDuration.EMPHASIZED,
                ),
            ) { height -> if (reducedMotion) 0 else height / 14 },
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                tonalElevation = 10.dp,
                shadowElevation = 12.dp,
            ) {
                Column(
                    modifier = Modifier.padding(CompaneroSpacing.xl),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "COMPAÑERO",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Tu escuela, en el momento correcto.",
                        modifier = Modifier.padding(top = CompaneroSpacing.xs),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = "Entra con tu cuenta institucional.",
                        modifier = Modifier.padding(
                            top = CompaneroSpacing.xs,
                            bottom = CompaneroSpacing.xl,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.submitting,
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        label = { Text("Usuario institucional") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = CompaneroSpacing.sm),
                        enabled = !state.submitting,
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        label = { Text("Contraseña") },
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
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                    )

                    state.errorMessage?.let { message ->
                        Text(
                            text = message,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = CompaneroSpacing.sm),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    Button(
                        onClick = ::submit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = CompaneroSpacing.lg),
                        enabled = canSubmit,
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        AnimatedContent(
                            targetState = state.submitting,
                            transitionSpec = {
                                fadeIn(tween(CompaneroMotionDuration.FAST))
                                    .togetherWith(fadeOut(tween(CompaneroMotionDuration.FAST)))
                            },
                            label = "loginSubmitState",
                        ) { submitting ->
                            if (submitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Text("Entrar")
                            }
                        }
                    }
                }
            }
        }
    }
}
