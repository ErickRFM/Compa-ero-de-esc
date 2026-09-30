package org.companerodeescuela.shared.contracts

import kotlinx.serialization.Serializable

/**
 * Envelope for successful responses that carry a payload.
 *
 * Kept deliberately thin: it exists so clients can evolve response metadata
 * (pagination, warnings) without breaking every endpoint.
 */
@Serializable
data class ApiResponse<T>(
    val data: T,
    val requestId: String? = null,
)
