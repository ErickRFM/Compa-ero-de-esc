package org.companerodeescuela.api.representatives

import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.companerodeescuela.api.auth.AuthTokenService
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.plugins.requestId
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.AppointRepresentativeRequest
import org.companerodeescuela.shared.contracts.UserRole

fun Route.groupRepresentativeRoutes(
    settings: ApiSettings,
    service: GroupRepresentativeService,
) {
    route("/academic/groups/{groupId}/representatives") {
        if (!settings.hasAuthentication) return@route

        authenticate(AuthTokenService.PROVIDER_NAME) {
            get {
                val principal = call.requirePrincipal()
                val groupId = call.requireGroupId()
                val groupName = call.parameters["groupName"] ?: "Grupo $groupId"
                val overview = service.getGroupOverview(
                    actorUserId = principal.subject(),
                    actorRoles = principal.roles(),
                    groupId = groupId,
                    groupName = groupName,
                )
                call.respond(
                    ApiResponse(
                        data = overview,
                        requestId = call.requestId(),
                    ),
                )
            }

            post {
                val principal = call.requirePrincipal()
                val groupId = call.requireGroupId()
                val groupName = call.parameters["groupName"] ?: "Grupo $groupId"
                val request = call.receive<AppointRepresentativeRequest>()
                val created = service.appointRepresentative(
                    actorUserId = principal.subject(),
                    actorRoles = principal.roles(),
                    groupId = groupId,
                    groupName = groupName,
                    request = request,
                )
                call.respond(
                    ApiResponse(
                        data = created,
                        requestId = call.requestId(),
                    ),
                )
            }
        }
    }

    route("/representatives") {
        if (!settings.hasAuthentication) return@route

        authenticate(AuthTokenService.PROVIDER_NAME) {
            get("/me") {
                val principal = call.requirePrincipal()
                val assignments = service.getStudentAssignments(principal.subject())
                call.respond(
                    ApiResponse(
                        data = assignments,
                        requestId = call.requestId(),
                    ),
                )
            }

            post("/{id}/accept") {
                val principal = call.requirePrincipal()
                val id = call.requireAssignmentId()
                val accepted = service.acceptAppointment(principal.subject(), id)
                call.respond(
                    ApiResponse(
                        data = accepted,
                        requestId = call.requestId(),
                    ),
                )
            }

            post("/{id}/decline") {
                val principal = call.requirePrincipal()
                val id = call.requireAssignmentId()
                val declined = service.declineAppointment(principal.subject(), id)
                call.respond(
                    ApiResponse(
                        data = declined,
                        requestId = call.requestId(),
                    ),
                )
            }

            patch("/{id}/revoke") {
                val principal = call.requirePrincipal()
                val id = call.requireAssignmentId()
                val revoked = service.revokeAppointment(
                    actorUserId = principal.subject(),
                    actorRoles = principal.roles(),
                    assignmentId = id,
                )
                call.respond(
                    ApiResponse(
                        data = revoked,
                        requestId = call.requestId(),
                    ),
                )
            }
        }
    }
}

private fun ApplicationCall.requirePrincipal(): JWTPrincipal =
    principal<JWTPrincipal>() ?: throw ApiException.Unauthorized()

private fun ApplicationCall.requireGroupId(): String =
    parameters["groupId"]?.takeIf(String::isNotBlank)
        ?: throw ApiException.Validation("groupId is required")

private fun ApplicationCall.requireAssignmentId(): String =
    parameters["id"]?.takeIf(String::isNotBlank)
        ?: throw ApiException.Validation("id is required")

private fun JWTPrincipal.subject(): String =
    payload.subject?.takeIf(String::isNotBlank) ?: throw ApiException.Unauthorized()

private fun JWTPrincipal.roles(): Set<UserRole> =
    payload.getClaim("roles")
        .asList(String::class.java)
        .orEmpty()
        .mapNotNull { encoded -> runCatching { UserRole.valueOf(encoded.uppercase()) }.getOrNull() }
        .toSet()
