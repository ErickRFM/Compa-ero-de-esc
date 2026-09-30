package org.companerodeescuela.shared.contracts

import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Overall service state reported by `GET /health`.
 *
 * This endpoint is public and unauthenticated, so it must never contain
 * configuration, credentials, hostnames or connection strings.
 */
@Serializable
enum class ServiceStatus {
    @SerialName("up")
    UP,

    @SerialName("degraded")
    DEGRADED,

    @SerialName("down")
    DOWN,
}

/**
 * State of a single dependency, as observed during a health check.
 */
@Serializable
data class DependencyStatus(
    val name: String,
    val status: ServiceStatus,
    /**
     * Short, non-sensitive explanation. Must never contain URIs, credentials or
     * driver internals; keep it to a category such as `timeout` or `unavailable`.
     */
    val detail: String? = null,
)

/**
 * Response body of `GET /health`.
 *
 * Design constraints (enforced by review, see `docs/architecture/API_ARCHITECTURE.md`):
 * - no secrets, no DSNs, no internal hostnames;
 * - cheap to compute, no full collection scans;
 * - safe to expose to unauthenticated callers.
 */
@Serializable
data class HealthResponse(
    val service: String,
    val status: ServiceStatus,
    val version: String,
    val environment: String,
    @Serializable(with = InstantAsIso8601Serializer::class)
    val timestamp: Instant,
    val dependencies: List<DependencyStatus> = emptyList(),
)
