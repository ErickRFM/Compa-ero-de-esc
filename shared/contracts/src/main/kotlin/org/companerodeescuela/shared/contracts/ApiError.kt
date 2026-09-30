package org.companerodeescuela.shared.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Machine-readable error codes returned by the API.
 *
 * Clients must branch on these codes, never on human-readable messages.
 * The enum is append-only: existing values keep their meaning.
 */
@Serializable
enum class ApiErrorCode {
    @SerialName("validation_error")
    VALIDATION_ERROR,

    @SerialName("unauthorized")
    UNAUTHORIZED,

    @SerialName("forbidden")
    FORBIDDEN,

    @SerialName("not_found")
    NOT_FOUND,

    @SerialName("conflict")
    CONFLICT,

    @SerialName("rate_limited")
    RATE_LIMITED,

    @SerialName("dependency_unavailable")
    DEPENDENCY_UNAVAILABLE,

    @SerialName("internal_error")
    INTERNAL_ERROR,
}

/**
 * Single error payload used by every non-2xx API response.
 *
 * @param code machine-readable identifier, safe to branch on.
 * @param message human-readable description, safe to log but not to display
 *   verbatim to end users without a localized mapping.
 * @param requestId correlation id, also emitted in the `X-Request-Id` header.
 *   Always present so a user can report a problem with a traceable id.
 * @param fieldErrors optional per-field details, used for form validation.
 */
@Serializable
data class ApiError(
    val code: ApiErrorCode,
    val message: String,
    val requestId: String? = null,
    val fieldErrors: List<ApiFieldError> = emptyList(),
)

/**
 * Field-scoped validation detail attached to an [ApiError].
 */
@Serializable
data class ApiFieldError(
    val field: String,
    val code: ApiErrorCode,
    val message: String,
)
