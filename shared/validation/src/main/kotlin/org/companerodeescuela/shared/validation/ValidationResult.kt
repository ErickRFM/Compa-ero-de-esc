package org.companerodeescuela.shared.validation

/**
 * Outcome of validating a single value or a group of values.
 */
sealed interface ValidationResult {
    data object Valid : ValidationResult

    data class Invalid(val failures: List<ValidationFailure>) : ValidationResult {
        init {
            require(failures.isNotEmpty()) { "Invalid results must carry at least one failure" }
        }
    }

    val isValid: Boolean
        get() = this is Valid

    /**
     * Human-readable summary, safe to log at startup. Never includes the
     * offending values themselves, only field names and reasons.
     */
    fun describe(): String = when (this) {
        Valid -> "valid"
        is Invalid -> failures.joinToString(separator = "; ") { "${it.field}: ${it.reason}" }
    }
}

/**
 * A single validation problem, identified by field name and reason.
 *
 * The rejected value is deliberately absent: these objects travel to logs and
 * error responses, and must never carry secrets.
 */
data class ValidationFailure(
    val field: String,
    val reason: String,
)
