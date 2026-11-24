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
import org.companerodeescuela.shared.contracts.InstitutionSummary
import org.companerodeescuela.shared.contracts.MfaStepUpRequest
import org.companerodeescuela.shared.contracts.PrepareRegistrationInvitationRequest
import org.companerodeescuela.shared.contracts.StepUpAction
import org.companerodeescuela.shared.contracts.VerificationDeliveryStatus
import java.util.UUID
import javax.inject.Inject

enum class InvitationIssueStep {
    PREPARE,
    MFA_STEP_UP,
    DISPATCH,
}

data class InvitationIssueState(
    val working: Boolean = false,
    val delivery: VerificationDeliveryStatus? = null,
    val failure: AppError? = null,
    val failureStep: InvitationIssueStep? = null,
    val institutions: List<InstitutionSummary> = emptyList(),
    val institutionsLoading: Boolean = false,
    val institutionsFailure: AppError? = null,
) {
    val institutionsErrorMessage: String?
        get() =
            institutionsFailure?.let { err ->
                val requestId = (err as? AppError.Http)?.requestId
                if (requestId != null) "${err.userMessage}\nReferencia: $requestId" else err.userMessage
            }
    val failureTitle: String?
        get() =
            if (failure == null) {
                null
            } else {
                when (failureStep) {
                    InvitationIssueStep.PREPARE -> "No se pudo preparar la invitación"
                    InvitationIssueStep.MFA_STEP_UP -> "Error en la verificación MFA"
                    InvitationIssueStep.DISPATCH -> "No se pudo enviar la invitación"
                    null -> "No se pudo enviar"
                }
            }

    val failureMessage: String?
        get() = failure?.let { formatInvitationFailure(failureStep, it) }
}

internal fun formatInvitationFailure(
    step: InvitationIssueStep?,
    error: AppError,
): String {
    val mappedDetail = invitationTypedError(error)
    if (mappedDetail != null) {
        val requestId = (error as? AppError.Http)?.requestId
        return if (requestId == null) mappedDetail else "$mappedDetail\nReferencia: $requestId"
    }
    val detail =
        when (error) {
            is AppError.Network ->
                when (step) {
                    InvitationIssueStep.PREPARE ->
                        "Sin conexión con el servidor al preparar la invitación. Revisa tu internet e inténtalo de nuevo."
                    InvitationIssueStep.MFA_STEP_UP ->
                        "Sin conexión con el servidor al validar MFA. Revisa tu internet e inténtalo de nuevo."
                    InvitationIssueStep.DISPATCH ->
                        "Sin conexión con el servidor al enviar la invitación. Revisa tu internet e inténtalo de nuevo."
                    null -> error.userMessage
                }
            is AppError.Http ->
                when (step) {
                    InvitationIssueStep.PREPARE ->
                        when (error.status) {
                            404, 503 ->
                                "El servicio de invitaciones no está habilitado en el servidor (HTTP ${error.status}). " +
                                    "Verifica PRODUCTION_INVITATIONS_ENABLED y el despliegue de la API."
                            400, 422 ->
                                "Los datos de la invitación no son válidos (HTTP ${error.status}). " +
                                    "Revisa el correo, el ID de institución habilitada y el perfil seleccionado."
                            401 ->
                                "Tu sesión expiró o cambió de estado (HTTP 401). Cierra sesión e inicia nuevamente."
                            403 ->
                                "Se requiere una cuenta SUPER_ADMIN activa y con correo verificado para emitir invitaciones (HTTP 403)."
                            429 ->
                                "Demasiados intentos de invitación (HTTP 429). Espera unos minutos e inténtalo de nuevo."
                            in 500..599 ->
                                "Error del servidor al preparar la invitación (HTTP ${error.status})."
                            else -> "${error.userMessage} (Paso: preparación, HTTP ${error.status})."
                        }
                    InvitationIssueStep.MFA_STEP_UP ->
                        when (error.status) {
                            403 ->
                                "Verificación MFA rechazada (HTTP 403). Verifica que tu contraseña sea correcta, " +
                                    "el código OTP de 6 dígitos esté vigente y tu cuenta tenga MFA activado."
                            401 ->
                                "Tu sesión no es válida o la cuenta no está activa para validar MFA (HTTP 401)."
                            400, 422 ->
                                "Solicitud de verificación MFA inválida (HTTP ${error.status}). Intenta nuevamente."
                            429 ->
                                "Demasiados intentos fallidos de MFA (HTTP 429). Espera unos minutos antes de reintentar."
                            503 ->
                                "El servicio MFA no está configurado en el servidor (HTTP 503)."
                            in 500..599 ->
                                "Error del servidor al verificar MFA (HTTP ${error.status})."
                            else -> "${error.userMessage} (Paso: verificación MFA, HTTP ${error.status})."
                        }
                    InvitationIssueStep.DISPATCH ->
                        when (error.status) {
                            403 ->
                                "La prueba MFA fue rechazada o expiró al enviar (HTTP 403). " +
                                    "Intenta nuevamente con un código OTP nuevo."
                            409 ->
                                "Esta operación de invitación ya fue procesada o entró en conflicto (HTTP 409). " +
                                    "Intenta enviar nuevamente."
                            400, 422 ->
                                "El servidor rechazó los datos de la invitación (HTTP ${error.status}). " +
                                    "Revisa el correo y la institución."
                            404, 503 ->
                                "El servicio de invitaciones no está disponible en el servidor (HTTP ${error.status})."
                            429 ->
                                "Demasiados intentos de envío (HTTP 429). Espera unos minutos e inténtalo de nuevo."
                            in 500..599 ->
                                "Error del servidor al enviar la invitación (HTTP ${error.status})."
                            else -> "${error.userMessage} (Paso: envío, HTTP ${error.status})."
                        }
                    null -> "${error.userMessage} (HTTP ${error.status})."
                }
            is AppError.Serialization, is AppError.Storage, is AppError.Unknown -> error.userMessage
        }
    val requestId = (error as? AppError.Http)?.requestId
    return if (requestId != null) "$detail\nReferencia: $requestId" else detail
}

