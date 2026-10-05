package org.companerodeescuela.shared.contracts

import kotlinx.serialization.Serializable

/**
 * Credentials sent only to our API.
 *
 * The Android app never receives or stores credentials for the institution's
 * upstream systems. The API delegates them to the configured IdentityProvider
 * and returns only a platform session token.
 */
@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

/**
 * Successful platform login.
 *
 * Access tokens are deliberately short lived. The opaque refresh token is
 * stored only in encrypted Android storage; the API stores only its hash.
 */
@Serializable
data class LoginResponse(
    val accessToken: String,
    val expiresAtEpochSeconds: Long,
    val sessionId: String,
    val refreshToken: String,
    val user: UserSummary,
)

@Serializable
data class RefreshSessionRequest(
    val sessionId: String,
    val refreshToken: String,
)
