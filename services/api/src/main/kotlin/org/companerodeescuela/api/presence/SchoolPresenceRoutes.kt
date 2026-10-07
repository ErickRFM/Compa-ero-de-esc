package org.companerodeescuela.api.presence

import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.companerodeescuela.api.auth.AuthTokenService
import org.companerodeescuela.api.auth.requireAdministrative
import org.companerodeescuela.api.auth.requirePlatformPrincipal
import org.companerodeescuela.api.auth.requireRole
import org.companerodeescuela.api.auth.subjectId
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
                    call.requirePlatformPrincipal().requireAdministrative()
                    call.respond(
                        ApiResponse(
                            data = qrAdminService.list(),
                            requestId = call.requestId(),
                        ),
                    )
                }

                post {
                    val principal = call.requirePlatformPrincipal()
                    principal.requireAdministrative()
                    call.respond(
                        ApiResponse(
                            data = qrAdminService.create(
                                actorId = principal.subjectId(),
                                request = call.receive<CreateSchoolEntryQrRequest>(),
                            ),
                            requestId = call.requestId(),
                        ),
                    )
                }

                post("/{qrId}/revoke") {
                    call.requirePlatformPrincipal().requireAdministrative()
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
                    val principal = call.requirePlatformPrincipal()
                    principal.requireAdministrative()
                    val qrId = call.parameters["qrId"]
                        ?: throw ApiException.Validation("qrId is required")
                    call.respond(
                        ApiResponse(
                            data = qrAdminService.regenerate(principal.subjectId(), qrId),
                            requestId = call.requestId(),
                        ),
                    )
                }
            }

            get("/school-day") {
                val principal = call.requirePlatformPrincipal()
                principal.requireRole(UserRole.STUDENT)
                call.respond(
                    ApiResponse(
                        data = service.activeFor(principal.subjectId()),
                        requestId = call.requestId(),
                    ),
                )
            }

            post("/school-day/start") {
                val principal = call.requirePlatformPrincipal()
                principal.requireRole(UserRole.STUDENT)
                call.respond(
                    ApiResponse(
                        data = service.start(
                            studentId = principal.subjectId(),
                            request = call.receive<StartSchoolPresenceRequest>(),
                        ),
                        requestId = call.requestId(),
                    ),
                )
            }

            post("/school-day/close") {
                val principal = call.requirePlatformPrincipal()
                principal.requireRole(UserRole.STUDENT)
                call.respond(
                    ApiResponse(
                        data = service.close(principal.subjectId()),
                        requestId = call.requestId(),
                    ),
                )
            }
        }
    }
}
