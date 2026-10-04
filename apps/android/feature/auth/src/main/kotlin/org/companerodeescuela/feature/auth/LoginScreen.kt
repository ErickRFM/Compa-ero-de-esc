package org.companerodeescuela.feature.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.companerodeescuela.core.motion.CompaneroMotionDuration

private val UptlaxBackground = Color(0xFF090D13)
private val UptlaxSurface = Color(0xFF111722)
private val UptlaxAccent = Color(0xFFD20A3E)
private val UptlaxAccentSoft = Color(0xFFF7DDE5)
private val UptlaxPaper = Color(0xFFF7F6F2)
private val UptlaxInk = Color(0xFF17191D)
private val UptlaxMuted = Color(0xFF69707A)
private val UptlaxBorder = Color(0xFFD5D8DE)

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
    val canSubmit = !state.submitting && username.isNotBlank() && password.isNotBlank()

    fun submit() {
        if (!canSubmit) return
        focusManager.clearFocus()
        onLogin(username.trim(), password)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(UptlaxBackground),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val startX = size.width * 0.72f
            val endX = size.width * 0.98f
            val topY = size.height * 0.04f
            repeat(5) { index ->
                val offset = index * 18f
                drawLine(
                    color = UptlaxAccent.copy(alpha = 0.68f),
                    start = Offset(startX + offset, topY),
                    end = Offset(endX, topY + 145f + offset),
                    strokeWidth = 2f,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 18.dp),
        ) {
            Image(
                painter = painterResource(id = R.drawable.uptlax_logo),
                contentDescription = "Universidad Politécnica de Tlaxcala",
                modifier = Modifier
                    .fillMaxWidth(0.48f)
                    .height(52.dp),
                alignment = Alignment.CenterStart,
                contentScale = ContentScale.Fit,
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Compañero de Clase",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Tu vida académica en contexto.",
                modifier = Modifier.padding(top = 4.dp),
                color = Color(0xFFBBC2CC),
                fontSize = 15.sp,
            )

            Spacer(modifier = Modifier.height(22.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = UptlaxSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "HOY · 10:00",
                            color = UptlaxAccentSoft,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "ASISTENCIA",
                            color = UptlaxAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = "Programación móvil",
                        modifier = Modifier.padding(top = 10.dp),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Laboratorio A · Asistencia abre 09:55",
                        modifier = Modifier.padding(top = 4.dp),
                        color = Color(0xFFADB4BF),
                        fontSize = 13.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = UptlaxPaper),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Acceso institucional",
                        color = UptlaxInk,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Usa la misma cuenta de tu universidad.",
                        modifier = Modifier.padding(top = 4.dp, bottom = 18.dp),
                        color = UptlaxMuted,
                        fontSize = 13.sp,
                    )

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.submitting,
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        label = { Text("Correo / usuario") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        colors = institutionalFieldColors(),
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        enabled = !state.submitting,
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
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
                                    tint = UptlaxMuted,
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        colors = institutionalFieldColors(),
                    )

                    state.errorMessage?.let { message ->
                        Text(
                            text = message,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            color = Color(0xFFB42318),
                            fontSize = 13.sp,
                        )
                    }

                    Button(
                        onClick = ::submit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp)
                            .height(52.dp),
                        enabled = canSubmit,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = UptlaxAccent,
                            contentColor = Color.White,
                            disabledContainerColor = UptlaxAccent.copy(alpha = 0.35f),
                            disabledContentColor = Color.White.copy(alpha = 0.72f),
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

                    OutlinedButton(
                        onClick = onCreateAccount,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .height(48.dp),
                        enabled = !state.submitting,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = "Activar acceso",
                            color = UptlaxAccent,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Text(
                text = "UPTLAX · Datos seguros · Uso académico",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, bottom = 24.dp),
                color = Color(0xFF7F8792),
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun institutionalFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = UptlaxInk,
    unfocusedTextColor = UptlaxInk,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    disabledContainerColor = Color(0xFFF0F0EE),
    focusedBorderColor = UptlaxAccent,
    unfocusedBorderColor = UptlaxBorder,
    focusedLabelColor = UptlaxAccent,
    unfocusedLabelColor = UptlaxMuted,
    cursorColor = UptlaxAccent,
)
