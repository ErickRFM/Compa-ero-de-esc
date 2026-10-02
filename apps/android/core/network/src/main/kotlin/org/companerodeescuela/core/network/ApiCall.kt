package org.companerodeescuela.core.network

import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.common.result.Outcome

/**
 * Runs a request that already reports its own outcome, and converts any
 * transport-level exception into an [Outcome] failure.
 *
 * The block is expected to return an [Outcome] (see [requireBody] and
 * [requireUnit]) rather than a raw value, so a protocol-level failure and a
 * connection-level failure arrive at the caller as the same type. Wrapping a
 * raw value here instead would nest one `Outcome` inside another and turn
 * every HTTP error into a success.
 */
suspend fun <T> apiCall(block: suspend () -> Outcome<T>): Outcome<T> = try {
    block()
} catch (e: ResponseException) {
    Outcome.Failure(e.toAppError())
} catch (e: java.io.IOException) {
    Outcome.Failure(AppError.Network(e.message))
} catch (e: kotlinx.serialization.SerializationException) {
    Outcome.Failure(AppError.Serialization(e.message))
} catch (e: io.ktor.serialization.ContentConvertException) {
    // Ktor wraps a JSON mismatch in its own type, which is not a
    // kotlinx SerializationException, so it has to be caught separately.
    Outcome.Failure(AppError.Serialization(e.message))
} catch (e: IllegalStateException) {
    // Ktor reports a malformed or empty body this way.
    Outcome.Failure(AppError.Serialization(e.message))
} catch (e: Exception) {
    Outcome.Failure(AppError.Unknown(e.message))
}

private fun ResponseException.toAppError(): AppError {
    val detail = runCatching { response.status.description }.getOrNull()
    return AppError.Http(status = response.status.value, technicalDetail = detail)
}

/** Longest error body kept for the log. Error bodies are never shown to users. */
const val MAX_ERROR_BODY: Int = 512

/** Reads a successful body, or reports the status as a failure. */
suspend inline fun <reified T> HttpResponse.requireBody(): Outcome<T> =
    if (status.value in 200..299) {
        apiCall { Outcome.Success(body<T>()) }
    } else {
        Outcome.Failure(
            AppError.Http(
                status = status.value,
                technicalDetail = runCatching { bodyAsText() }.getOrNull()?.take(MAX_ERROR_BODY),
            ),
        )
    }

/** Convenience for endpoints whose success payload is empty. */
suspend fun HttpResponse.requireUnit(): Outcome<Unit> =
    if (status.value in 200..299) Outcome.Success(Unit)
    else Outcome.Failure(AppError.Http(status.value, status.description))

/** True when the caller must re-authenticate. */
fun AppError.isUnauthorized(): Boolean = this is AppError.Http && status == 401

/** True when retrying the same request could plausibly succeed. */
fun AppError.isRetryable(): Boolean = when (this) {
    is AppError.Network -> true
    is AppError.Http -> status >= 500
    is AppError.Serialization, is AppError.Unknown -> false
}
