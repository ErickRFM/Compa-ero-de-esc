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
 * Access tokens are deliberately short lived. Refresh-token persistence and
 * revocation are added with the database-backed session lifecycle; this first
 * slice does not pretend logout/revocation exists before there is a store for it.
 */
@Serializable
data class LoginResponse(
    val accessToken: String,
    val expiresAtEpochSeconds: Long,
    val user: UserSummary,
)
