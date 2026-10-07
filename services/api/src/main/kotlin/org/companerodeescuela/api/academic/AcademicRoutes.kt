package org.companerodeescuela.api.academic

import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.time.LocalDate
import org.companerodeescuela.api.academic.groups.AcademicGroupRepository
import org.companerodeescuela.api.auth.AuthTokenService
import org.companerodeescuela.api.auth.platformRoles
import org.companerodeescuela.api.auth.requirePlatformPrincipal
import org.companerodeescuela.api.auth.subjectId
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.academic.AcademicProvider
import org.companerodeescuela.api.plugins.requestId
import org.companerodeescuela.shared.contracts.AcademicDataSource
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.UpsertScheduleBlockRequest
import org.companerodeescuela.shared.contracts.UserRole

fun Route.academicRoutes(
    settings: ApiSettings,
    academicProvider: AcademicProvider,
    scheduleManagement: AcademicScheduleManagementService? = null,
    scheduleOverrides: AcademicScheduleOverrideRepository? = null,
    groupRepository: AcademicGroupRepository? = null,
) {
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
                get("/load") {
                    val externalId = call.requirePlatformPrincipal().subjectId()
                    call.respond(
                        ApiResponse(
                            data = AcademicService(
                                provider = academicProvider,
                                scheduleOverrides = scheduleOverrides,
                                groupRepository = groupRepository,
                            ).loadFor(externalId),
                            requestId = call.requestId(),
                        ),
                    )
                }
                get("/schedule") {
                    val externalId = call.requirePlatformPrincipal().subjectId()
                    call.respond(
                        ApiResponse(
                            data = AcademicService(
                                provider = academicProvider,
                                scheduleOverrides = scheduleOverrides,
                                groupRepository = groupRepository,
                            ).scheduleFor(externalId),
                            requestId = call.requestId(),
                        ),
                    )
                }
                get("/schedule/v2") {
                    val externalId = call.requirePlatformPrincipal().subjectId()
                    val weekOf = call.request.queryParameters["weekOf"]
                        ?.let { raw ->
                            runCatching { LocalDate.parse(raw) }
                                .getOrElse { throw ApiException.Validation("weekOf must be YYYY-MM-DD") }
                        }
                        ?: throw ApiException.Validation("weekOf is required")

                    call.respond(
                        ApiResponse(
                            data = AcademicService(
                                provider = academicProvider,
                                scheduleOverrides = scheduleOverrides,
                                groupRepository = groupRepository,
                            ).scheduleWeekFor(
                                externalId = externalId,
                                weekOf = weekOf,
                            ),
                            requestId = call.requestId(),
                        ),
                    )
                }

                route("/manual-schedule") {
                    get("/{ownerId}") {
                        val principal = call.requirePlatformPrincipal()
                        principal.requireScheduleManager()
                        val ownerId = call.parameters["ownerId"]
                            ?.takeIf(String::isNotBlank)
                            ?: throw ApiException.Validation("ownerId is required")
                        val service = scheduleManagement
                            ?: throw ApiException.DependencyUnavailable(
                                "Manual academic schedule management is not configured",
                            )
                        call.respond(
                            ApiResponse(
                                data = service.listForOwner(ownerId),
                                requestId = call.requestId(),
                            ),
                        )
                    }

                    post {
                        val principal = call.requirePlatformPrincipal()
                        val source = principal.requireScheduleManager()
                        val service = scheduleManagement
                            ?: throw ApiException.DependencyUnavailable(
                                "Manual academic schedule management is not configured",
                            )
                        call.respond(
                            ApiResponse(
                                data = service.upsert(
                                    actorId = principal.subjectId(),
                                    source = source,
                                    request = call.receive<UpsertScheduleBlockRequest>(),
                                ),
                                requestId = call.requestId(),
                            ),
                        )
                    }

                    delete("/{id}") {
                        val principal = call.requirePlatformPrincipal()
                        principal.requireScheduleManager()
                        val id = call.parameters["id"]
                            ?.takeIf(String::isNotBlank)
                            ?: throw ApiException.Validation("id is required")
                        val service = scheduleManagement
                            ?: throw ApiException.DependencyUnavailable(
                                "Manual academic schedule management is not configured",
                            )
                        val deleted = service.delete(principal.subjectId(), id)
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

private fun io.ktor.server.auth.jwt.JWTPrincipal.requireScheduleManager(): AcademicDataSource {
    val roles = platformRoles()
    return when {
        UserRole.SUPER_ADMIN in roles || UserRole.ADMIN in roles -> AcademicDataSource.ADMIN_MANUAL
        UserRole.COORDINATOR in roles -> AcademicDataSource.SUPERVISOR_MANUAL
        else -> throw ApiException.Forbidden("Administrator or coordinator role is required")
    }
}
