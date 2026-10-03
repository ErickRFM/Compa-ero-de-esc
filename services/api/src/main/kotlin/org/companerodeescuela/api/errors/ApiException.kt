package org.companerodeescuela.api.errors

import io.ktor.http.HttpStatusCode
import org.companerodeescuela.shared.contracts.ApiError
import org.companerodeescuela.shared.contracts.ApiErrorCode

/**
 * Errors that are safe to translate into a client-visible response.
 *
 * Anything that is *not* an [ApiException] is treated as a bug by the
 * `StatusPages` fallback and reported as a generic 500 without details.
 */
sealed class ApiException(
    val httpStatus: HttpStatusCode,
    val errorCode: ApiErrorCode,
    val userMessage: String,
    cause: Throwable? = null,
) : RuntimeException(userMessage, cause) {

    class Validation(
        message: String,
        val fieldErrors: List<org.companerodeescuela.shared.contracts.ApiFieldError> = emptyList(),
    ) : ApiException(
        httpStatus = HttpStatusCode.BadRequest,
        errorCode = ApiErrorCode.VALIDATION_ERROR,
        userMessage = message,
    )

    class Unauthorized(
        message: String = "Authentication is required",
    ) : ApiException(
        httpStatus = HttpStatusCode.Unauthorized,
        errorCode = ApiErrorCode.UNAUTHORIZED,
        userMessage = message,
    )

    class Forbidden(
        message: String = "You are not allowed to perform this action",
    ) : ApiException(
        httpStatus = HttpStatusCode.Forbidden,
        errorCode = ApiErrorCode.FORBIDDEN,
        userMessage = message,
    )

    class NotFound(
        message: String = "Resource not found",
    ) : ApiException(
        httpStatus = HttpStatusCode.NotFound,
        errorCode = ApiErrorCode.NOT_FOUND,
        userMessage = message,
    )

    class Conflict(
        message: String,
    ) : ApiException(
        httpStatus = HttpStatusCode.Conflict,
        errorCode = ApiErrorCode.CONFLICT,
        userMessage = message,
    )

    class Domain(
        status: HttpStatusCode,
        code: ApiErrorCode,
        message: String,
    ) : ApiException(
        httpStatus = status,
        errorCode = code,
        userMessage = message,
    )

    class RateLimited(
        message: String = "Too many authentication attempts",
    ) : ApiException(
        httpStatus = HttpStatusCode.TooManyRequests,
        errorCode = ApiErrorCode.RATE_LIMITED,
        userMessage = message,
    )

    /**
     * A dependency the request needs is unreachable. The client may retry, so
     * this maps to 503 rather than 500.
     */
    class DependencyUnavailable(
        message: String = "A required dependency is unavailable",
        cause: Throwable? = null,
    ) : ApiException(
        httpStatus = HttpStatusCode.ServiceUnavailable,
        errorCode = ApiErrorCode.DEPENDENCY_UNAVAILABLE,
        userMessage = message,
        cause = cause,
    )

    class Internal(
        message: String = "Unexpected server error",
        cause: Throwable? = null,
    ) : ApiException(
        httpStatus = HttpStatusCode.InternalServerError,
        errorCode = ApiErrorCode.INTERNAL_ERROR,
        userMessage = message,
        cause = cause,
    )

    fun httpStatus(): HttpStatusCode = httpStatus
}

/**
 * Builds the public error payload.
 *
 * `requestId` is always echoed so a user can report a problem with a
 * traceable id, and the internal exception message is never propagated.
 */
fun ApiException.toApiError(requestId: String?): ApiError = ApiError(
    code = errorCode,
    message = userMessage,
    requestId = requestId,
    fieldErrors = when (this) {
        is ApiException.Validation -> fieldErrors
        else -> emptyList()
    },
)
