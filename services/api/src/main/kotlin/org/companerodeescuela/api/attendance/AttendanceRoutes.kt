package org.companerodeescuela.api.attendance

import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.companerodeescuela.api.auth.AuthTokenService
import org.companerodeescuela.api.auth.hasAdministrativeScope
import org.companerodeescuela.api.auth.requireAdministrative
import org.companerodeescuela.api.auth.requirePlatformPrincipal
import org.companerodeescuela.api.auth.requireRole
import org.companerodeescuela.api.auth.requireStaff
import org.companerodeescuela.api.auth.subjectId
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.plugins.requestId
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.AttendanceAttemptRequest
import org.companerodeescuela.shared.contracts.AttendanceQrInspectionRequest
import org.companerodeescuela.shared.contracts.CreateAttendanceSessionRequest
import org.companerodeescuela.shared.contracts.ClassCallConfirmationRequest
import org.companerodeescuela.shared.contracts.ReviewAttendanceRequest
import org.companerodeescuela.shared.contracts.UserRole

fun Route.attendanceRoutes(
    settings: ApiSettings,
    sessionService: AttendanceSessionService,
    studentService: AttendanceStudentService,
    reviewService: AttendanceReviewService,
    qrService: AttendanceQrService? = null,
    campusRoster: TeacherCampusRosterService? = null,
) {
    route("/attendance") {
        if (!settings.hasAuthentication) {
            get("/sessions/active") {
                throw ApiException.DependencyUnavailable("Authentication is not configured")
            }
            return@route
        }

        authenticate(AuthTokenService.PROVIDER_NAME) {
            get("/sessions/open") {
                val principal = call.requirePlatformPrincipal()
                principal.requireAdministrative()
                call.respond(
                    ApiResponse(
                        data = sessionService.activeForAdministration(),
                        requestId = call.requestId(),
                    ),
                )
            }

            get("/sessions/active") {
                val principal = call.requirePlatformPrincipal()
                principal.requireRole(UserRole.STUDENT)
                call.respond(
                    ApiResponse(
                        data = studentService.activeFor(principal.subjectId()),
                        requestId = call.requestId(),
                    ),
                )
            }

            post("/qr/inspect") {
                val principal = call.requirePlatformPrincipal()
                principal.requireRole(UserRole.STUDENT)
                call.respond(
                    ApiResponse(
                        data = studentService.inspectQr(
                            studentId = principal.subjectId(),
                            request = call.receive<AttendanceQrInspectionRequest>(),
                        ),
                        requestId = call.requestId(),
                    ),
                )
            }

            get("/occurrences/{occurrenceId}/campus-roster") {
                val principal = call.requirePlatformPrincipal()
                principal.requireRole(UserRole.TEACHER)
                val occurrenceId = call.parameters["occurrenceId"]
                    ?: throw ApiException.Validation("occurrenceId is required")
                val occurrenceDate = call.request.queryParameters["date"]
                    ?: throw ApiException.Validation("date is required")
                val rosterService = campusRoster
                    ?: throw ApiException.DependencyUnavailable("School presence verification is not configured")
                call.respond(
                    ApiResponse(
                        data = rosterService.forTeacher(
                            teacherId = principal.subjectId(),
                            occurrenceId = occurrenceId,
                            date = occurrenceDate,
                        ),
                        requestId = call.requestId(),
                    ),
                )
            }

            get("/sessions/mine") {
                val principal = call.requirePlatformPrincipal()
                principal.requireRole(UserRole.TEACHER)
                call.respond(
                    ApiResponse(
                        data = sessionService.activeForTeacher(principal.subjectId()),
                        requestId = call.requestId(),
                    ),
                )
            }

            post("/sessions") {
                val principal = call.requirePlatformPrincipal()
                principal.requireRole(UserRole.TEACHER)
                call.respond(
                    ApiResponse(
                        data = sessionService.openSession(
                            teacherId = principal.subjectId(),
                            request = call.receive<CreateAttendanceSessionRequest>(),
                        ),
                        requestId = call.requestId(),
                    ),
                )
            }

            post("/sessions/{sessionId}/qr") {
                val principal = call.requirePlatformPrincipal()
                principal.requireStaff()
                val sessionId = call.parameters["sessionId"]
                    ?: throw ApiException.Validation("sessionId is required")
                val serviceQr = qrService
                    ?: throw ApiException.DependencyUnavailable(
                        "Attendance QR signing is not configured",
                    )
                call.respond(
                    ApiResponse(
                        data = serviceQr.issue(
                            actorId = principal.subjectId(),
                            sessionId = sessionId,
                            allowCrossOwner = principal.hasAdministrativeScope(),
                        ),
                        requestId = call.requestId(),
                    ),
                )
            }

            post("/sessions/{sessionId}/attempts") {
                val principal = call.requirePlatformPrincipal()
                principal.requireRole(UserRole.STUDENT)
                val sessionId = call.parameters["sessionId"]
                    ?: throw ApiException.Validation("sessionId is required")
                call.respond(
                    ApiResponse(
                        data = studentService.register(
                            studentId = principal.subjectId(),
                            sessionId = sessionId,
                            request = call.receive<AttendanceAttemptRequest>(),
                        ),
                        requestId = call.requestId(),
                    ),
                )
            }

            post("/sessions/{sessionId}/confirm") {
                val principal = call.requirePlatformPrincipal()
                principal.requireRole(UserRole.STUDENT)
                val sessionId = call.parameters["sessionId"]
                    ?: throw ApiException.Validation("sessionId is required")
                call.respond(
                    ApiResponse(
                        data = studentService.confirmClassCall(
                            studentId = principal.subjectId(),
                            sessionId = sessionId,
                            request = call.receive<ClassCallConfirmationRequest>(),
                        ),
                        requestId = call.requestId(),
                    ),
                )
            }

            post("/sessions/{sessionId}/close") {
                val principal = call.requirePlatformPrincipal()
                principal.requireStaff()
                val sessionId = call.parameters["sessionId"]
                    ?: throw ApiException.Validation("sessionId is required")
                call.respond(
                    ApiResponse(
                        data = sessionService.closeSession(
                            actorId = principal.subjectId(),
                            sessionId = sessionId,
                            allowCrossOwner = principal.hasAdministrativeScope(),
                        ),
                        requestId = call.requestId(),
                    ),
                )
            }

            get("/sessions/{sessionId}/roster") {
                val principal = call.requirePlatformPrincipal()
                principal.requireStaff()
                val sessionId = call.parameters["sessionId"]
                    ?: throw ApiException.Validation("sessionId is required")
                call.respond(
                    ApiResponse(
                        data = sessionService.roster(
                            actorId = principal.subjectId(),
                            sessionId = sessionId,
                            allowCrossOwner = principal.hasAdministrativeScope(),
                        ),
                        requestId = call.requestId(),
                    ),
                )
            }

            patch("/records/{recordId}/review") {
                val principal = call.requirePlatformPrincipal()
                principal.requireStaff()
                val recordId = call.parameters["recordId"]
                    ?: throw ApiException.Validation("recordId is required")
                call.respond(
                    ApiResponse(
                        data = reviewService.review(
                            reviewerId = principal.subjectId(),
                            recordId = recordId,
                            request = call.receive<ReviewAttendanceRequest>(),
                            allowCrossOwner = principal.hasAdministrativeScope(),
                        ),
                        requestId = call.requestId(),
                    ),
                )
            }
        }
    }
}
