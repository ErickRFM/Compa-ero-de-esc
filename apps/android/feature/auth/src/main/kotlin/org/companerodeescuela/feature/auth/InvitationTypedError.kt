package org.companerodeescuela.feature.auth

import org.companerodeescuela.core.common.result.AppError

/** Localized allowlist. The API's raw message is intentionally never shown. */
internal fun invitationTypedError(error: AppError): String? =
    when ((error as? AppError.Http)?.apiCode) {
        "INVITATION_INSTITUTION_UNAVAILABLE" ->
            "La institución indicada no está habilitada para recibir registros. Verifica el ID."
        "MFA_ENROLLMENT_REQUIRED" ->
            "Debes activar y confirmar el segundo factor (MFA) en tu cuenta antes de invitar."
        "MFA_REAUTHENTICATION_FAILED" ->
            "La contraseña de confirmación es incorrecta. Verifícala e inténtalo otra vez."
        "MFA_CODE_INVALID" ->
            "El código MFA es incorrecto o expiró. Genera uno nuevo."
        "MFA_CODE_REUSED" ->
            "Ese código MFA ya se utilizó. Espera al siguiente código de tu aplicación."
        else -> null
    }
