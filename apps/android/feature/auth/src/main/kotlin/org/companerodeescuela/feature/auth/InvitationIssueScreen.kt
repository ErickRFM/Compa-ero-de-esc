@file:Suppress("ktlint:standard:function-naming")

package org.companerodeescuela.feature.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.designsystem.v8.V8FrostedGlassPanel
import org.companerodeescuela.core.designsystem.v8.V8RedDimensions
import org.companerodeescuela.core.ui.component.NoticeTone
import org.companerodeescuela.core.ui.component.StatusNotice
import org.companerodeescuela.shared.contracts.VerificationDeliveryStatus

private data class InvitationProfileOption(
    val code: String,
    val label: String,
)

private val INVITATION_PROFILES =
    listOf(
        InvitationProfileOption("INSTITUTION_ADMIN", "Administrador"),
        InvitationProfileOption("TEACHER", "Docente"),
        InvitationProfileOption("TUTOR", "Tutor"),
        InvitationProfileOption("STUDENT", "Alumno"),
    )

@OptIn(ExperimentalLayoutApi::class)
@Suppress("ktlint:standard:function-naming")
@Composable
fun InvitationIssueScreen(
    onBack: () -> Unit,
    defaultInstitutionId: String? = null,
    viewModel: InvitationIssueViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var recipient by remember { mutableStateOf("") }
    var institutionId by remember(defaultInstitutionId) { mutableStateOf(defaultInstitutionId.orEmpty()) }
    var profile by remember { mutableStateOf("TEACHER") }
    var password by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    LaunchedEffect(state.institutions, defaultInstitutionId) {
        if (institutionId.isBlank()) {
            val matched =
                defaultInstitutionId?.takeIf { id -> state.institutions.any { it.id == id } }
                    ?: state.institutions.singleOrNull()?.id
            if (matched != null) institutionId = matched
        }
    }
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
        Text("Invitar al equipo", style = MaterialTheme.typography.headlineMedium, color = authInk())
        Text(
            "Envía una invitación de un solo uso para docentes, administradores, tutores o alumnos de tu carrera. Requiere MFA y confirmación de correo.",
            color = authMuted(),
        )
        V8FrostedGlassPanel(Modifier.fillMaxWidth(), themeAware = true) {
            Column(
                modifier = Modifier.padding(V8RedDimensions.ScreenHorizontal),
                verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
            ) {
                OutlinedTextField(
                    value = recipient,
                    onValueChange = { recipient = it.trim().take(128) },
                    label = { Text("Correo de destino") },
                    modifier = Modifier.fillMaxWidth().testTag("invite_recipient"),
                    singleLine = true,
                    enabled = !state.working,
                )
                if (state.institutions.isNotEmpty()) {
                    Text("Institución / carrera conectada a SIII", color = authInk())
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        state.institutions.forEach { institution ->
                            FilterChip(
                                selected = institutionId == institution.id,
                                onClick = { institutionId = institution.id },
                                label = { Text(institution.displayName) },
                                enabled = !state.working,
                                modifier = Modifier.testTag("invite_institution_${institution.id}"),
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = institutionId,
                    onValueChange = { institutionId = it.trim().take(128) },
                    label = { Text("ID de institución habilitada") },
                    modifier = Modifier.fillMaxWidth().testTag("invite_institution"),
                    singleLine = true,
                    enabled = !state.working,
                )
                Text("Perfil que recibirá", color = authInk())
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    INVITATION_PROFILES.forEach { option ->
                        FilterChip(
                            selected = profile == option.code,
                            onClick = { profile = option.code },
                            label = { Text(option.label) },
                            enabled = !state.working,
                            modifier = Modifier.testTag("invite_profile_${option.code}"),
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
                state.failureMessage?.let { detail ->
                    StatusNotice(
                        title = state.failureTitle ?: "No se pudo enviar",
                        message = detail,
                        tone = NoticeTone.ERROR,
                        modifier = Modifier.testTag("invite_error"),
                    )
                }
                state.delivery?.let { delivery ->
                    val (tone, title, detail) =
                        when (delivery) {
                            VerificationDeliveryStatus.ACCEPTED ->
                                Triple(
                                    NoticeTone.INFO,
                                    "Aceptada por proveedor (entrega pendiente)",
                                    "Resend aceptó la solicitud de envío; la entrega al buzón del destinatario aún debe confirmarse por webhook.",
                                )
                            VerificationDeliveryStatus.DELIVERED ->
                                Triple(
                                    NoticeTone.SUCCESS,
                                    "Invitación entregada",
                                    "Correo entregado y confirmado por webhook firmado.",
                                )
                            VerificationDeliveryStatus.UNCONFIRMED ->
                                Triple(
                                    NoticeTone.WARNING,
                                    "Entrega sin confirmar",
                                    "No se confirmó la entrega del correo. Puedes reintentar desde el historial.",
                                )
                            VerificationDeliveryStatus.FAILED ->
                                Triple(NoticeTone.ERROR, "Falló el envío", "El proveedor rechazó el envío.")
                            VerificationDeliveryStatus.UNAVAILABLE ->
                                Triple(NoticeTone.ERROR, "Correo no disponible", "El servicio de correo no está configurado.")
                            VerificationDeliveryStatus.PENDING ->
                                Triple(NoticeTone.WARNING, "Entrega en proceso", "La invitación está en cola de envío.")
                        }
                    StatusNotice(
                        title = title,
                        message = detail,
                        tone = tone,
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
