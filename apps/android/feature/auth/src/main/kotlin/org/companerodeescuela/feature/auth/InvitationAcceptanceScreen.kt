package org.companerodeescuela.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.companerodeescuela.core.designsystem.v8.V8FrostedGlassPanel

/** No code is persisted in saved state, logs or an intent. Redemption never grants a role. */
@Suppress("ktlint:standard:function-naming")
@Composable
fun InvitationAcceptanceScreen(
    state: SessionUiState,
    onAccept: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    var invitationId by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    LaunchedEffect(state.invitationAccepted) {
        if (state.invitationAccepted) {
            invitationId = ""
            code = ""
        }
    }
    AuthV8Layout(themeAware = true) {
        Text(
            "Aceptar invitación",
            style = MaterialTheme.typography.headlineMedium,
            color = authInk(),
        )
        V8FrostedGlassPanel(Modifier.fillMaxWidth(), emphasized = true, themeAware = true) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Usa el ID y código que recibiste por correo. Debes haber verificado tu dirección.",
                    color = authMuted(),
                )
                OutlinedTextField(
                    value = invitationId,
                    onValueChange = { invitationId = it.filterNot(Char::isWhitespace).take(43) },
                    modifier = Modifier.fillMaxWidth().testTag("invitation_id"),
                    label = { Text("ID de invitación") },
                    singleLine = true,
                    enabled = !state.invitationWorking,
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filterNot(Char::isWhitespace).take(43) },
                    modifier = Modifier.fillMaxWidth().testTag("invitation_code"),
                    label = { Text("Código recibido") },
                    singleLine = true,
                    enabled = !state.invitationWorking,
                )
                AuthV8PrimaryAction(
                    "Aceptar invitación",
                    { onAccept(invitationId, code) },
                    state.emailVerified &&
                        !state.invitationWorking &&
                        Regex("[A-Za-z0-9_-]{43}").matches(invitationId) &&
                        Regex("[A-Za-z0-9_-]{43}").matches(code),
                    state.invitationWorking,
                    Modifier.testTag("invitation_submit"),
                    "Validando...",
                )
                if (state.invitationAccepted) {
                    Text(
                        "Invitación aceptada. Los permisos dependen de la aprobación correspondiente.",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("invitation_success"),
                    )
                }
                state.invitationFailure?.let {
                    Text(
                        it.userMessage,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("invitation_error"),
                    )
                }
                AuthV8SecondaryAction("Volver a mi perfil", onBack, !state.invitationWorking)
            }
        }
    }
}
