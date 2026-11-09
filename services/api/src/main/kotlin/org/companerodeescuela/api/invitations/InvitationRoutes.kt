package org.companerodeescuela.api.invitations

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
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.companerodeescuela.api.audit.PlatformAuditEvent
import org.companerodeescuela.api.audit.PlatformAuditLog
import org.companerodeescuela.api.auth.AUTH_BODY_MAX_BYTES
import org.companerodeescuela.api.auth.AuthTokenService
import org.companerodeescuela.api.auth.LoginAttemptLimiter
import org.companerodeescuela.api.auth.PlatformAccountRepository
import org.companerodeescuela.api.auth.RateLimitStore
import org.companerodeescuela.api.auth.RefreshSessionRepository
import org.companerodeescuela.api.auth.boundedBody
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.config.Environment
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.identity.IdentityProvider
import org.companerodeescuela.api.plugins.clientAddress
import org.companerodeescuela.api.plugins.requestId
import org.companerodeescuela.api.security.NativeActorResolver
import org.companerodeescuela.shared.contracts.AcceptRegistrationInvitationRequest
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.PrepareRegistrationInvitationRequest
import org.companerodeescuela.shared.contracts.RegistrationInvitationDispatchReceipt
import org.companerodeescuela.shared.contracts.RegistrationInvitationPreparation
import org.companerodeescuela.shared.contracts.RegistrationInvitationReceipt
import java.time.Clock

/**
 * One wire contract with Android (`shared/contracts/RegistrationInvitationContracts.kt`):
 * this route intentionally does not re-declare invitation payloads.
 */

@Serializable
internal data class InvitationRetryBody(
    val invitationId: String,
    val proof: String,
)

@Serializable
internal data class InvitationRevokeBody(
    val invitationId: String,
)

