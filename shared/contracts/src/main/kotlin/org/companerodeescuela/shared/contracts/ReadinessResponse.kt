package org.companerodeescuela.shared.contracts

import java.time.Instant
import kotlinx.serialization.Serializable

/**
 * Response body of `GET /ready`.
 *
 * Readiness answers "can this instance serve traffic right now?", which is a
 * stricter question than liveness. It is used by orchestrators and local
 * scripts, and may include dependency details that `/health` also exposes.
 */
@Serializable
data class ReadinessResponse(
    val status: ServiceStatus,
    @Serializable(with = InstantAsIso8601Serializer::class)
    val timestamp: Instant,
    val checks: List<DependencyStatus> = emptyList(),
)

/**
 * Response body of `GET /version`.
 */
@Serializable
data class VersionResponse(
    val service: String,
    val version: String,
    val environment: String,
    val apiVersion: String,
)
