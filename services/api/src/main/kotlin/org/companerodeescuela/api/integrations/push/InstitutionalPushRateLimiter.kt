package org.companerodeescuela.api.integrations.push

interface InstitutionalPushRateLimiter {
    /** Returns retry-after seconds when the address has exhausted its current window. */
    suspend fun acquire(clientAddress: String): Long?
}
