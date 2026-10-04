package org.companerodeescuela.api.attendance

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
import org.companerodeescuela.shared.contracts.AttendanceAttemptRequest\nimport org.companerodeescuela.shared.contracts.AttendanceQrInspectionRequest
import org.companerodeescuela.shared.contracts.CreateAttendanceSessionRequest
import org.companerodeescuela.shared.contracts.ReviewAttendanceRequest
import org.companerodeescuela.shared.contracts.UserRole

fun Route.attendanceRoutes(
    settings: ApiSettings,
    service: AttendanceService,
    qrService: AttendanceQrService? = null,
) {
    route("/attendance") {
        if (!settings.hasAuthentication) {
            get("/sessions/active") {
                throw ApiException.DependencyUnavailable("Authentication is not configured")
            }
            return@route
        }

        authenticate(AuthTokenService.PROVIDER_NAME) {
            get("/sessions/active") {
                val principal = call.requirePrincipal()
                principal.requireStudentRole()
                call.respond(
                    ApiResponse(
                        data = service.activeFor(principal.subject()),
                        requestId = call.requestId(),
                    ),
                )
            }

            post("/qr/inspect") {
                val principal = call.requirePrincipal()
                principal.requireStudentRole()
                call.respond(
                    ApiResponse(
                        data = service.inspectQr(
                            studentId = principal.subject(),
                            request = call.receive<AttendanceQrInspectionRequest>(),
                        ),
                        requestId = call.requestId(),
                    ),
                )
            }

            get("/sessions/mine") {
                val principal = call.requirePrincipal()
                principal.requireTeacherRole()
                call.respond(
                    ApiResponse(
                        data = service.activeForTeacher(principal.subject()),
                        requestId = call.requestId(),
                    ),
                )
            }

            post("/sessions") {
                val principal = call.requirePrincipal()
                principal.requireTeacherRole()
                call.respond(
                    ApiResponse(
                        data = service.openSession(
                            teacherId = principal.subject(),
                            request = call.receive<CreateAttendanceSessionRequest>(),
                        ),
                        requestId = call.requestId(),
                    ),
                )
            }

            post("/sessions/{sessionId}/qr") {
                val principal = call.requirePrincipal()
                principal.requireStaffRole()
                val sessionId = call.parameters["sessionId"]
                    ?: throw ApiException.Validation("sessionId is required")
                val serviceQr = qrService
                    ?: throw ApiException.DependencyUnavailable(
                        "Attendance QR signing is not configured",
                    )
                call.respond(
                    ApiResponse(
                        data = serviceQr.issue(
                            actorId = principal.subject(),
                            sessionId = sessionId,
                            allowCrossOwner = principal.roles().any(UserRole::isAdministrative),
                        ),
                        requestId = call.requestId(),
                    ),
                )
            }

            post("/sessions/{sessionId}/attempts") {
                val principal = call.requirePrincipal()
                principal.requireStudentRole()
                val sessionId = call.parameters["sessionId"]
                    ?: throw ApiException.Validation("sessionId is required")
                call.respond(
                    ApiResponse(
                        data = service.register(
                            studentId = principal.subject(),
                            sessionId = sessionId,
                            request = call.receive<AttendanceAttemptRequest>(),
                        ),
                        requestId = call.requestId(),
                    ),
                )
            }

            post("/sessions/{sessionId}/close") {
                val principal = call.requirePrincipal()
                principal.requireStaffRole()
                val sessionId = call.parameters["sessionId"]
                    ?: throw ApiException.Validation("sessionId is required")
                call.respond(
                    ApiResponse(
                        data = service.closeSession(
                            actorId = principal.subject(),
                            sessionId = sessionId,
                            allowCrossOwner = principal.roles().any(UserRole::isAdministrative),
                        ),
                        requestId = call.requestId(),
                    ),
                )
            }

            get("/sessions/{sessionId}/roster") {
                val principal = call.requirePrincipal()
                principal.requireStaffRole()
                val sessionId = call.parameters["sessionId"]
                    ?: throw ApiException.Validation("sessionId is required")
                call.respond(
                    ApiResponse(
                        data = service.roster(
                            actorId = principal.subject(),
                            sessionId = sessionId,
                            allowCrossOwner = principal.roles().any(UserRole::isAdministrative),
                        ),
                        requestId = call.requestId(),
                    ),
                )
            }

            patch("/records/{recordId}/review") {
                val principal = call.requirePrincipal()
                principal.requireStaffRole()
                val recordId = call.parameters["recordId"]
                    ?: throw ApiException.Validation("recordId is required")
                call.respond(
                    ApiResponse(
                        data = service.review(
                            reviewerId = principal.subject(),
                            recordId = recordId,
                            request = call.receive<ReviewAttendanceRequest>(),
                            allowCrossOwner = principal.roles().any(UserRole::isAdministrative),
                        ),
                        requestId = call.requestId(),
                    ),
                )
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.requirePrincipal(): JWTPrincipal =
    principal<JWTPrincipal>() ?: throw ApiException.Unauthorized()

private fun JWTPrincipal.subject(): String =
    payload.subject?.takeIf { it.isNotBlank() } ?: throw ApiException.Unauthorized()

private fun JWTPrincipal.roles(): Set<UserRole> =
    payload.getClaim("roles")
        .asList(String::class.java)
        .orEmpty()
        .mapNotNull { encoded -> runCatching { UserRole.valueOf(encoded) }.getOrNull() }
        .toSet()

private fun JWTPrincipal.requireStudentRole() {
    if (UserRole.STUDENT !in roles()) {
        throw ApiException.Forbidden("Student role is required")
    }
}

private fun JWTPrincipal.requireTeacherRole() {
    if (UserRole.TEACHER !in roles()) {
        throw ApiException.Forbidden("Teacher role is required")
    }
}

private fun JWTPrincipal.requireStaffRole() {
    if (roles().none { it.isStaff }) {
        throw ApiException.Forbidden("Staff role is required")
    }
}
