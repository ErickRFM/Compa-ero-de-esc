package org.companerodeescuela.api.grading

import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.GradeSyncRequest
import org.companerodeescuela.shared.contracts.GradeSyncResult

/**
 * Boundary for the school's official grade API.
 *
 * A concrete adapter must validate classroom/course mappings and translate
 * institutional error codes. Keeping this boundary outside Android prevents
 * school credentials from ever shipping in the APK.
 */
fun interface GradeSyncGateway {
    suspend fun sync(teacherId: String, request: GradeSyncRequest): GradeSyncResult
}

object UnavailableGradeSyncGateway : GradeSyncGateway {
    override suspend fun sync(teacherId: String, request: GradeSyncRequest): GradeSyncResult {
        throw ApiException.DependencyUnavailable(
            "The institutional grade API is not configured",
        )
    }
}
