package org.companerodeescuela.core.security

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Platform session storage.
 *
 * Only our short-lived API token belongs here. Institutional credentials are
 * never persisted on the device.
 *
 * The observable form is important: any layer that invalidates the token
 * (for example after a 401) immediately drives the app back to authentication.
 */
interface SessionTokenStore {
    suspend fun readAccessToken(): String?
    suspend fun writeAccessToken(token: String)
    suspend fun clear()

    /**
     * Observe session changes. Simple test doubles can keep the default
     * one-shot implementation; the real DataStore-backed store overrides it.
     */
    fun observeAccessToken(): Flow<String?> = flow {
        emit(readAccessToken())
    }
}
