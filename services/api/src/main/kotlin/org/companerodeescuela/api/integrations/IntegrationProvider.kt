package org.companerodeescuela.api.integrations

/**
 * Marker for every adapter that talks to an external institutional system.
 *
 * The platform never calls an external system directly from a route or a
 * repository: the only path is
 *
 * ```
 * route -> service -> repository -> provider (adapter) -> external system
 * ```
 *
 * Keeping [id] and [displayName] on the interface makes the active adapters
 * observable, which matters when an institution runs more than one system.
 */
interface IntegrationProvider {
    /** Stable identifier used in configuration and logs, for example `mock-academic`. */
    val id: String

    /** Human-readable name, safe to show in diagnostics. */
    val displayName: String
}

/**
 * True for providers that return fabricated data.
 *
 * Production wiring must refuse to start when a mock provider is still
 * selected, so a development shortcut can never reach a real institution.
 */
interface MockIntegrationProvider : IntegrationProvider {
    val isMock: Boolean get() = true
}

/**
 * Raised when an external system cannot answer.
 *
 * Carries a [category] instead of the upstream message so that credentials,
 * hostnames and vendor payloads never leak into API responses or logs.
 */
class IntegrationException(
    val providerId: String,
    val category: Category,
    message: String,
    cause: Throwable? = null,
) : RuntimeException("$providerId/${category.name}: $message", cause) {

    enum class Category {
        /** The provider is not reachable (DNS, TLS, connection refused). */
        UNAVAILABLE,

        /** The provider rejected the credentials. */
        UNAUTHORIZED,

        /** The provider answered, but the request was not understood. */
        BAD_REQUEST,

        /** The provider answered with something this adapter cannot map. */
        MALFORMED_RESPONSE,

        /** The request took longer than the configured budget. */
        TIMEOUT,

        /** The requested entity does not exist upstream. */
        NOT_FOUND,
    }
}
