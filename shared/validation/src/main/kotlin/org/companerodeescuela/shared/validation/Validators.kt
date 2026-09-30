package org.companerodeescuela.shared.validation

/**
 * Small, dependency-free validators for configuration-shaped values.
 *
 * Scope is intentionally narrow: these exist so that startup configuration
 * fails loudly and consistently instead of propagating nulls into the runtime.
 * They are not a general-purpose validation framework, and they are not used
 * for business rule validation.
 */
object Validators {

    /** Accepts a value with at least one non-whitespace character. */
    fun notBlank(field: String, value: String?): ValidationResult =
        if (value.isNullOrBlank()) {
            ValidationResult.Invalid(listOf(ValidationFailure(field, "must not be blank")))
        } else {
            ValidationResult.Valid
        }

    /**
     * Accepts a blank value (treated as "not configured") or an absolute
     * `http`/`https` URI.
     */
    fun absoluteHttpUrl(field: String, value: String?): ValidationResult {
        if (value.isNullOrBlank()) return ValidationResult.Valid
        val uri = runCatching { java.net.URI(value) }.getOrNull()
            ?: return invalid(field, "must be a valid URI")
        val scheme = uri.scheme?.lowercase()
        val isAbsolute = uri.isAbsolute && !uri.host.isNullOrBlank()
        return if (isAbsolute && (scheme == "http" || scheme == "https")) {
            ValidationResult.Valid
        } else {
            invalid(field, "must be an absolute http(s) URL")
        }
    }

    /** Accepts a TCP port in the range 1..65535. */
    fun port(field: String, value: String?): ValidationResult {
        if (value.isNullOrBlank()) return invalid(field, "must not be blank")
        val parsed = value.trim().toIntOrNull()
            ?: return invalid(field, "must be a number")
        return if (parsed in 1..65535) {
            ValidationResult.Valid
        } else {
            invalid(field, "must be between 1 and 65535")
        }
    }

    /** Accepts a non-negative integer, expressed as text. */
    fun nonNegativeInt(field: String, value: String?): ValidationResult {
        if (value.isNullOrBlank()) return invalid(field, "must not be blank")
        val parsed = value.trim().toIntOrNull()
            ?: return invalid(field, "must be a number")
        return if (parsed >= 0) {
            ValidationResult.Valid
        } else {
            invalid(field, "must be greater than or equal to 0")
        }
    }

    private fun invalid(field: String, reason: String): ValidationResult =
        ValidationResult.Invalid(listOf(ValidationFailure(field, reason)))
}
