package org.companerodeescuela.api.admin

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.AuthenticationStrategy
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.DeserializationStrategy
import org.companerodeescuela.api.audit.PlatformAuditEvent
import org.companerodeescuela.api.audit.PlatformAuditLog
import org.companerodeescuela.api.auth.AuthTokenService
import org.companerodeescuela.api.auth.LoginAttemptLimiter
import org.companerodeescuela.api.auth.PlatformAccount
import org.companerodeescuela.api.auth.PlatformAccountRepository
import org.companerodeescuela.api.auth.RefreshSessionRepository
import org.companerodeescuela.api.auth.boundedAuthRequest
import org.companerodeescuela.api.auth.staffApplications
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.identity.IdentityProvider
import org.companerodeescuela.api.plugins.requestId
import org.companerodeescuela.api.security.NativeActorResolver
import org.companerodeescuela.shared.contracts.AccountLifecycleRequest
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.ApproveStaffRequest
import org.companerodeescuela.shared.contracts.ApproveStudentAffiliationRequest
import org.companerodeescuela.shared.contracts.TeacherApplicationRequest
import org.companerodeescuela.shared.contracts.TutorApplicationRequest
import java.util.UUID

internal fun Route.accountAdministrationRoutes(
    settings: ApiSettings,
    identity: IdentityProvider,
    sessions: RefreshSessionRepository,
    accounts: PlatformAccountRepository,
    service: AccountAdministrationService,
    auditLog: PlatformAuditLog? = null,
) {
    if (!settings.hasAuthentication) return
    val tokens = AuthTokenService(settings)
    val resolver = NativeActorResolver(accounts, sessions, tokens, identity)
    val limiter = LoginAttemptLimiter(maxAttempts = 20)

    suspend fun ApplicationCall.actor(mutation: Boolean = false): PlatformAccount {
        response.header(HttpHeaders.CacheControl, "no-store")
        val current = resolver.resolve(principal<JWTPrincipal>()?.payload ?: throw ApiException.Unauthorized())
        if (mutation) {
            limiter.acquire(current.id, "native-administration")?.let { retry ->
                response.header(HttpHeaders.RetryAfter, retry.toString())
                throw ApiException.RateLimited()
            }
        }
        return current
    }

    suspend fun recordAudit(
        actorId: String,
        action: String,
        resourceType: String,
        resourceId: String,
        operationId: String,
        outcome: String,
    ) {
        auditLog?.record(
            PlatformAuditEvent.create(
                actorId = actorId,
                action = action,
                resourceType = resourceType,
                resourceId = resourceId,
                operationId = operationId,
                outcome = outcome,
                createdAt = java.time.Instant.now(),
            ),
        )
    }

    authenticate(
        AuthTokenService.ACTIVE_PROVIDER_NAME,
        AuthTokenService.PENDING_APPROVAL_PROVIDER_NAME,
        strategy = AuthenticationStrategy.FirstSuccessful,
    ) {
        route("/auth/staff-applications") {
            get {
                val actor = call.actor()
                if (actor.emailVerifiedAt == null) throw ApiException.Forbidden("Verified native identity is required")
                call.respond(ApiResponse(actor.staffApplications(), call.requestId()))
            }
            post {
                val actor = call.actor(true)
                val request = call.administrationRequest(TutorApplicationRequest.serializer())
                val operationId = UUID.randomUUID().toString()
                recordAudit(actor.id, "STAFF_APPLICATION_TUTOR", "STAFF_APPLICATION", actor.id, operationId, "STARTED")
                val result = service.requestTutor(actor, request)
                recordAudit(actor.id, "STAFF_APPLICATION_TUTOR", "STAFF_APPLICATION", actor.id, operationId, "SUCCEEDED")
                call.respond(HttpStatusCode.Created, ApiResponse(result, operationId))
            }
            post("/teacher") {
                val actor = call.actor(true)
                val request = call.administrationRequest(TeacherApplicationRequest.serializer())
                val operationId = UUID.randomUUID().toString()
                recordAudit(actor.id, "STAFF_APPLICATION_TEACHER", "STAFF_APPLICATION", actor.id, operationId, "STARTED")
                val result = service.requestTeacher(actor, request)
                recordAudit(actor.id, "STAFF_APPLICATION_TEACHER", "STAFF_APPLICATION", actor.id, operationId, "SUCCEEDED")
                call.respond(HttpStatusCode.Created, ApiResponse(result, operationId))
            }
        }
    }
    authenticate(AuthTokenService.PROVIDER_NAME) {
        route("/admin/accounts") {
            get { call.respond(ApiResponse(service.overview(call.actor()), call.requestId())) }
            get("/academic-directory") { call.respond(ApiResponse(service.academicDirectory(call.actor()), call.requestId())) }
            get("/approval-groups") { call.respond(ApiResponse(service.approvalGroups(call.actor()), call.requestId())) }
            post("/{accountId}/staff-approval") {
                val actor = call.actor(true)
                val request = call.administrationRequest(ApproveStaffRequest.serializer())
                val id = call.parameters["accountId"] ?: throw ApiException.Validation("Account is required")
                val operationId = UUID.randomUUID().toString()
                recordAudit(actor.id, "ACCOUNT_STAFF_APPROVAL", "ACCOUNT", id, operationId, "STARTED")
                val result = service.approveStaff(actor, id, request)
                recordAudit(actor.id, "ACCOUNT_STAFF_APPROVAL", "ACCOUNT", id, operationId, "SUCCEEDED")
                call.respond(ApiResponse(result, operationId))
            }
            post("/{accountId}/student-affiliation") {
                val actor = call.actor(true)
                val request = call.administrationRequest(ApproveStudentAffiliationRequest.serializer())
                val id = call.parameters["accountId"] ?: throw ApiException.Validation("Account is required")
                val operationId = UUID.randomUUID().toString()
                recordAudit(actor.id, "ACCOUNT_STUDENT_AFFILIATION", "ACCOUNT", id, operationId, "STARTED")
                val result = service.approveStudentAffiliation(actor, id, request)
                recordAudit(actor.id, "ACCOUNT_STUDENT_AFFILIATION", "ACCOUNT", id, operationId, "SUCCEEDED")
                call.respond(ApiResponse(result, operationId))
            }
            post("/{accountId}/lifecycle") {
                val actor = call.actor(true)
                val request = call.administrationRequest(AccountLifecycleRequest.serializer())
                val id = call.parameters["accountId"] ?: throw ApiException.Validation("Account is required")
                val operationId = UUID.randomUUID().toString()
                recordAudit(actor.id, "ACCOUNT_LIFECYCLE", "ACCOUNT", id, operationId, "STARTED")
                val result = service.lifecycle(actor, id, request)
                recordAudit(actor.id, "ACCOUNT_LIFECYCLE", "ACCOUNT", id, operationId, "SUCCEEDED")
                call.respond(ApiResponse(result, operationId))
            }
        }
    }
}

private suspend fun <T> ApplicationCall.administrationRequest(serializer: DeserializationStrategy<T>): T =
    try {
        boundedAuthRequest(serializer)
    } catch (
        cancelled: CancellationException,
    ) {
        throw cancelled
    } catch (_: Exception) {
        throw ApiException.Validation("Invalid administration request")
    }
