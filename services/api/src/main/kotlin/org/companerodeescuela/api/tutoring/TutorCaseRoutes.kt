package org.companerodeescuela.api.tutoring

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
import org.companerodeescuela.shared.contracts.AddTutorCaseNoteRequest
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.CreateTutorCaseRequest

fun Route.tutorCaseRoutes(settings: ApiSettings, service: TutorCaseService) {
    route("/tutoring/cases") {
        if (!settings.hasAuthentication) {
            get { throw ApiException.DependencyUnavailable("Authentication is not configured") }
            return@route
        }
        val tokens = AuthTokenService(settings)
        authenticate(AuthTokenService.PROVIDER_NAME) {
            get {
                val actor = call.requireActor(tokens)
                call.respond(ApiResponse(data = service.list(actor), requestId = call.requestId()))
            }
            get("/my-published-notes") {
                val actor = call.requireActor(tokens)
                call.respond(ApiResponse(data = service.myPublishedNotes(actor), requestId = call.requestId()))
            }
            post {
                val actor = call.requireActor(tokens)
                val request = call.receive<CreateTutorCaseRequest>()
                call.respond(HttpStatusCode.Created,
                    ApiResponse(data = service.create(actor, request), requestId = call.requestId()))
            }
            get("/{caseId}") {
                val actor = call.requireActor(tokens)
                val caseId = call.parameters["caseId"] ?: throw ApiException.Validation("caseId is required")
                call.respond(ApiResponse(data = service.detail(actor, caseId), requestId = call.requestId()))
            }
            post("/{caseId}/notes") {
                val actor = call.requireActor(tokens)
                val caseId = call.parameters["caseId"] ?: throw ApiException.Validation("caseId is required")
                val request = call.receive<AddTutorCaseNoteRequest>()
                call.respond(HttpStatusCode.Created,
                    ApiResponse(data = service.addNote(actor, caseId, request), requestId = call.requestId()))
            }
        }
    }
}
