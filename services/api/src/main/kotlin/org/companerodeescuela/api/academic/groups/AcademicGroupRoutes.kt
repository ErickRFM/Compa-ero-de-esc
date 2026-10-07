package org.companerodeescuela.api.academic.groups

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.companerodeescuela.api.auth.AuthTokenService
import org.companerodeescuela.api.auth.requireActor
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.plugins.requestId
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.AssignAcademicGroupMemberRequest
import org.companerodeescuela.shared.contracts.CreateAcademicGroupRequest

fun Route.academicGroupRoutes(
    settings: ApiSettings,
    service: AcademicGroupService,
) {
    route("/academic/groups") {
        if (!settings.hasAuthentication) {
            get {
                throw ApiException.DependencyUnavailable("Authentication is not configured")
            }
            return@route
        }

        val tokenService = AuthTokenService(settings)
        authenticate(AuthTokenService.PROVIDER_NAME) {
            get {
                val actor = call.requireActor(tokenService)
                call.respond(
                    ApiResponse(
                        data = service.listFor(actor),
                        requestId = call.requestId(),
                    ),
                )
            }

            post {
                val actor = call.requireActor(tokenService)
                val request = runCatching { call.receive<CreateAcademicGroupRequest>() }
                    .getOrElse { throw ApiException.Validation("Invalid academic group request") }
                call.respond(
                    status = HttpStatusCode.Created,
                    message = ApiResponse(
                        data = service.create(actor, request),
                        requestId = call.requestId(),
                    ),
                )
            }

            post("/{groupId}/members") {
                val actor = call.requireActor(tokenService)
                val groupId = call.parameters["groupId"]
                    ?.takeIf(String::isNotBlank)
                    ?: throw ApiException.Validation("groupId is required")
                val request = runCatching { call.receive<AssignAcademicGroupMemberRequest>() }
                    .getOrElse { throw ApiException.Validation("Invalid group membership request") }
                call.respond(
                    status = HttpStatusCode.Created,
                    message = ApiResponse(
                        data = service.assignMember(actor, groupId, request),
                        requestId = call.requestId(),
                    ),
                )
            }
        }
    }
}
