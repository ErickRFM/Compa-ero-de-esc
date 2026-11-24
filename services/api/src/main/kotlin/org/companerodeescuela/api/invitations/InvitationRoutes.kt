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
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.CancellationException
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
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.PrepareRegistrationInvitationRequest
import org.companerodeescuela.shared.contracts.PrepareRegistrationInvitationRetryRequest
import org.companerodeescuela.shared.contracts.RegistrationInvitationDispatchReceipt
import org.companerodeescuela.shared.contracts.RegistrationInvitationPreparation
import org.companerodeescuela.shared.contracts.RegistrationInvitationReceipt
import org.companerodeescuela.shared.contracts.RegistrationInvitationSummary
import org.companerodeescuela.shared.contracts.RetryRegistrationInvitationRequest
import org.companerodeescuela.shared.contracts.UserRole
import java.time.Clock
import java.util.UUID

/**
 * One wire contract with Android (`shared/contracts/RegistrationInvitationContracts.kt`):
 * this route intentionally does not re-declare invitation payloads.
 */

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
    // Production invitations remain disabled unless explicitly opted in with all required dependencies.
    val productionBlocked =
        settings.environment == Environment.PRODUCTION &&
            (
                !settings.productionInvitationsEnabled ||
                    !settings.mongo.isConfigured ||
                    !settings.hasVerificationEmail ||
                    settings.mfaEncryptionKey == null
            )
    if (!settings.hasAuthentication || productionBlocked) {
        val disabledMessage =
            if (!settings.hasAuthentication) {
                "Authentication is not configured"
            } else {
                "Invitations are disabled in this environment"
            }
        route("/admin/invitations") {
            get { throw ApiException.DependencyUnavailable(disabledMessage) }
            post { throw ApiException.DependencyUnavailable(disabledMessage) }
            for (path in listOf("/prepare", "/retry/prepare", "/retry", "/revoke")) {
                post(path) { throw ApiException.DependencyUnavailable(disabledMessage) }
            }
        }
        post("/auth/invitations/accept") {
            throw ApiException.DependencyUnavailable(disabledMessage)
        }
        return
    }
    val resolver = NativeActorResolver(accounts, sessions, AuthTokenService(settings), identity)
    // A single invitation uses prepare and dispatch: keep independent abuse buckets.
    val issuerLimiter = LoginAttemptLimiter(maxAttempts = 5, store = rateLimitStore)
    val readLimiter = LoginAttemptLimiter(maxAttempts = 30, store = rateLimitStore)
    val acceptanceLimiter = LoginAttemptLimiter(maxAttempts = 10, store = rateLimitStore)
    val acceptance = InvitationAcceptanceService(accounts, invitations, clock)
    val auditOperationPattern = Regex("[A-Za-z0-9_-]{1,128}")

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

    suspend fun <T> auditedMutation(
        actorId: String,
        action: String,
        startResourceType: String,
        startResourceId: String,
        operationId: String,
        successResourceType: String = startResourceType,
        successResourceId: (T) -> String = { startResourceId },
        block: suspend () -> T,
    ): T {
        recordAudit(actorId, action, startResourceType, startResourceId, operationId, "STARTED")
        val result =
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                recordAudit(actorId, action, startResourceType, startResourceId, operationId, "FAILED")
                throw failure
            }
        recordAudit(actorId, action, successResourceType, successResourceId(result), operationId, "SUCCEEDED")
        return result
    }

    suspend fun ApplicationCall.nativeActor() =
        resolver
            .resolve(
                principal<JWTPrincipal>()?.payload ?: throw ApiException.Unauthorized(),
            ).also {
                response.header(HttpHeaders.CacheControl, "no-store")
            }

    fun toCommand(body: PrepareRegistrationInvitationRequest): InvitationDispatchCommand {
        val profile =
            InvitationProfile.entries.firstOrNull { it.name == body.profile }
                ?: throw ApiException.Validation("Invalid invitation profile")
        return InvitationDispatchCommand(body.recipient, body.institutionId, profile, body.operationId)
    }

    authenticate(AuthTokenService.PROVIDER_NAME) {
        route("/admin/invitations") {
            get {
                val actor = call.nativeActor()
                if (actor.accountStatus != AccountStatus.ACTIVE ||
                    actor.emailVerifiedAt == null ||
                    actor.emailVerifiedAt > clock.instant() ||
                    UserRole.SUPER_ADMIN !in actor.roles
                ) {
                    throw ApiException.Forbidden("Verified super administrator is required to list invitations")
                }
                readLimiter.acquire("read:" + actor.id, call.clientAddress())?.let {
                    call.response.header(HttpHeaders.RetryAfter, it.toString())
                    throw ApiException.RateLimited()
                }
                val rawLimit = call.request.queryParameters["limit"]?.trim()
                val limit =
                    if (rawLimit.isNullOrEmpty()) {
                        50
                    } else {
                        val parsed = rawLimit.toIntOrNull()
                        if (parsed == null || parsed !in 1..50) {
                            throw ApiException.Validation("Invitation limit must be between 1 and 50")
                        }
                        parsed
                    }
                val institutionId =
                    call.request.queryParameters["institutionId"]
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?.also {
                            if (!it.matches(Regex("[A-Za-z0-9_-]{1,128}"))) {
                                throw ApiException.Validation("Invalid institution filter")
                            }
                        }
                val items =
                    invitations
                        .listRecent(limit = limit, institutionId = institutionId)
                        .map { record ->
                            RegistrationInvitationSummary(
                                invitationId = record.id,
                                recipient = record.recipient,
                                institutionId = record.institutionId,
                                profile = record.profile.name,
                                status = record.status.name,
                                createdAtEpochSeconds = record.createdAt.epochSecond,
                                expiresAtEpochSeconds = record.expiresAt.epochSecond,
                                acceptedAtEpochSeconds = record.acceptedAt?.epochSecond,
                                revokedAtEpochSeconds = record.revokedAt?.epochSecond,
                                deliveryStatus = record.deliveryStatus ?: defaultDeliveryStatus(record.status),
                                deliveryUpdatedAtEpochSeconds = record.deliveryUpdatedAt?.epochSecond,
                                acceptedByAccountId = record.acceptedBy,
                            )
                        }
                val readOperationId =
                    call.requestId()?.takeIf { auditOperationPattern.matches(it) } ?: UUID.randomUUID().toString()
                recordAudit(
                    actor.id,
                    "INVITATION_LIST",
                    "INVITATION_DIRECTORY",
                    institutionId ?: "ALL",
                    readOperationId,
                    "SUCCEEDED",
                )
                call.respond(ApiResponse(items, call.requestId()))
            }
            post("/prepare") {
                val actor = call.nativeActor()
                issuerLimiter.acquire("prepare:" + actor.id, call.clientAddress())?.let {
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
                issuerLimiter.acquire("dispatch:" + actor.id, call.clientAddress())?.let {
                    call.response.header(HttpHeaders.RetryAfter, it.toString())
                    throw ApiException.RateLimited()
                }
                val body = call.boundedBody(PrepareRegistrationInvitationRequest.serializer(), AUTH_BODY_MAX_BYTES)
                if (!Regex("[A-Za-z0-9_-]{8,128}").matches(body.operationId)) {
                    throw ApiException.Validation("Operation identifier is invalid")
                }
                val proof = body.proof ?: throw ApiException.Forbidden("Scoped MFA proof is required")
                val command = toCommand(body)
                val result =
                    auditedMutation(
                        actorId = actor.id,
                        action = "INVITATION_DISPATCH",
                        startResourceType = "INVITATION_OPERATION",
                        startResourceId = body.operationId,
                        operationId = body.operationId,
                        successResourceType = "INVITATION",
                        successResourceId = { it.invitationId },
                    ) {
                        service.dispatch(actor, command, proof)
                    }
                call.respond(
                    HttpStatusCode.Created,
                    ApiResponse(
                        RegistrationInvitationDispatchReceipt(result.invitationId, result.delivery, result.expiresAtEpochSeconds),
                        call.requestId(),
                    ),
                )
            }
            post("/retry/prepare") {
                val actor = call.nativeActor()
                issuerLimiter.acquire("prepare:" + actor.id, call.clientAddress())?.let {
                    call.response.header(HttpHeaders.RetryAfter, it.toString())
                    throw ApiException.RateLimited()
                }
                val body = call.boundedBody(PrepareRegistrationInvitationRetryRequest.serializer(), AUTH_BODY_MAX_BYTES)
                val prepared = service.prepareRetry(actor, body.invitationId, body.operationId)
                recordAudit(
                    actor.id,
                    "INVITATION_RETRY_PREPARE",
                    "INVITATION",
                    body.invitationId,
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
            post("/retry") {
                val actor = call.nativeActor()
                issuerLimiter.acquire("manage:" + actor.id, call.clientAddress())?.let {
                    call.response.header(HttpHeaders.RetryAfter, it.toString())
                    throw ApiException.RateLimited()
                }
                val body = call.boundedBody(RetryRegistrationInvitationRequest.serializer(), AUTH_BODY_MAX_BYTES)
                if (!Regex("[A-Za-z0-9_-]{43}").matches(body.invitationId) ||
                    !Regex("[A-Za-z0-9_-]{8,128}").matches(body.operationId) ||
                    !Regex("[A-Za-z0-9_-]{43}").matches(body.proof)
                ) {
                    throw ApiException.Validation("Invitation, operation, or proof is invalid")
                }
                val result =
                    auditedMutation(
                        actorId = actor.id,
                        action = "INVITATION_RETRY",
                        startResourceType = "INVITATION",
                        startResourceId = body.invitationId,
                        operationId = body.operationId,
                        successResourceId = { it.invitationId },
                    ) {
                        service.retry(actor, body.invitationId, body.operationId, body.proof)
                    }
                call.respond(
                    ApiResponse(
                        RegistrationInvitationDispatchReceipt(result.invitationId, result.delivery, result.expiresAtEpochSeconds),
                        call.requestId(),
                    ),
                )
            }
            post("/revoke") {
                val actor = call.nativeActor()
                if (actor.accountStatus != AccountStatus.ACTIVE ||
                    actor.emailVerifiedAt == null ||
                    actor.emailVerifiedAt > clock.instant() ||
                    UserRole.SUPER_ADMIN !in actor.roles
                ) {
                    throw ApiException.Forbidden("Verified super administrator is required to revoke invitations")
                }
                issuerLimiter.acquire("manage:" + actor.id, call.clientAddress())?.let {
                    call.response.header(HttpHeaders.RetryAfter, it.toString())
                    throw ApiException.RateLimited()
                }
                val body = call.boundedBody(InvitationRevokeBody.serializer(), AUTH_BODY_MAX_BYTES)
                if (!Regex("[A-Za-z0-9_-]{1,128}").matches(body.invitationId)) {
                    throw ApiException.Validation("Invitation is invalid")
                }
                val auditOperationId = UUID.randomUUID().toString()
                auditedMutation(
                    actorId = actor.id,
                    action = "INVITATION_REVOKE",
                    startResourceType = "INVITATION",
                    startResourceId = body.invitationId,
                    operationId = auditOperationId,
                ) {
                    val revoked = invitations.revoke(body.invitationId, clock.instant())
                    if (!revoked) throw ApiException.Validation("Invitation cannot be revoked")
                }
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
            val accepted =
                auditedMutation(
                    actorId = actor.id,
                    action = "INVITATION_ACCEPT",
                    startResourceType = "INVITATION",
                    startResourceId = body.invitationId,
                    operationId = body.operationId,
                    successResourceId = { it.invitationId },
                ) {
                    acceptance.accept(actor, body.invitationId, body.code, body.operationId)
                }
            call.respond(ApiResponse(RegistrationInvitationReceipt(accepted.invitationId, accepted.status.name), call.requestId()))
        }
    }
}

private fun defaultDeliveryStatus(status: InvitationStatus): String =
    when (status) {
        InvitationStatus.FAILED -> "FAILED"
        InvitationStatus.CREATED, InvitationStatus.SENT -> "PENDING"
        InvitationStatus.ACCEPTED, InvitationStatus.ACTIVE -> "ACCEPTED"
        InvitationStatus.EXPIRED, InvitationStatus.REVOKED -> "UNCONFIRMED"
    }
