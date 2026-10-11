package org.companerodeescuela.mobile

import kotlinx.serialization.Serializable

// Explicit wire adapters. The original operational DTOs depend on java.time.Instant.
@Serializable data class DependencyPayload(val name: String, val status: String, val detail: String? = null)
@Serializable data class HealthPayload(
    val service: String, val status: String, val version: String, val environment: String,
    val timestamp: String, val dependencies: List<DependencyPayload> = emptyList(),
)
@Serializable data class ReadinessPayload(
    val status: String, val timestamp: String, val checks: List<DependencyPayload> = emptyList(),
)
