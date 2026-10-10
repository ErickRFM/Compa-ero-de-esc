package org.companerodeescuela.api.presence

import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.response.header
import io.ktor.http.HttpHeaders
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.companerodeescuela.api.auth.AuthTokenService
import org.companerodeescuela.api.auth.requireNativeActor
import org.companerodeescuela.api.auth.boundedAuthRequest
import io.ktor.server.application.ApplicationCall
import kotlinx.serialization.DeserializationStrategy
import kotlinx.coroutines.CancellationException
import org.companerodeescuela.shared.contracts.AcademicOperationRequest
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
    val tokens=if(settings.hasAuthentication) AuthTokenService(settings) else null
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
                    call.response.header(HttpHeaders.CacheControl,"no-store")
                    val actor=call.requireNativeActor(tokens!!)
                    call.respond(
                        ApiResponse(
                            data = qrAdminService.listScoped(actor),
                            requestId = call.requestId(),
                        ),
                    )
                }

                post {
                    call.response.header(HttpHeaders.CacheControl,"no-store")
                    val actor=call.requireNativeActor(tokens!!)
                    call.respond(
                        ApiResponse(
                            data = qrAdminService.createScoped(
                                actor=actor,
                                request = call.presenceRequest(CreateSchoolEntryQrRequest.serializer()),
                            ),
                            requestId = call.requestId(),
                        ),
                    )
                }

                post("/{qrId}/revoke") {
                    call.response.header(HttpHeaders.CacheControl,"no-store")
                    val actor=call.requireNativeActor(tokens!!)
                    val qrId = call.parameters["qrId"]
                        ?: throw ApiException.Validation("qrId is required")
                    call.respond(
                        ApiResponse(
                            data = qrAdminService.revokeScoped(actor,qrId,call.presenceRequest(AcademicOperationRequest.serializer())),
                            requestId = call.requestId(),
                        ),
                    )
                }

                post("/{qrId}/regenerate") {
                    call.response.header(HttpHeaders.CacheControl,"no-store")
                    val actor=call.requireNativeActor(tokens!!)
                    val qrId = call.parameters["qrId"]
                        ?: throw ApiException.Validation("qrId is required")
                    call.respond(
                        ApiResponse(
                            data = qrAdminService.regenerateScoped(actor,qrId,call.presenceRequest(AcademicOperationRequest.serializer())),
                            requestId = call.requestId(),
                        ),
                    )
                }
            }

            get("/school-day") {
                val principal = call.requirePlatformPrincipal()
                principal.requireRole(UserRole.STUDENT)
                val actor=call.requireNativeActor(tokens!!)
                call.respond(
                    ApiResponse(
                        data = service.activeFor(principal.subjectId(),actor),
                        requestId = call.requestId(),
                    ),
                )
            }

            post("/school-day/start") {
                val principal = call.requirePlatformPrincipal()
                principal.requireRole(UserRole.STUDENT)
                val actor=call.requireNativeActor(tokens!!)
                call.respond(
                    ApiResponse(
                        data = service.start(
                            studentId = principal.subjectId(),
                            request = call.presenceRequest(StartSchoolPresenceRequest.serializer()),
                            nativeActor=actor,
                        ),
                        requestId = call.requestId(),
                    ),
                )
            }

            post("/school-day/close") {
                val principal = call.requirePlatformPrincipal()
                principal.requireRole(UserRole.STUDENT)
                val actor=call.requireNativeActor(tokens!!)
                call.respond(
                    ApiResponse(
                        data = service.close(principal.subjectId(),actor),
                        requestId = call.requestId(),
                    ),
                )
            }
        }
    }
}

private suspend fun <T> ApplicationCall.presenceRequest(serializer:DeserializationStrategy<T>):T = try {
    boundedAuthRequest(serializer)
} catch(cancelled:CancellationException) { throw cancelled }
catch(_:Exception) { throw ApiException.Validation("Invalid school presence request") }
