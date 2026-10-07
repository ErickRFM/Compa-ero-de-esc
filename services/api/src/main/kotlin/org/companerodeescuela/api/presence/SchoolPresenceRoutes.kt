package org.companerodeescuela.api.presence

import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.companerodeescuela.api.auth.AuthTokenService
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.plugins.requestId
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.CreateSchoolEntryQrRequest
import org.companerodeescuela.shared.contracts.StartSchoolPresenceRequest
import org.companerodeescuela.shared.contracts.UserRole

fun Route.schoolPresenceRoutes(
    settings: ApiSettings,
    service: SchoolPresenceService,
    qrAdminService: SchoolEntryQrService,
) {
    route("/presence") {
        if (!settings.hasAuthentication) {
            get("/school-day") {
                throw ApiException.DependencyUnavailable("Authentication is not configured")
            }
            return@route
        }

        authenticate(AuthTokenService.PROVIDER_NAME) {
            route("/admin/qrs") {
                get {
                    call.requireAdminPrincipal()
                    call.respond(
                        ApiResponse(
                            data = qrAdminService.list(),
                            requestId = call.requestId(),
                        ),
                    )
                }

                post {
                    val principal = call.requireAdminPrincipal()
                    call.respond(
                        ApiResponse(
                            data = qrAdminService.create(
                                actorId = principal.subject(),
                                request = call.receive<CreateSchoolEntryQrRequest>(),
                            ),
                            requestId = call.requestId(),
                        ),
                    )
                }

                post("/{qrId}/revoke") {
                    call.requireAdminPrincipal()
                    val qrId = call.parameters["qrId"]
                        ?: throw ApiException.Validation("qrId is required")
                    call.respond(
                        ApiResponse(
                            data = qrAdminService.revoke(qrId),
                            requestId = call.requestId(),
                        ),
                    )
                }

                post("/{qrId}/regenerate") {
                    val principal = call.requireAdminPrincipal()
                    val qrId = call.parameters["qrId"]
                        ?: throw ApiException.Validation("qrId is required")
                    call.respond(
                        ApiResponse(
                            data = qrAdminService.regenerate(principal.subject(), qrId),
                            requestId = call.requestId(),
                        ),
                    )
                }
            }

            get("/school-day") {
                val principal = call.requireStudentPrincipal()
                call.respond(
                    ApiResponse(
                        data = service.activeFor(principal.subject()),
                        requestId = call.requestId(),
                    ),
                )
            }

            post("/school-day/start") {
                val principal = call.requireStudentPrincipal()
                call.respond(
                    ApiResponse(
                        data = service.start(
                            studentId = principal.subject(),
                            request = call.receive<StartSchoolPresenceRequest>(),
                        ),
                        requestId = call.requestId(),
                    ),
                )
            }

            post("/school-day/close") {
                val principal = call.requireStudentPrincipal()
                call.respond(
                    ApiResponse(
                        data = service.close(principal.subject()),
                        requestId = call.requestId(),
                    ),
                )
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.requireStudentPrincipal(): JWTPrincipal {
    val principal = principal<JWTPrincipal>() ?: throw ApiException.Unauthorized()
    val roles = principal.payload.getClaim("roles")
        .asList(String::class.java)
        .orEmpty()
        .mapNotNull { encoded -> runCatching { UserRole.valueOf(encoded) }.getOrNull() }
        .toSet()
    if (UserRole.STUDENT !in roles) {
        throw ApiException.Forbidden("Student role is required")
    }
    return principal
}

private fun JWTPrincipal.subject(): String =
    payload.subject?.takeIf { it.isNotBlank() } ?: throw ApiException.Unauthorized()

private fun io.ktor.server.application.ApplicationCall.requireAdminPrincipal(): JWTPrincipal {
    val principal = principal<JWTPrincipal>() ?: throw ApiException.Unauthorized()
    val roles = principal.payload.getClaim("roles")
        .asList(String::class.java)
        .orEmpty()
        .mapNotNull { encoded -> runCatching { UserRole.valueOf(encoded) }.getOrNull() }
        .toSet()
    if (roles.none(UserRole::isAdministrative)) {
        throw ApiException.Forbidden("Administrative role is required")
    }
    return principal
}
