package org.companerodeescuela.feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.companerodeescuela.core.designsystem.v8.V8FrostedGlassPanel
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.VerificationDeliveryStatus

/** Connected native account lifecycle, before any academic navigation is authorized. */
@Composable
fun AccountOnboardingScreen(state: SessionUiState, onVerify: (String) -> Unit, onResend: () -> Unit,
    onRefresh: () -> Unit, onLogout: () -> Unit) {
    var token by remember { mutableStateOf("") }
    val verification = state.accountStatus == AccountStatus.PENDING_VERIFICATION
    val approval = state.accountStatus == AccountStatus.PENDING_APPROVAL
    AuthV8Layout(themeAware = true) {
        Text(stringResource(if (verification) R.string.verification_title else if (approval) R.string.approval_title else R.string.participant_title),
            color = authInk(), fontSize = 30.sp, fontWeight = FontWeight.Bold)
        state.displayName?.let { Text(it, color = authMuted(), modifier = Modifier.padding(top = 10.dp, bottom = 20.dp)) }
        V8FrostedGlassPanel(Modifier.fillMaxWidth(), emphasized = true, themeAware = true) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(if (verification) R.string.verification_description else if (approval) R.string.approval_description else R.string.participant_description),
                    color = authMuted(), lineHeight = 22.sp)
                if (verification) {
                    state.email?.let { Text(it, color = authInk()) }
                    Text(stringResource(when (state.verificationDelivery) {
                        VerificationDeliveryStatus.ACCEPTED -> R.string.verification_accepted
                        VerificationDeliveryStatus.UNAVAILABLE -> R.string.verification_unavailable
                        VerificationDeliveryStatus.FAILED -> R.string.verification_failed
                        VerificationDeliveryStatus.UNCONFIRMED -> R.string.verification_unconfirmed
                        else -> R.string.verification_pending
                    }), color = authMuted())
                    OutlinedTextField(token, { token = it.trim().take(43) }, Modifier.fillMaxWidth().testTag("verification_code"),
                        label = { Text(stringResource(R.string.verification_code)) }, singleLine = true, enabled = !state.submitting,
                        shape = RoundedCornerShape(18.dp), colors = institutionalFieldColors())
                    AuthV8PrimaryAction(stringResource(R.string.verification_confirm), { onVerify(token) },
                        !state.submitting && Regex("[A-Za-z0-9_-]{43}").matches(token), state.submitting,
                        Modifier.testTag("verification_confirm"), stringResource(R.string.verification_working))
                    AuthV8SecondaryAction(stringResource(R.string.verification_resend), onResend, !state.submitting)
                } else AuthV8SecondaryAction(stringResource(R.string.approval_refresh), onRefresh, !state.submitting)
                if (state.verificationFailure != null) {
                    val error = state.verificationFailure
                    Text(stringResource(if ((error as? org.companerodeescuela.core.common.result.AppError.Http)?.status == 400)
                        R.string.verification_invalid else registrationErrorResource(error)), color = MaterialTheme.colorScheme.error)
                }
                AuthV8SecondaryAction(stringResource(R.string.registration_logout), onLogout, !state.submitting)
            }
        }
    }
}
