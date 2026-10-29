package org.companerodeescuela.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.companerodeescuela.core.common.result.AppError

enum class PushRegistrationState(
    val userMessage: String,
) {
    NOT_CONFIGURED("Las notificaciones no están configuradas en esta versión."),
    NOT_REGISTERED("Activa las notificaciones para recibir avisos de pase de lista."),
    REGISTERING("Registrando este dispositivo para recibir avisos…"),
    REGISTERED("Dispositivo registrado. La entrega de avisos depende de la conexión y los ajustes del teléfono."),
    PERMISSION_REQUIRED("Permite las notificaciones en los ajustes del teléfono para recibir avisos."),
    AUTH_REQUIRED("Inicia sesión nuevamente para registrar los avisos de este dispositivo."),
    REJECTED("No se pudo registrar este dispositivo. Revisa el estado de tu cuenta y vuelve a intentarlo."),
    RETRYING("Sin conexión con el servicio de avisos. El registro se reintentará automáticamente."),
}

/** No credentials, account identifiers or provider responses are exposed to the UI. */
object PushRegistrationDiagnostics {
    private val current = MutableStateFlow(PushRegistrationState.NOT_REGISTERED)
    val state: StateFlow<PushRegistrationState> = current.asStateFlow()

    internal fun update(state: PushRegistrationState) {
        current.value = state
    }
}

internal fun pushRegistrationFailure(error: AppError): PushRegistrationState =
    when {
        error is AppError.Http && error.status == 401 -> PushRegistrationState.AUTH_REQUIRED
        error is AppError.Network ||
            error is AppError.Http &&
            (error.status in setOf(408, 425, 429) || error.status in 500..599) -> PushRegistrationState.RETRYING
        else -> PushRegistrationState.REJECTED
    }
