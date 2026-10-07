package org.companerodeescuela.api.events

import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.companerodeescuela.api.auth.AuthTokenService
import org.companerodeescuela.api.auth.platformRoles
import org.companerodeescuela.api.auth.requirePlatformPrincipal
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.plugins.requestId
import org.companerodeescuela.shared.contracts.AcademicEvent
import org.companerodeescuela.shared.contracts.ApiResponse

fun Route.academicEventRoutes(
    settings: ApiSettings,
    service: AcademicEventService,
) {
    route("/events") {
        if (!settings.hasAuthentication) {
            get {
                call.respond(
                    ApiResponse(
                        data = service.listEvents(),
                        requestId = call.requestId(),
                    ),
                )
            }
            return@route
        }

        authenticate(AuthTokenService.PROVIDER_NAME) {
            get {
                val principal = call.requirePlatformPrincipal()
                call.respond(
                    ApiResponse(
                        data = service.listEvents(),
                        requestId = call.requestId(),
                    ),
                )
            }

            get("/{id}") {
                val principal = call.requirePlatformPrincipal()
                val id = call.requireEventId()
                val event = service.getEvent(id) ?: throw ApiException.NotFound("Event $id not found")
                call.respond(
                    ApiResponse(
                        data = event,
                        requestId = call.requestId(),
                    ),
                )
            }

            post {
                val principal = call.requirePlatformPrincipal()
                val roles = principal.platformRoles()
                if (roles.none { it.isStaff }) {
                    throw ApiException.Forbidden("Only staff can post events")
                }

                val request = call.receive<AcademicEvent>()
                val created = service.createEvent(request)
                call.respond(
                    ApiResponse(
                        data = created,
                        requestId = call.requestId(),
                    ),
                )
            }

            patch("/{id}") {
                val principal = call.requirePlatformPrincipal()
                val id = call.requireEventId()
                val roles = principal.platformRoles()
                if (roles.none { it.isStaff }) {
                    throw ApiException.Forbidden("Only staff can edit events")
                }

                val request = call.receive<AcademicEvent>()
                val updated = service.updateEvent(id, request) ?: throw ApiException.NotFound("Event $id not found")
                call.respond(
                    ApiResponse(
                        data = updated,
                        requestId = call.requestId(),
                    ),
                )
            }

            delete("/{id}") {
                val principal = call.requirePlatformPrincipal()
                val id = call.requireEventId()
                val roles = principal.platformRoles()
                if (roles.none { it.isStaff }) {
                    throw ApiException.Forbidden("Only staff can delete events")
                }

                val deleted = service.deleteEvent(id)
                if (!deleted) throw ApiException.NotFound("Event $id not found")
                call.respond(
                    ApiResponse(
                        data = true,
                        requestId = call.requestId(),
                    ),
                )
            }
        }
    }
}

private fun ApplicationCall.requireEventId(): String =
    parameters["id"]?.takeIf(String::isNotBlank)
        ?: throw ApiException.Validation("id is required")
