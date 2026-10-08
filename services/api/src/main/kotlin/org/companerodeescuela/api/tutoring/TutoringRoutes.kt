package org.companerodeescuela.api.tutoring

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.patch
import io.ktor.server.routing.route
import org.companerodeescuela.api.auth.AuthTokenService
import org.companerodeescuela.api.auth.requireActor
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.plugins.requestId
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.CreateTutorAssignmentRequest

fun Route.tutoringRoutes(
    settings: ApiSettings,
    service: TutorAssignmentService,
) {
    route("/tutoring") {
        if (!settings.hasAuthentication) {
            get {
                throw ApiException.DependencyUnavailable("Authentication is not configured")
            }
            return@route
        }

        val tokenService = AuthTokenService(settings)
        authenticate(AuthTokenService.PROVIDER_NAME) {
            get("/assignments") {
                val actor = call.requireActor(tokenService)
                call.respond(ApiResponse(data = service.listFor(actor), requestId = call.requestId()))
            }

            get("/me") {
                val actor = call.requireActor(tokenService)
                call.respond(ApiResponse(data = service.scopeFor(actor), requestId = call.requestId()))
            }

            patch("/assignments/{assignmentId}/revoke") {
                val actor = call.requireActor(tokenService)
                val assignmentId = call.parameters["assignmentId"]
                    ?: throw ApiException.Validation("assignmentId is required")
                call.respond(ApiResponse(data = service.revoke(actor, assignmentId), requestId = call.requestId()))
            }

            post("/assignments") {
                val actor = call.requireActor(tokenService)
                val request = runCatching { call.receive<CreateTutorAssignmentRequest>() }
                    .getOrElse { throw ApiException.Validation("Invalid tutor assignment request") }
                call.respond(
                    status = HttpStatusCode.Created,
                    message = ApiResponse(
                        data = service.create(actor, request),
                        requestId = call.requestId(),
                    ),
                )
            }
        }
    }
}
