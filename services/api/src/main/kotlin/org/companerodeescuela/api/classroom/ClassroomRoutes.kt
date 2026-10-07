package org.companerodeescuela.api.classroom

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.companerodeescuela.api.auth.AuthTokenService
import org.companerodeescuela.api.auth.requireActor
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.plugins.requestId
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.CreateClassInviteRequest
import org.companerodeescuela.shared.contracts.CreateClassroomRequest
import org.companerodeescuela.shared.contracts.JoinClassInviteRequest

fun Route.classroomRoutes(
    settings: ApiSettings,
    service: ClassroomService,
) {
    route("/classrooms") {
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
                val request = runCatching { call.receive<CreateClassroomRequest>() }
                    .getOrElse { throw ApiException.Validation("Invalid classroom request") }
                call.respond(
                    status = HttpStatusCode.Created,
                    message = ApiResponse(
                        data = service.create(actor, request),
                        requestId = call.requestId(),
                    ),
                )
            }

            post("/join") {
                val actor = call.requireActor(tokenService)
                val request = runCatching { call.receive<JoinClassInviteRequest>() }
                    .getOrElse { throw ApiException.Validation("Invalid invite request") }
                call.respond(
                    ApiResponse(
                        data = service.join(actor, request.code),
                        requestId = call.requestId(),
                    ),
                )
            }

            post("/{classroomId}/invites") {
                val actor = call.requireActor(tokenService)
                val classroomId = call.requireClassroomId()
                val request = runCatching { call.receive<CreateClassInviteRequest>() }
                    .getOrElse { throw ApiException.Validation("Invalid invite configuration") }
                call.respond(
                    status = HttpStatusCode.Created,
                    message = ApiResponse(
                        data = service.createInvite(actor, classroomId, request),
                        requestId = call.requestId(),
                    ),
                )
            }

            delete("/{classroomId}/invites/{inviteId}") {
                val actor = call.requireActor(tokenService)
                service.revokeInvite(
                    actor = actor,
                    classroomId = call.requireClassroomId(),
                    inviteId = call.parameters["inviteId"]
                        ?.takeIf(String::isNotBlank)
                        ?: throw ApiException.Validation("inviteId is required"),
                )
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}


private fun io.ktor.server.application.ApplicationCall.requireClassroomId(): String =
    parameters["classroomId"]?.takeIf(String::isNotBlank)
        ?: throw ApiException.Validation("classroomId is required")
