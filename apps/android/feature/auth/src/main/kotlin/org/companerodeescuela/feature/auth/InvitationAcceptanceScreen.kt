@file:Suppress("ktlint:standard:function-naming")

package org.companerodeescuela.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.designsystem.v8.V8FrostedGlassPanel
import org.companerodeescuela.core.designsystem.v8.V8RedDimensions
import org.companerodeescuela.core.ui.component.NoticeTone
import org.companerodeescuela.core.ui.component.StatusNotice

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
    val clipboard = LocalClipboardManager.current
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
                modifier = Modifier.padding(V8RedDimensions.ScreenHorizontal),
                verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
            ) {
                Text(
                    "Pega la clave completa del correo (ID.Código) o escribe el ID y el código por separado.",
                    color = authMuted(),
                )
                OutlinedButton(
                    onClick = {
                        val raw = clipboard.getText()?.text.orEmpty()
                        InvitationCredentialParser.parse(raw)?.let { parsed ->
                            invitationId = parsed.invitationId
                            code = parsed.code
                        }
                    },
                    enabled = !state.invitationWorking,
                    modifier = Modifier.fillMaxWidth().testTag("invitation_paste_combined"),
                ) {
                    Row {
                        Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pegar clave desde portapapeles")
                    }
                }
                OutlinedTextField(
                    value = invitationId,
                    onValueChange = { raw ->
                        val parsed = InvitationCredentialParser.parse(raw)
                        if (parsed != null) {
                            invitationId = parsed.invitationId
                            code = parsed.code
                        } else {
                            invitationId = raw.filterNot(Char::isWhitespace).take(43)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("invitation_id"),
                    label = { Text("ID de invitación") },
                    singleLine = true,
                    enabled = !state.invitationWorking,
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { raw ->
                        val parsed = InvitationCredentialParser.parse(raw)
                        if (parsed != null) {
                            invitationId = parsed.invitationId
                            code = parsed.code
                        } else {
                            code = raw.filterNot(Char::isWhitespace).take(43)
                        }
                    },
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
                        InvitationCredentialParser.isValidPart(invitationId) &&
                        InvitationCredentialParser.isValidPart(code),
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
                    StatusNotice(
                        title = "Invitación no aceptada",
                        message = it.userMessage,
                        tone = NoticeTone.ERROR,
                        modifier = Modifier.testTag("invitation_error"),
                    )
                }
                AuthV8SecondaryAction("Volver", onBack, !state.invitationWorking)
            }
        }
    }
}
