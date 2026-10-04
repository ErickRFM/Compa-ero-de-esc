package org.companerodeescuela.core.common.result

/**
 * Outcome of an operation that can fail in a way the UI has to explain.
 *
 * This is deliberately not `kotlin.Result`, because a sealed hierarchy lets
 * the compiler force every `when` to handle every case, and gives us a place
 * to carry a message that is safe to show to a student.
 */
sealed interface Outcome<out T> {
    data class Success<out T>(val value: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>

    val isSuccess: Boolean get() = this is Success

    fun valueOrNull(): T? = (this as? Success)?.value

    fun <R> map(transform: (T) -> R): Outcome<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }
}

/**
 * Errors the presentation layer is allowed to know about.
 *
 * Anything the user should not see (a stack trace, a DSN, a raw HTTP body)
 * belongs in [technicalDetail], which is logged but never rendered.
 */
sealed interface AppError {
    val userMessage: String
    val technicalDetail: String?

    /** The request never reached the server, or the connection dropped. */
    data class Network(override val technicalDetail: String? = null) : AppError {
        override val userMessage: String =
            "No pudimos conectar. Revisa tu conexión a internet e inténtalo de nuevo."
    }

    /** The server answered with a non-success status. */
    data class Http(val status: Int, override val technicalDetail: String? = null) : AppError {
        override val userMessage: String = when (status) {
            401 -> "Tu sesión expiró. Inicia sesión nuevamente."
            403 -> "No tienes permiso para ver esta información."
            404 -> "No encontramos la información que buscas."
            409 -> "La operación entra en conflicto con el estado actual."
            422 -> "Revisa la información enviada e inténtalo nuevamente."
            429 -> "Demasiados intentos. Espera un momento e inténtalo de nuevo."
            in 500..599 -> "El servicio no está disponible en este momento."
            else -> "No pudimos completar la solicitud."
        }
    }

    /** The response arrived but did not match the agreed contract. */
    data class Serialization(override val technicalDetail: String? = null) : AppError {
        override val userMessage: String =
            "Recibimos una respuesta que no pudimos interpretar."
    }

    /** Secure device storage/keystore could not persist the authenticated session. */
    data class Storage(override val technicalDetail: String? = null) : AppError {
        override val userMessage: String =
            "No pudimos guardar tu sesión de forma segura. Inténtalo nuevamente."
    }

    data class Unknown(override val technicalDetail: String? = null) : AppError {
        override val userMessage: String =
            "No pudimos completar la operación. Inténtalo nuevamente."
    }
}
