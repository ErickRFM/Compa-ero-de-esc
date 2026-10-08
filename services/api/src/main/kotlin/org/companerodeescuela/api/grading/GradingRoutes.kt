package org.companerodeescuela.api.grading

import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.companerodeescuela.api.auth.AuthTokenService
import org.companerodeescuela.api.auth.requirePlatformPrincipal
import org.companerodeescuela.api.auth.requireRole
import org.companerodeescuela.api.auth.subjectId
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.plugins.requestId
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.GradeSyncRequest
import org.companerodeescuela.shared.contracts.UserRole

fun Route.gradingRoutes(
    settings: ApiSettings,
    syncGateway: GradeSyncGateway,
) {
    route("/grading") {
        if (!settings.hasAuthentication) {
            post("/sync") {
                throw ApiException.DependencyUnavailable("Authentication is not configured")
            }
            return@route
        }

        authenticate(AuthTokenService.PROVIDER_NAME) {
            post("/sync") {
                val principal = call.requirePlatformPrincipal()
                principal.requireRole(UserRole.TEACHER)
                val request = call.receive<GradeSyncRequest>()
                if (request.classroomId.isBlank()) throw ApiException.Validation("classroomId is required")
                if (request.gradingPeriod.isBlank()) throw ApiException.Validation("gradingPeriod is required")
                if (request.rows.isEmpty()) throw ApiException.Validation("At least one grade is required")
                if (request.rows.any { it.studentId.isBlank() || it.finalGrade !in 0.0..10.0 }) {
                    throw ApiException.Validation("Each row requires a student id and a grade between 0 and 10")
                }
                if (request.rows.map { it.studentId }.distinct().size != request.rows.size) {
                    throw ApiException.Validation("Duplicate student ids are not allowed")
                }

                call.respond(
                    ApiResponse(
                        data = syncGateway.sync(principal.subjectId(), request),
                        requestId = call.requestId(),
                    ),
                )
            }
        }
    }
}
