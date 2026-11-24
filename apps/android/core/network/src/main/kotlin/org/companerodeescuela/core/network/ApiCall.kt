package org.companerodeescuela.core.network

import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.shared.contracts.ApiError

/**
 * Unifies HTTP and transport failures. Never render untrusted server error text.
 */
suspend fun <T> apiCall(block: suspend () -> Outcome<T>): Outcome<T> =
    try {
        block()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: ResponseException) {
        Outcome.Failure(e.response.errorFromResponse())
    } catch (e: java.io.IOException) {
        Outcome.Failure(AppError.Network(e.message))
    } catch (e: kotlinx.serialization.SerializationException) {
        Outcome.Failure(AppError.Serialization(e.message))
    } catch (e: io.ktor.serialization.ContentConvertException) {
        Outcome.Failure(AppError.Serialization(e.message))
    } catch (e: IllegalStateException) {
        Outcome.Failure(AppError.Serialization(e.message))
    } catch (e: Exception) {
        Outcome.Failure(AppError.Unknown(e.message))
    }

/** Only preserve bounded printable server correlation IDs. */
@PublishedApi
internal fun HttpResponse.safeRequestId(): String? = headers["X-Request-Id"].safeCorrelationId()

private fun String?.safeCorrelationId(): String? =
    this?.takeIf { value ->
        value.length in 1..80 && value.all { (it.isLetterOrDigit() && it.code < 128) || it == '-' }
    }

@PublishedApi
internal val wireErrorJson = Json { ignoreUnknownKeys = true }

/** Longest error body retained for diagnostics; never shown directly to users. */
const val MAX_ERROR_BODY: Int = 512

/** Upper bound for decoding untrusted error JSON into a structured wire contract. */
private const val MAX_PARSED_ERROR_BODY: Int = 16_384

/** Parse non-success responses in one place, including Ktor ResponseException. */
@PublishedApi
internal suspend fun HttpResponse.errorFromResponse(): AppError.Http {
    val raw = runCatching { bodyAsText() }.getOrNull()
    val parsed =
        raw?.takeIf { it.length <= MAX_PARSED_ERROR_BODY }?.let { rawBody ->
            runCatching { wireErrorJson.decodeFromString<ApiError>(rawBody) }.getOrNull()
        }
    return AppError.Http(
        status = status.value,
        technicalDetail = raw?.take(MAX_ERROR_BODY),
        requestId = safeRequestId() ?: parsed?.requestId.safeCorrelationId(),
        apiCode = parsed?.code?.name,
    )
}

/** Reads a successful body, or reports a structured HTTP failure. */
suspend inline fun <reified T> HttpResponse.requireBody(): Outcome<T> =
    if (status.value in 200..299) {
        apiCall { Outcome.Success(body<T>()) }
    } else {
        Outcome.Failure(errorFromResponse())
    }

/**
 * Convenience for empty-success endpoints; deliberately does not read error bodies.
 * This preserves the existing no-error-body diagnostic contract.
 */
suspend fun HttpResponse.requireUnit(): Outcome<Unit> =
    if (status.value in 200..299) {
        Outcome.Success(Unit)
    } else {
        Outcome.Failure(AppError.Http(status.value, status.description, safeRequestId()))
    }

/** True when the caller must re-authenticate. */
fun AppError.isUnauthorized(): Boolean = this is AppError.Http && status == 401

/** True when retrying the same request could plausibly succeed. */
fun AppError.isRetryable(): Boolean =
    when (this) {
        is AppError.Network -> true
        is AppError.Http -> status >= 500
        is AppError.Serialization, is AppError.Storage, is AppError.Unknown -> false
    }
