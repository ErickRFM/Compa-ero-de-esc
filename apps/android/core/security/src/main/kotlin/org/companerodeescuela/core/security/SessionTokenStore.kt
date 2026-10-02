package org.companerodeescuela.core.security

/**
 * Platform session storage.
 *
 * Only our short-lived API token belongs here. Institutional credentials are
 * never persisted on the device.
 */
interface SessionTokenStore {
    suspend fun readAccessToken(): String?
    suspend fun writeAccessToken(token: String)
    suspend fun clear()
}
