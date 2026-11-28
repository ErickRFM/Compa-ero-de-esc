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
import androidx.compose.ui.res.stringResource
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
    var pasteError by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(state.invitationAccepted) {
        if (state.invitationAccepted) {
            invitationId = ""
            code = ""
            pasteError = false
        }
    }

    fun updateInvitationId(raw: String) {
        val parsed = InvitationCredentialParser.parse(raw)
        if (parsed != null) {
            invitationId = parsed.invitationId
            code = parsed.code
        } else {
            val normalized = raw.filterNot(Char::isWhitespace).take(43)
            if (invitationId.isNotEmpty() && code.isNotEmpty() && normalized != invitationId) code = ""
            invitationId = normalized
        }
        pasteError = false
    }

    fun updateInvitationCode(raw: String) {
        val parsed = InvitationCredentialParser.parse(raw)
        if (parsed != null) {
            invitationId = parsed.invitationId
            code = parsed.code
        } else {
            val normalized = raw.filterNot(Char::isWhitespace).take(43)
            if (code.isNotEmpty() && invitationId.isNotEmpty() && normalized != code) invitationId = ""
            code = normalized
        }
        pasteError = false
    }

    fun pasteInvitation() {
        val parsed = InvitationCredentialParser.parse(clipboard.getText()?.text.orEmpty())
        if (parsed == null) {
            invitationId = ""
            code = ""
            pasteError = true
        } else {
            invitationId = parsed.invitationId
            code = parsed.code
            pasteError = false
        }
    }
    AuthV8Layout(themeAware = true) {
        Text(
            stringResource(R.string.invitation_accept_title),
            style = MaterialTheme.typography.headlineMedium,
            color = authInk(),
        )
        V8FrostedGlassPanel(Modifier.fillMaxWidth(), emphasized = true, themeAware = true) {
            Column(
                modifier = Modifier.padding(V8RedDimensions.ScreenHorizontal),
                verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
            ) {
                Text(
                    stringResource(R.string.invitation_accept_instructions),
                    color = authMuted(),
                )
                OutlinedButton(
                    onClick = {
                        pasteInvitation()
                    },
                    enabled = !state.invitationWorking,
                    modifier = Modifier.fillMaxWidth().testTag("invitation_paste_combined"),
                ) {
                    Row {
                        Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.invitation_paste_button))
                    }
                }
                if (pasteError) {
                    StatusNotice(
                        title = stringResource(R.string.invitation_paste_invalid),
                        message = stringResource(R.string.invitation_accept_instructions),
                        tone = NoticeTone.ERROR,
                        modifier = Modifier.testTag("invitation_paste_error"),
                    )
                }
                OutlinedTextField(
                    value = invitationId,
                    onValueChange = ::updateInvitationId,
                    modifier = Modifier.fillMaxWidth().testTag("invitation_id"),
                    label = { Text(stringResource(R.string.registration_invitation_id)) },
                    singleLine = true,
                    enabled = !state.invitationWorking,
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = ::updateInvitationCode,
                    modifier = Modifier.fillMaxWidth().testTag("invitation_code"),
                    label = { Text(stringResource(R.string.registration_invitation_code)) },
                    singleLine = true,
                    enabled = !state.invitationWorking,
                )
                AuthV8PrimaryAction(
                    stringResource(R.string.invitation_accept_action),
                    { onAccept(invitationId, code) },
                    state.emailVerified &&
                        !state.invitationWorking &&
                        InvitationCredentialParser.isValidPart(invitationId) &&
                        InvitationCredentialParser.isValidPart(code),
                    state.invitationWorking,
                    Modifier.testTag("invitation_submit"),
                    stringResource(R.string.invitation_accept_working),
                )
                if (state.invitationAccepted) {
                    Text(
                        stringResource(R.string.invitation_accept_success),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("invitation_success"),
                    )
                }
                state.invitationFailure?.let {
                    StatusNotice(
                        title = stringResource(R.string.invitation_accept_failure),
                        message = it.userMessage,
                        tone = NoticeTone.ERROR,
                        modifier = Modifier.testTag("invitation_error"),
                    )
                }
                AuthV8SecondaryAction(stringResource(R.string.security_back), onBack, !state.invitationWorking)
            }
        }
    }
}
