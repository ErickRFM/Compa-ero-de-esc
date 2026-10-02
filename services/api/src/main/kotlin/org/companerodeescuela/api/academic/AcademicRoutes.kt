package org.companerodeescuela.api.academic

import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.companerodeescuela.api.auth.AuthTokenService
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.academic.AcademicProvider
import org.companerodeescuela.api.plugins.requestId
import org.companerodeescuela.shared.contracts.ApiResponse

fun Route.academicRoutes(
    settings: ApiSettings,
    academicProvider: AcademicProvider,
) {
    route("/academic") {
        if (!settings.hasAuthentication) {
            get("/load") {
                throw ApiException.DependencyUnavailable("Authentication is not configured")
            }
            get("/schedule") {
                throw ApiException.DependencyUnavailable("Authentication is not configured")
            }
        } else {
            authenticate(AuthTokenService.PROVIDER_NAME) {
                get("/load") {
                    val externalId = call.requireSubject()
                    call.respond(
                        ApiResponse(
                            data = AcademicService(academicProvider).loadFor(externalId),
                            requestId = call.requestId(),
                        ),
                    )
                }
                get("/schedule") {
                    val externalId = call.requireSubject()
                    call.respond(
                        ApiResponse(
                            data = AcademicService(academicProvider).scheduleFor(externalId),
                            requestId = call.requestId(),
                        ),
                    )
                }
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.requireSubject(): String =
    principal<JWTPrincipal>()
        ?.payload
        ?.subject
        ?.takeIf { it.isNotBlank() }
        ?: throw ApiException.Unauthorized()
