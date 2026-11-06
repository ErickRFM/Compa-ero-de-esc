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
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.VerificationDeliveryStatus
import java.time.Clock

/** No public unauthenticated invitation-token oracle or role assignment route. */
@Serializable
internal data class InvitationCreateBody(
    val recipient: String,
    val institutionId: String,
    val profile: String,
    val operationId: String,
    val proof: String? = null,
)

@Serializable
internal data class InvitationPrepareResponse(
    val operationId: String,
    val resourceId: String,
    val payloadDigest: String,
)

@Serializable
internal data class InvitationSendResponse(
    val invitationId: String,
    val delivery: VerificationDeliveryStatus,
    val expiresAtEpochSeconds: Long,
)

@Serializable
internal data class InvitationAcceptBody(
    val invitationId: String,
    val code: String,
    val operationId: String,
)

@Serializable
internal data class InvitationRetryBody(
    val invitationId: String,
    val proof: String,
)

@Serializable
internal data class InvitationRevokeBody(
    val invitationId: String,
)

@Serializable
internal data class InvitationAcceptResponse(
    val invitationId: String,
    val status: String,
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
) {
    // Fail closed in production until real QA delivery, Android acceptance and recovery gates are certified.
    if (!settings.hasAuthentication || settings.environment == Environment.PRODUCTION) return
    val resolver = NativeActorResolver(accounts, sessions, AuthTokenService(settings), identity)
    val issuerLimiter = LoginAttemptLimiter(maxAttempts = 5, store = rateLimitStore)
    val acceptanceLimiter = LoginAttemptLimiter(maxAttempts = 10, store = rateLimitStore)

    suspend fun ApplicationCall.nativeActor() =
        resolver.resolve(
            principal<JWTPrincipal>()?.payload ?: throw ApiException.Unauthorized(),
        )

    fun toCommand(body: InvitationCreateBody): InvitationDispatchCommand {
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
                val body = call.boundedBody(InvitationCreateBody.serializer(), AUTH_BODY_MAX_BYTES)
                val prepared = service.prepare(actor, toCommand(body))
                call.respond(
                    ApiResponse(
                        InvitationPrepareResponse(prepared.operationId, prepared.resourceId, prepared.payloadDigest),
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
                val body = call.boundedBody(InvitationCreateBody.serializer(), AUTH_BODY_MAX_BYTES)
                val proof = body.proof ?: throw ApiException.Forbidden("Scoped MFA proof is required")
                val result = service.dispatch(actor, toCommand(body), proof)
                call.respond(
                    HttpStatusCode.Created,
                    ApiResponse(
                        InvitationSendResponse(result.invitationId, result.delivery, result.expiresAtEpochSeconds),
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
                val result = service.retry(actor, body.invitationId, body.proof)
                call.respond(
                    ApiResponse(
                        InvitationSendResponse(result.invitationId, result.delivery, result.expiresAtEpochSeconds),
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
                val revoked = invitations.revoke(body.invitationId, clock.instant())
                if (!revoked) throw ApiException.Validation("Invitation cannot be revoked")
                call.respond(
                    ApiResponse(
                        InvitationAcceptResponse(body.invitationId, "REVOKED"),
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
            val body = call.boundedBody(InvitationAcceptBody.serializer(), AUTH_BODY_MAX_BYTES)
            if (!Regex("[A-Za-z0-9_-]{43}").matches(body.invitationId) ||
                !Regex("[A-Za-z0-9_-]{43}").matches(body.code) ||
                !Regex("[A-Za-z0-9_-]{8,128}").matches(body.operationId)
            ) {
                throw ApiException.Validation("Invitation is invalid or unavailable")
            }
            val now = clock.instant()
            if (!actor.permitsSession ||
                actor.accountStatus !in setOf(AccountStatus.ACTIVE, AccountStatus.PENDING_APPROVAL) ||
                actor.emailVerifiedAt == null ||
                actor.emailVerifiedAt > now
            ) {
                throw ApiException.Forbidden("Verified native account is required")
            }
            val accepted =
                invitations.accept(
                    body.invitationId,
                    InvitationToken.hash(body.code),
                    actor.email,
                    true,
                    actor.id,
                    body.operationId,
                    now,
                ) ?: throw ApiException.Validation("Invitation is invalid or unavailable")
            call.respond(ApiResponse(InvitationAcceptResponse(accepted.id, accepted.status.name), call.requestId()))
        }
    }
}
