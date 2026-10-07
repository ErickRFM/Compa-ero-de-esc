package org.companerodeescuela.api.excuses

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.companerodeescuela.api.auth.AuthTokenService
import org.companerodeescuela.api.auth.requireActor
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.plugins.requestId
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.ReviewExcuseRequest
import org.companerodeescuela.shared.contracts.SubmitExcuseRequest

fun Route.excuseRoutes(
    settings: ApiSettings,
    service: ExcuseService,
) {
    route("/excuses") {
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
                call.respond(ApiResponse(data = service.listFor(actor), requestId = call.requestId()))
            }

            post {
                val actor = call.requireActor(tokenService)
                val request = runCatching { call.receive<SubmitExcuseRequest>() }
                    .getOrElse { throw ApiException.Validation("Invalid excuse request") }
                call.respond(
                    status = HttpStatusCode.Created,
                    message = ApiResponse(
                        data = service.submit(actor, request),
                        requestId = call.requestId(),
                    ),
                )
            }

            patch("/{excuseId}/review") {
                val actor = call.requireActor(tokenService)
                val excuseId = call.parameters["excuseId"]
                    ?.takeIf(String::isNotBlank)
                    ?: throw ApiException.Validation("excuseId is required")
                val request = runCatching { call.receive<ReviewExcuseRequest>() }
                    .getOrElse { throw ApiException.Validation("Invalid excuse review request") }
                call.respond(
                    ApiResponse(
                        data = service.review(actor, excuseId, request),
                        requestId = call.requestId(),
                    ),
                )
            }
        }
    }
}
