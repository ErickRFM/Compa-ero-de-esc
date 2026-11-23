package org.companerodeescuela.feature.auth

import org.companerodeescuela.core.common.result.AppError

/**
 * Localized, allowlisted invitation diagnostics. Never display server response messages
 * (including technicalDetail) because they may contain untrusted/internal data.
 */
internal fun invitationFailureMessage(error: AppError, stage: InvitationStage?): String {
    val http = error as? AppError.Http ?: return error.userMessage
    return when (http.apiCode) {
        "INVITATION_INSTITUTION_UNAVAILABLE" ->
            "La institución indicada no está habilitada para recibir registros. Verifica su ID."
        "MFA_ENROLLMENT_REQUIRED" ->
            "Debes activar y confirmar el segundo factor (MFA) en tu cuenta antes de invitar."
        "MFA_REAUTHENTICATION_FAILED" ->
            "La contraseña de confirmación es incorrecta. Verifícala e inténtalo otra vez."
        "MFA_CODE_INVALID" ->
            "El código de seis dígitos es incorrecto o expiró. Genera uno nuevo."
        "MFA_CODE_REUSED" ->
            "Ese código MFA ya se utilizó. Espera al siguiente código de tu aplicación."
        else -> when (http.status) {
            400, 422 -> when (stage) {
                InvitationStage.PREPARE ->
                    "Revisa el correo, el perfil y el ID de institución. Los datos de invitación no son válidos."
                InvitationStage.MFA ->
                    "No se pudo validar el alcance MFA de la operación. Vuelve a intentarlo."
                else -> "La invitación contiene datos inválidos. Revísalos antes de enviar."
            }
            403 -> when (stage) {
                InvitationStage.MFA ->
                    "No se autorizó la verificación MFA. Revisa tu contraseña, el código y que tu MFA esté activo."
                else ->
                    "Tu cuenta debe ser superadministradora activa y tener el correo verificado para invitar."
            }
            404 -> "La función de invitaciones no está disponible en este servidor. Contacta a soporte."
            409 -> "Esta invitación entra en conflicto con una operación anterior. Revisa antes de reintentar."
            429 -> "Límite de intentos alcanzado. Espera unos minutos y vuelve a intentarlo."
            503 -> "El servicio necesario para invitar (MFA, correo o API) no está disponible. Contacta a soporte."
            else -> error.userMessage
        }
    }
}
