package org.companerodeescuela.feature.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import org.companerodeescuela.core.designsystem.v8.V8RedColors
import org.companerodeescuela.core.designsystem.v8.V8BrandHeader
import org.companerodeescuela.core.designsystem.v8.V8FrostedGlassPanel
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.companerodeescuela.core.designsystem.brand.UptlaxBrand
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.motion.CompaneroMotionDuration

private val UptlaxBackground = V8RedColors.Background
private val UptlaxSurface = V8RedColors.Surface.copy(alpha = 0.94f)
private val UptlaxAccent = V8RedColors.Crimson
private val UptlaxAccentSoft = V8RedColors.TextSecondary
private val UptlaxInk = V8RedColors.TextPrimary
private val UptlaxMuted = V8RedColors.TextSecondary
private val UptlaxBorder = V8RedColors.Outline

@Composable
fun LoginScreen(
    state: SessionUiState,
    onLogin: (String, String) -> Unit,
    onCreateAccount: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val usernameError = InstitutionalCredentialValidator.identifierError(username)
    val passwordError = InstitutionalCredentialValidator.passwordError(password)
    val canSubmit = !state.submitting && usernameError == null && passwordError == null
    val hasSessionNotice = !state.noticeMessage.isNullOrBlank()

    fun submit() {
        if (!canSubmit) return
        focusManager.clearFocus()
        onLogin(username, password)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(UptlaxBackground, Color(0xFF16070C), UptlaxBackground)))
            .imePadding(),
    ) {
        CampusLoginBackdrop()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = CompaneroSize.loginContentMaxWidth)
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            V8BrandHeader()

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Tu vida universitaria,",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "más simple.",
                modifier = Modifier.padding(top = 3.dp),
                color = UptlaxAccent,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )

            Text(
                "Organiza tus clases, mantente al día y alcanza tus metas. Todo en un solo lugar.",
                color = UptlaxMuted,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                modifier = Modifier.padding(top = 13.dp, bottom = 12.dp),
            )

            if (hasSessionNotice) Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = UptlaxSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (hasSessionNotice) "ACCESO REQUERIDO" else "TU DÍA ACADÉMICO",
                            color = UptlaxAccentSoft,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (hasSessionNotice) "SESIÓN" else "ACCESO",
                            color = UptlaxAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Text(
                        text = if (hasSessionNotice) {
                            "Vuelve a iniciar sesión"
                        } else {
                            "Pendiente de iniciar sesión"
                        },
                        modifier = Modifier.padding(top = 8.dp),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    Text(
                        text = state.noticeMessage
                            ?: "Ingresa con tu cuenta de Compañero para consultar clases, horario y asistencia.",
                        modifier = Modifier.padding(top = 4.dp),
                        color = Color(0xFFADB4BF),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            V8FrostedGlassPanel(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 22.dp)) {
                    Text(
                        text = "Iniciar sesión",
                        color = UptlaxInk,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Accede con tu cuenta escolar o matrícula.",
                        modifier = Modifier.padding(top = 3.dp, bottom = 14.dp),
                        color = UptlaxMuted,
                        fontSize = 13.sp,
                    )

                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = InstitutionalCredentialValidator.sanitizeIdentifier(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.submitting,
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        label = { Text("Correo electrónico o matrícula") },
                        isError = username.isNotEmpty() && usernameError != null,
                        supportingText = usernameError
                            ?.takeIf { username.isNotEmpty() }
                            ?.let { error -> { Text(error) } },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        ),
                        colors = institutionalFieldColors(),
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = InstitutionalCredentialValidator.sanitizePassword(it)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        enabled = !state.submitting,
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        label = { Text("Contraseña") },
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
                                    tint = UptlaxMuted,
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        colors = institutionalFieldColors(),
                    )

                    state.errorMessage?.let { message ->
                        Text(
                            text = message,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            color = V8RedColors.Error,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                    }

                    Button(
                        onClick = ::submit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp)
                            .height(56.dp)
                            .shadow(
                                elevation = if (canSubmit) 11.dp else 0.dp,
                                shape = RoundedCornerShape(18.dp),
                                ambientColor = UptlaxAccent.copy(alpha = 0.48f),
                                spotColor = UptlaxAccent.copy(alpha = 0.40f),
                            ),
                        enabled = canSubmit,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = UptlaxAccent,
                            contentColor = Color.White,
                            disabledContainerColor = UptlaxAccent.copy(alpha = 0.30f),
                            disabledContentColor = Color.White.copy(alpha = 0.82f),
                        ),
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
                                    color = Color.White,
                                )
                            } else {
                                Text(
                                    text = "Iniciar sesión  →",
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 22.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = Color(0x665B5360),
                        )
                        Text("O", color = UptlaxMuted, fontSize = 13.sp)
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = Color(0x665B5360),
                        )
                    }

                    OutlinedButton(
                        onClick = onCreateAccount,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        enabled = !state.submitting,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, UptlaxAccent.copy(alpha = 0.53f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xEF1A121A),
                            contentColor = UptlaxInk,
                        ),
                    ) {
                        Text(
                            text = "Crear cuenta",
                            color = UptlaxInk,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Text(
                text = "DISCIPLINA HOY, MEJORES MAÑANAS",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 18.dp),
                color = Color(0xFFCDC4CA),
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun institutionalFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = UptlaxInk,
    unfocusedTextColor = UptlaxInk,
    focusedContainerColor = Color(0xF31A1118),
    unfocusedContainerColor = Color(0xED171419),
    disabledContainerColor = Color(0xED171419),
    focusedBorderColor = UptlaxAccent,
    unfocusedBorderColor = Color(0xFF71616C),
    focusedLabelColor = UptlaxAccent,
    unfocusedLabelColor = UptlaxMuted,
    cursorColor = UptlaxAccent,
    errorBorderColor = V8RedColors.Error,
    errorLabelColor = V8RedColors.Error,
)
