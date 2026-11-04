package org.companerodeescuela.feature.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.companerodeescuela.core.designsystem.v8.V8FrostedGlassPanel
import org.companerodeescuela.shared.contracts.VerificationDeliveryStatus

@Composable
fun InvitationIssueScreen(
    onBack: () -> Unit,
    viewModel: InvitationIssueViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var recipient by remember { mutableStateOf("") }
    var institutionId by remember { mutableStateOf("") }
    var profile by remember { mutableStateOf("STUDENT") }
    var password by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    val context = LocalView.current.context
    DisposableEffect(context) {
        val window = context.findActivity()?.window
        val previouslySecure =
            ((window?.attributes?.flags ?: 0) and WindowManager.LayoutParams.FLAG_SECURE) != 0
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            if (!previouslySecure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
    AuthV8Layout(themeAware = true) {
        Text("Invitar persona", style = MaterialTheme.typography.headlineMedium, color = authInk())
        Text(
            "Envía una invitación desde tu cuenta autorizada. Se requiere MFA y el destinatario debe confirmar su correo.",
            color = authMuted(),
        )
        V8FrostedGlassPanel(Modifier.fillMaxWidth(), themeAware = true) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = recipient,
                    onValueChange = { recipient = it.trim().take(128) },
                    label = { Text("Correo de destino") },
                    modifier = Modifier.fillMaxWidth().testTag("invite_recipient"),
                    singleLine = true,
                    enabled = !state.working,
                )
                OutlinedTextField(
                    value = institutionId,
                    onValueChange = { institutionId = it.trim().take(128) },
                    label = { Text("ID de institución habilitada") },
                    modifier = Modifier.fillMaxWidth().testTag("invite_institution"),
                    singleLine = true,
                    enabled = !state.working,
                )
                Text("Perfil solicitado", color = authInk())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("STUDENT", "TEACHER", "TUTOR").forEach { option ->
                        FilterChip(
                            selected = profile == option,
                            onClick = { profile = option },
                            label = { Text(option.lowercase().replaceFirstChar { it.uppercase() }) },
                            enabled = !state.working,
                        )
                    }
                }
                SecurityMemoryField(
                    password,
                    { password = it.take(128) },
                    R.string.mfa_password,
                    "invite_password",
                    !state.working,
                    password = true,
                )
                SecurityMemoryField(
                    otp,
                    { otp = it.filter(Char::isDigit).take(6) },
                    R.string.mfa_otp,
                    "invite_otp",
                    !state.working,
                    otp = true,
                )
                AuthV8PrimaryAction(
                    "Enviar invitación",
                    {
                        val passwordInput = password
                        val otpInput = otp
                        password = ""
                        otp = ""
                        viewModel.send(recipient, institutionId, profile, passwordInput, otpInput)
                    },
                    !state.working &&
                        recipient.contains("@") &&
                        institutionId.isNotBlank() &&
                        password.length >= 8 &&
                        otp.length == 6,
                    state.working,
                    Modifier.testTag("invite_send"),
                    "Validando...",
                )
                state.failure?.let {
                    Text(it.userMessage, color = MaterialTheme.colorScheme.error)
                }
                state.delivery?.let {
                    Text(
                        when (it) {
                            VerificationDeliveryStatus.ACCEPTED -> "Proveedor aceptó el correo; la entrega final debe verificarse."
                            VerificationDeliveryStatus.DELIVERED -> "Correo entregado (confirmado por webhook firmado)."
                            VerificationDeliveryStatus.UNCONFIRMED -> "No se confirmó la entrega del correo."
                            VerificationDeliveryStatus.FAILED -> "Falló el envío."
                            VerificationDeliveryStatus.UNAVAILABLE -> "El correo no está configurado."
                            VerificationDeliveryStatus.PENDING -> "Entrega en proceso."
                        },
                        color = authMuted(),
                        modifier = Modifier.testTag("invite_delivery"),
                    )
                }
                AuthV8SecondaryAction("Volver", onBack, !state.working)
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
