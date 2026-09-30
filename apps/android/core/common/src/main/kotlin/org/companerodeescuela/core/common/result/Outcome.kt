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
            "No pudimos conectar. Revisa tu conexion a internet e intentalo de nuevo."
    }

    /** The server answered with a non-success status. */
    data class Http(val status: Int, override val technicalDetail: String? = null) : AppError {
        override val userMessage: String = when (status) {
            401 -> "Tu sesion expiro. Inicia sesion nuevamente."
            403 -> "No tienes permiso para ver esta informacion."
            404 -> "No encontramos la informacion que buscas."
            in 500..599 -> "El servicio no esta disponible en este momento."
            else -> "Ocurrio un error inesperado."
        }
    }

    /** The response arrived but did not match the agreed contract. */
    data class Serialization(override val technicalDetail: String? = null) : AppError {
        override val userMessage: String =
            "Recibimos una respuesta que no pudimos interpretar."
    }

    data class Unknown(override val technicalDetail: String? = null) : AppError {
        override val userMessage: String = "Ocurrio un error inesperado."
    }
}
