package org.companerodeescuela.core.attendance

import kotlin.math.min

object AttendanceRetryPolicy {
    private const val BASE_DELAY_SECONDS = 15L
    private const val MAX_DELAY_SECONDS = 15L * 60L

    fun nextDelaySeconds(attemptCount: Int): Long {
        val exponent = attemptCount.coerceIn(0, 10)
        val delay = BASE_DELAY_SECONDS * (1L shl exponent)
        return min(delay, MAX_DELAY_SECONDS)
    }

    fun isRetryableHttp(status: Int): Boolean =
        status == 408 || status == 425 || status == 429 || status in 500..599
}
