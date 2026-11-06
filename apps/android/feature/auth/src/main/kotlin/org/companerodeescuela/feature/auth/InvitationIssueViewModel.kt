package org.companerodeescuela.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.network.MfaRepository
import org.companerodeescuela.shared.contracts.MfaStepUpRequest
import org.companerodeescuela.shared.contracts.PrepareRegistrationInvitationRequest
import org.companerodeescuela.shared.contracts.StepUpAction
import org.companerodeescuela.shared.contracts.VerificationDeliveryStatus
import java.util.UUID
import javax.inject.Inject

data class InvitationIssueState(
    val working: Boolean = false,
    val delivery: VerificationDeliveryStatus? = null,
    val failure: AppError? = null,
)

@HiltViewModel
class InvitationIssueViewModel
    @Inject
    constructor(
        private val auth: AuthRepository,
        private val mfa: MfaRepository,
    ) : ViewModel() {
        private val mutable = MutableStateFlow(InvitationIssueState())
        val state: StateFlow<InvitationIssueState> = mutable.asStateFlow()

        /** Sensitive inputs are passed to a transient coroutine and never saved in UI state. */
        fun send(
            recipient: String,
            institutionId: String,
            profile: String,
            password: String,
            otp: String,
        ) {
            if (mutable.value.working) return
            mutable.value = InvitationIssueState(working = true)
            val request =
                PrepareRegistrationInvitationRequest(
                    recipient = recipient.trim().lowercase(),
                    institutionId = institutionId.trim(),
                    profile = profile,
                    operationId = UUID.randomUUID().toString(),
                )
            viewModelScope.launch {
                val prepared = auth.prepareRegistrationInvitation(request)
                if (prepared is Outcome.Failure) {
                    mutable.value = InvitationIssueState(failure = prepared.error)
                    return@launch
                }
                val scope = (prepared as Outcome.Success).value
                val proof =
                    mfa.stepUp(
                        MfaStepUpRequest(
                            password = password,
                            otp = otp,
                            action = StepUpAction.ADMIN_INVITE,
                            resourceId = scope.resourceId,
                            operationId = scope.operationId,
                            payloadDigest = scope.payloadDigest,
                        ),
                    )
                if (proof is Outcome.Failure) {
                    mutable.value = InvitationIssueState(failure = proof.error)
                    return@launch
                }
                val result = auth.sendRegistrationInvitation(request.copy(proof = (proof as Outcome.Success).value.proof))
                mutable.value =
                    when (result) {
                        is Outcome.Success -> InvitationIssueState(delivery = result.value.delivery)
                        is Outcome.Failure -> InvitationIssueState(failure = result.error)
                    }
            }
        }

        fun clearStatus() {
            if (!mutable.value.working) mutable.value = InvitationIssueState()
        }
    }