@HiltViewModel
class InvitationIssueViewModel
    @Inject
    constructor(
        private val auth: AuthRepository,
        private val mfa: MfaRepository,
    ) : ViewModel() {
        private val mutable = MutableStateFlow(InvitationIssueState())
        val state: StateFlow<InvitationIssueState> = mutable.asStateFlow()

        init {
            loadInstitutions()
        }

        fun loadInstitutions() {
            if (mutable.value.institutionsLoading) return
            mutable.value = mutable.value.copy(institutionsLoading = true, institutionsFailure = null)
            viewModelScope.launch {
                mutable.value =
                    when (val result = auth.institutions()) {
                        is Outcome.Success ->
                            mutable.value.copy(
                                institutions = result.value,
                                institutionsLoading = false,
                                institutionsFailure = null,
                            )
                        is Outcome.Failure ->
                            mutable.value.copy(
                                institutionsLoading = false,
                                institutionsFailure = result.error,
                            )
                    }
            }
        }

        /** Sensitive inputs are passed to a transient coroutine and never saved in UI state. */
        fun send(
            recipient: String,
            institutionId: String,
            profile: String,
            password: String,
            otp: String,
        ) {
            if (mutable.value.working) return
            mutable.value = mutable.value.copy(working = true, delivery = null, failure = null, failureStep = null)
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
                    mutable.value =
                        mutable.value.copy(
                            working = false,
                            failure = prepared.error,
                            failureStep = InvitationIssueStep.PREPARE,
                        )
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
                    mutable.value =
                        mutable.value.copy(
                            working = false,
                            failure = proof.error,
                            failureStep = InvitationIssueStep.MFA_STEP_UP,
                        )
                    return@launch
                }
                val result = auth.sendRegistrationInvitation(request.copy(proof = (proof as Outcome.Success).value.proof))
                mutable.value =
                    when (result) {
                        is Outcome.Success ->
                            mutable.value.copy(
                                working = false,
                                delivery = result.value.delivery,
                                failure = null,
                                failureStep = null,
                            )
                        is Outcome.Failure ->
                            mutable.value.copy(
                                working = false,
                                delivery = null,
                                failure = result.error,
                                failureStep = InvitationIssueStep.DISPATCH,
                            )
                    }
            }
        }

        fun clearStatus() {
            if (!mutable.value.working) {
                mutable.value = mutable.value.copy(delivery = null, failure = null, failureStep = null)
            }
        }
    }
