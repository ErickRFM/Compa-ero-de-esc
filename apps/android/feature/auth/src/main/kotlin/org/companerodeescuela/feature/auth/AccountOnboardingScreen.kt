@file:Suppress("ktlint:standard:function-naming")

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
import androidx.lifecycle.viewmodel.compose.viewModel
import org.companerodeescuela.core.designsystem.v8.V8FrostedGlassPanel
import org.companerodeescuela.core.ui.component.NoticeTone
import org.companerodeescuela.core.ui.component.StatusNotice
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.VerificationDeliveryStatus

/** Connected native account lifecycle, before any academic navigation is authorized. */
@Composable
fun AccountOnboardingScreen(
    state: SessionUiState,
    onVerify: (String) -> Unit,
    onResend: () -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onAcceptInvitation: (String, String) -> Unit = { _, _ -> },
    draft: AuthDraftViewModel = viewModel(),
) {
    var token by draft.verificationCode
    var showInvitationRescue by remember { mutableStateOf(false) }
    var rescueKeyInput by remember { mutableStateOf("") }
    var rescueId by remember { mutableStateOf("") }
    var rescueCode by remember { mutableStateOf("") }
    var rescueSeparate by remember { mutableStateOf(false) }

    LaunchedEffect(state.userId, state.accountStatus) {
        draft.bindVerificationAccount(state.userId, state.accountStatus)
    }
    LaunchedEffect(state.invitationAccepted) {
        if (state.invitationAccepted) {
            showInvitationRescue = false
            rescueKeyInput = ""
            rescueId = ""
            rescueCode = ""
        }
    }

    val verification = state.accountStatus == AccountStatus.PENDING_VERIFICATION
    val approval = state.accountStatus == AccountStatus.PENDING_APPROVAL
    val invitedConfirmed = approval && state.invitationAccepted

    AuthV8Layout(themeAware = true) {
        Text(
            stringResource(
                when {
                    verification -> R.string.verification_title
                    invitedConfirmed -> R.string.approval_invited_title
                    approval -> R.string.approval_title
                    else -> R.string.participant_title
                },
            ),
            color = authInk(),
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
        )
        state.displayName?.let { Text(it, color = authMuted(), modifier = Modifier.padding(top = 10.dp, bottom = 20.dp)) }
        V8FrostedGlassPanel(Modifier.fillMaxWidth(), emphasized = true, themeAware = true) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(
                        when {
                            verification -> R.string.verification_description
                            invitedConfirmed -> R.string.approval_invited_description
                            approval -> R.string.approval_description
                            else -> R.string.participant_description
                        },
                    ),
                    color = authMuted(),
                    lineHeight = 22.sp,
                )
                if (verification) {
                    state.email?.let { Text(it, color = authInk()) }
                    Text(
                        stringResource(
                            when (state.verificationDelivery) {
                                VerificationDeliveryStatus.ACCEPTED -> R.string.verification_accepted
                                VerificationDeliveryStatus.DELIVERED -> R.string.verification_delivered
                                VerificationDeliveryStatus.UNAVAILABLE -> R.string.verification_unavailable
                                VerificationDeliveryStatus.FAILED -> R.string.verification_failed
                                VerificationDeliveryStatus.UNCONFIRMED -> R.string.verification_unconfirmed
                                else -> R.string.verification_pending
                            },
                        ),
                        color = authMuted(),
                    )
                    OutlinedTextField(
                        token,
                        { token = it.trim().take(43) },
                        Modifier.fillMaxWidth().testTag("verification_code"),
                        label = { Text(stringResource(R.string.verification_code)) },
                        singleLine = true,
                        enabled = !state.submitting,
                        shape = RoundedCornerShape(18.dp),
                        colors = institutionalFieldColors(),
                    )
                    AuthV8PrimaryAction(
                        stringResource(R.string.verification_confirm),
                        { onVerify(token) },
                        !state.submitting && Regex("[A-Za-z0-9_-]{43}").matches(token),
                        state.submitting,
                        Modifier.testTag("verification_confirm"),
                        stringResource(R.string.verification_working),
                    )
                    AuthV8SecondaryAction(stringResource(R.string.verification_resend), onResend, !state.submitting)
                } else {
                    if (invitedConfirmed) {
                        StatusNotice(
                            title = stringResource(R.string.approval_invited_title),
                            message = "Invitación aceptada y pendiente de autorización final por Administración.",
                            tone = NoticeTone.SUCCESS,
                            modifier = Modifier.testTag("onboarding_invitation_confirmed"),
                        )
                    }
                    AuthV8PrimaryAction(
                        stringResource(R.string.approval_refresh),
                        onRefresh,
                        !state.submitting && !state.invitationWorking,
                        state.submitting,
                        Modifier.testTag("onboarding_refresh"),
                    )
                    if (approval && state.emailVerified && !state.invitationAccepted) {
                        AuthV8SecondaryAction(
                            stringResource(
                                if (showInvitationRescue) {
                                    R.string.onboarding_hide_invitation
                                } else {
                                    R.string.onboarding_have_invitation
                                },
                            ),
                            { showInvitationRescue = !showInvitationRescue },
                            !state.submitting && !state.invitationWorking,
                            Modifier.testTag("onboarding_toggle_invitation"),
                        )
                        if (showInvitationRescue) {
                            SmartInvitationKeySection(
                                keyInput = rescueKeyInput,
                                onKeyInputChange = { raw ->
                                    rescueKeyInput = raw.take(512)
                                    InvitationCredentialParser.parse(raw)?.let { parsed ->
                                        rescueId = parsed.invitationId
                                        rescueCode = parsed.code
                                    }
                                },
                                invitationId = rescueId,
                                onInvitationIdChange = { rescueId = it.filterNot(Char::isWhitespace).take(43) },
                                invitationCode = rescueCode,
                                onInvitationCodeChange = { rescueCode = it.filterNot(Char::isWhitespace).take(43) },
                                separateFields = rescueSeparate,
                                onToggleSeparateFields = { rescueSeparate = !rescueSeparate },
                                enabled = !state.invitationWorking && !state.submitting,
                                tagPrefix = "onboarding_invitation",
                            )
                            AuthV8PrimaryAction(
                                "Aceptar invitación",
                                { onAcceptInvitation(rescueId.trim(), rescueCode.trim()) },
                                !state.invitationWorking &&
                                    InvitationCredentialParser.isValidPart(rescueId) &&
                                    InvitationCredentialParser.isValidPart(rescueCode),
                                state.invitationWorking,
                                Modifier.testTag("onboarding_invitation_submit"),
                                "Validando...",
                            )
                            state.invitationFailure?.let { failure ->
                                StatusNotice(
                                    title = "Invitación no aceptada",
                                    message = failure.userMessage,
                                    tone = NoticeTone.ERROR,
                                    modifier = Modifier.testTag("onboarding_invitation_error"),
                                )
                            }
                        }
                    }
                }
                if (state.verificationFailure != null) {
                    val error = state.verificationFailure
                    Text(
                        stringResource(
                            if ((error as? org.companerodeescuela.core.common.result.AppError.Http)?.status == 400) {
                                R.string.verification_invalid
                            } else {
                                registrationErrorResource(error)
                            },
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                AuthV8SecondaryAction(stringResource(R.string.registration_logout), {
                    draft.clearAll()
                    onLogout()
                }, !state.submitting && !state.invitationWorking)
            }
        }
    }
}