internal fun Route.invitationRoutes(
    settings: ApiSettings,
    identity: IdentityProvider,
    sessions: RefreshSessionRepository,
    accounts: PlatformAccountRepository,
    service: InvitationDispatchService,
    invitations: InvitationRepository,
    clock: Clock = Clock.systemUTC(),
    rateLimitStore: RateLimitStore? = null,
    auditLog: PlatformAuditLog? = null,
) {
    // Fail closed in production until real QA delivery, Android acceptance and recovery gates are certified.
    if (!settings.hasAuthentication || settings.environment == Environment.PRODUCTION) return
    val resolver = NativeActorResolver(accounts, sessions, AuthTokenService(settings), identity)
    val issuerLimiter = LoginAttemptLimiter(maxAttempts = 5, store = rateLimitStore)
    val acceptanceLimiter = LoginAttemptLimiter(maxAttempts = 10, store = rateLimitStore)
    val acceptance = InvitationAcceptanceService(accounts, invitations, clock)

    suspend fun recordAudit(
        actorId: String,
        action: String,
        resourceType: String,
        resourceId: String,
        operationId: String?,
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
                createdAt = clock.instant(),
            ),
        )
    }

    suspend fun ApplicationCall.nativeActor() =
        resolver.resolve(
            principal<JWTPrincipal>()?.payload ?: throw ApiException.Unauthorized(),
        )

    fun toCommand(body: PrepareRegistrationInvitationRequest): InvitationDispatchCommand {
        val profile =
            InvitationProfile.entries.firstOrNull { it.name == body.profile }
                ?: throw ApiException.Validation("Invalid invitation profile")
        return InvitationDispatchCommand(body.recipient, body.institutionId, profile, body.operationId)
    }

    authenticate(AuthTokenService.PROVIDER_NAME) {
        route("/admin/invitations") {
            post("/prepare") {
                val actor = call.nativeActor()
                issuerLimiter.acquire(actor.id, call.clientAddress())?.let {
                    call.response.header(HttpHeaders.RetryAfter, it.toString())
                    throw ApiException.RateLimited()
                }
                val body = call.boundedBody(PrepareRegistrationInvitationRequest.serializer(), AUTH_BODY_MAX_BYTES)
                val prepared = service.prepare(actor, toCommand(body))
                recordAudit(
                    actor.id,
                    "INVITATION_PREPARE",
                    "INVITATION_OPERATION",
                    prepared.operationId,
                    prepared.operationId,
                    "SUCCEEDED",
                )
                call.respond(
                    ApiResponse(
                        RegistrationInvitationPreparation(
                            prepared.operationId,
                            prepared.resourceId,
                            prepared.payloadDigest,
                        ),
                        call.requestId(),
                    ),
                )
            }
            post {
                val actor = call.nativeActor()
                issuerLimiter.acquire(actor.id, call.clientAddress())?.let {
                    call.response.header(HttpHeaders.RetryAfter, it.toString())
                    throw ApiException.RateLimited()
                }
                val body = call.boundedBody(PrepareRegistrationInvitationRequest.serializer(), AUTH_BODY_MAX_BYTES)
                val proof = body.proof ?: throw ApiException.Forbidden("Scoped MFA proof is required")
                recordAudit(
                    actor.id,
                    "INVITATION_DISPATCH",
                    "INVITATION_OPERATION",
                    body.operationId,
                    body.operationId,
                    "STARTED",
                )
                val result = service.dispatch(actor, toCommand(body), proof)
                recordAudit(
                    actor.id,
                    "INVITATION_DISPATCH",
                    "INVITATION",
                    result.invitationId,
                    body.operationId,
                    "SUCCEEDED",
                )
                call.respond(
                    HttpStatusCode.Created,
                    ApiResponse(
                        RegistrationInvitationDispatchReceipt(result.invitationId, result.delivery, result.expiresAtEpochSeconds),
                        call.requestId(),
                    ),
                )
            }
            post("/retry") {
                val actor = call.nativeActor()
                issuerLimiter.acquire(actor.id, call.clientAddress())?.let {
                    call.response.header(HttpHeaders.RetryAfter, it.toString())
                    throw ApiException.RateLimited()
                }
                val body = call.boundedBody(InvitationRetryBody.serializer(), AUTH_BODY_MAX_BYTES)
                if (!Regex("[A-Za-z0-9_-]{43}").matches(body.invitationId) ||
                    !Regex("[A-Za-z0-9_-]{43}").matches(body.proof)
                ) {
                    throw ApiException.Validation("Invitation or proof is invalid")
                }
                recordAudit(
                    actor.id,
                    "INVITATION_RETRY",
                    "INVITATION",
                    body.invitationId,
                    body.invitationId,
                    "STARTED",
                )
                val result = service.retry(actor, body.invitationId, body.proof)
                recordAudit(
                    actor.id,
                    "INVITATION_RETRY",
                    "INVITATION",
                    result.invitationId,
                    body.invitationId,
                    "SUCCEEDED",
                )
                call.respond(
                    ApiResponse(
                        RegistrationInvitationDispatchReceipt(result.invitationId, result.delivery, result.expiresAtEpochSeconds),
                        call.requestId(),
                    ),
                )
            }
            post("/revoke") {
                val actor = call.nativeActor()
                issuerLimiter.acquire(actor.id, call.clientAddress())?.let {
                    call.response.header(HttpHeaders.RetryAfter, it.toString())
                    throw ApiException.RateLimited()
                }
                val body = call.boundedBody(InvitationRevokeBody.serializer(), AUTH_BODY_MAX_BYTES)
                if (!Regex("[A-Za-z0-9_-]{1,128}").matches(body.invitationId)) {
                    throw ApiException.Validation("Invitation is invalid")
                }
                recordAudit(
                    actor.id,
                    "INVITATION_REVOKE",
                    "INVITATION",
                    body.invitationId,
                    body.invitationId,
                    "STARTED",
                )
                val revoked = invitations.revoke(body.invitationId, clock.instant())
                if (!revoked) throw ApiException.Validation("Invitation cannot be revoked")
                recordAudit(
                    actor.id,
                    "INVITATION_REVOKE",
                    "INVITATION",
                    body.invitationId,
                    body.invitationId,
                    "SUCCEEDED",
                )
                call.respond(
                    ApiResponse(
                        RegistrationInvitationReceipt(body.invitationId, "REVOKED"),
                        call.requestId(),
                    ),
                )
            }
        }
    }
    authenticate(
        AuthTokenService.ACTIVE_PROVIDER_NAME,
        AuthTokenService.PENDING_APPROVAL_PROVIDER_NAME,
        strategy = AuthenticationStrategy.FirstSuccessful,
    ) {
        post("/auth/invitations/accept") {
            val actor = call.nativeActor()
            acceptanceLimiter.acquire(actor.id, call.clientAddress())?.let {
                call.response.header(HttpHeaders.RetryAfter, it.toString())
                throw ApiException.RateLimited()
            }
            val body = call.boundedBody(AcceptRegistrationInvitationRequest.serializer(), AUTH_BODY_MAX_BYTES)
            if (!Regex("[A-Za-z0-9_-]{43}").matches(body.invitationId) ||
                !Regex("[A-Za-z0-9_-]{43}").matches(body.code) ||
                !Regex("[A-Za-z0-9_-]{8,128}").matches(body.operationId)
            ) {
                throw ApiException.Validation("Invitation is invalid or unavailable")
            }
            recordAudit(
                actor.id,
                "INVITATION_ACCEPT",
                "INVITATION",
                body.invitationId,
                body.operationId,
                "STARTED",
            )
            val accepted = acceptance.accept(actor, body.invitationId, body.code, body.operationId)
            recordAudit(
                actor.id,
                "INVITATION_ACCEPT",
                "INVITATION",
                accepted.invitationId,
                body.operationId,
                "SUCCEEDED",
            )
            call.respond(ApiResponse(RegistrationInvitationReceipt(accepted.invitationId, accepted.status.name), call.requestId()))
        }
    }
}
