package org.companerodeescuela.api.academic

import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.companerodeescuela.api.academic.groups.AcademicGroupRepository
import org.companerodeescuela.api.auth.AuthTokenService
import org.companerodeescuela.api.auth.SessionIdentitySource
import org.companerodeescuela.api.auth.boundedAuthRequest
import org.companerodeescuela.api.auth.requireNativeActor
import org.companerodeescuela.api.auth.requirePlatformPrincipal
import org.companerodeescuela.api.auth.subjectId
import org.companerodeescuela.api.auth.verifiedSessionSource
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.academic.AcademicProvider
import org.companerodeescuela.api.plugins.requestId
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.UpsertScheduleBlockRequest
import java.time.LocalDate

fun Route.academicRoutes(
    settings: ApiSettings,
    academicProvider: AcademicProvider,
    scheduleManagement: AcademicScheduleManagementService? = null,
    scheduleOverrides: AcademicScheduleOverrideRepository? = null,
    groupRepository: AcademicGroupRepository? = null,
    nativeProjection: NativeAcademicProjection? = null,
    institutionalAcademic: org.companerodeescuela.api.integrations.academic.siii.SiiiAcademicService? = null,
) {
    val tokens = if (settings.hasAuthentication) AuthTokenService(settings) else null
    val service = AcademicService(academicProvider, scheduleOverrides, groupRepository, nativeProjection, institutionalAcademic)

    suspend fun actor(call: ApplicationCall) =
        if (call.verifiedSessionSource() ==
            SessionIdentitySource.NATIVE
        ) {
            call.requireNativeActor(tokens!!)
        } else {
            null
        }
    route("/academic") {
        if (!settings.hasAuthentication) {
            get("/load") {
                throw ApiException.DependencyUnavailable("Authentication is not configured")
            }
            get("/schedule") {
                throw ApiException.DependencyUnavailable("Authentication is not configured")
            }
            get("/schedule/v2") {
                throw ApiException.DependencyUnavailable("Authentication is not configured")
            }
        } else {
            authenticate(AuthTokenService.PROVIDER_NAME) {
                get("/institutional/sections") {
                    val current = call.requireNativeActor(tokens!!)
                    val institutional =
                        institutionalAcademic
                            ?: throw ApiException.DependencyUnavailable("Institutional academic delivery is not configured")
                    call.response.headers.append("Cache-Control", "no-store")
                    call.respond(ApiResponse(data = institutional.load(current).institutionalSections, requestId = call.requestId()))
                }
                get("/institutional/sections/{sectionId}") {
                    val current = call.requireNativeActor(tokens!!)
                    val institutional =
                        institutionalAcademic
                            ?: throw ApiException.DependencyUnavailable("Institutional academic delivery is not configured")
                    val sectionId = call.parameters["sectionId"] ?: throw ApiException.Validation("sectionId is required")
                    val section =
                        institutional.load(current).institutionalSections.singleOrNull { it.id == sectionId }
                            ?: throw ApiException.NotFound("Institutional section was not found")
                    call.response.headers.append("Cache-Control", "no-store")
                    call.respond(ApiResponse(data = section, requestId = call.requestId()))
                }
                get("/load") {
                    val externalId = call.requirePlatformPrincipal().subjectId()
                    call.respond(
                        ApiResponse(
                            data =
                                actor(call).let { current ->
                                    if (current ==
                                        null
                                    ) {
                                        service.loadInstitutional(externalId)
                                    } else {
                                        service.loadFor(externalId, current)
                                    }
                                },
                            requestId = call.requestId(),
                        ),
                    )
                }
                get("/schedule") {
                    val externalId = call.requirePlatformPrincipal().subjectId()
                    call.respond(
                        ApiResponse(
                            data =
                                actor(call).let { current ->
                                    if (current ==
                                        null
                                    ) {
                                        service.loadInstitutional(externalId).schedule
                                    } else {
                                        service.scheduleFor(externalId, current)
                                    }
                                },
                            requestId = call.requestId(),
                        ),
                    )
                }
                get("/schedule/v2") {
                    val externalId = call.requirePlatformPrincipal().subjectId()
                    val weekOf =
                        call.request.queryParameters["weekOf"]
                            ?.let { raw ->
                                runCatching { LocalDate.parse(raw) }
                                    .getOrElse { throw ApiException.Validation("weekOf must be YYYY-MM-DD") }
                            }
                            ?: throw ApiException.Validation("weekOf is required")

                    call.respond(
                        ApiResponse(
                            data =
                                service.scheduleWeekFor(
                                    externalId = externalId,
                                    weekOf = weekOf,
                                    nativeActor = actor(call),
                                ),
                            requestId = call.requestId(),
                        ),
                    )
                }

                route("/manual-schedule") {
                    get("/{ownerId}") {
                        val principal = call.requirePlatformPrincipal()
                        val actor = call.requireNativeActor(AuthTokenService(settings))
                        val ownerId =
                            call.parameters["ownerId"]
                                ?.takeIf(String::isNotBlank)
                                ?: throw ApiException.Validation("ownerId is required")
                        val service =
                            scheduleManagement
                                ?: throw ApiException.DependencyUnavailable(
                                    "Manual academic schedule management is not configured",
                                )
                        call.respond(
                            ApiResponse(
                                data = service.listScoped(actor, ownerId),
                                requestId = call.requestId(),
                            ),
                        )
                    }

                    post {
                        val principal = call.requirePlatformPrincipal()
                        val actor = call.requireNativeActor(AuthTokenService(settings))
                        val service =
                            scheduleManagement
                                ?: throw ApiException.DependencyUnavailable(
                                    "Manual academic schedule management is not configured",
                                )
                        call.respond(
                            ApiResponse(
                                data = service.upsertScoped(actor, call.boundedAuthRequest(UpsertScheduleBlockRequest.serializer())),
                                requestId = call.requestId(),
                            ),
                        )
                    }

                    delete("/{id}") {
                        val principal = call.requirePlatformPrincipal()
                        val actor = call.requireNativeActor(AuthTokenService(settings))
                        val id =
                            call.parameters["id"]
                                ?.takeIf(String::isNotBlank)
                                ?: throw ApiException.Validation("id is required")
                        val service =
                            scheduleManagement
                                ?: throw ApiException.DependencyUnavailable(
                                    "Manual academic schedule management is not configured",
                                )
                        val deleted =
                            service.deleteScoped(
                                actor,
                                id,
                                call.boundedAuthRequest(
                                    org.companerodeescuela.shared.contracts.AcademicOperationRequest
                                        .serializer(),
                                ),
                            )
                        if (!deleted) throw ApiException.NotFound("Schedule block $id not found")
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
    }
}
