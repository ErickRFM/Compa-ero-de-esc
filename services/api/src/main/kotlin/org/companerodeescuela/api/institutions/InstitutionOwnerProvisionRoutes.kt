package org.companerodeescuela.api.institutions

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
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
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.identity.IdentityProvider
import org.companerodeescuela.api.plugins.clientAddress
import org.companerodeescuela.api.plugins.requestId
import org.companerodeescuela.api.security.MfaService
import org.companerodeescuela.api.security.NativeActorResolver
import org.companerodeescuela.shared.contracts.ApiResponse

@Serializable
internal data class InstitutionProvisionBody(
    val institutionId: String,
    val displayName: String,
    val operationId: String,
    val proof: String? = null,
)

@Serializable
internal data class InstitutionProvisionPreparation(
    val operationId: String,
    val resourceId: String,
    val payloadDigest: String,
)

internal fun Route.institutionOwnerProvisionRoutes(
    settings: ApiSettings,
    identity: IdentityProvider,
    sessions: RefreshSessionRepository,
    accounts: PlatformAccountRepository,
    mfa: MfaService,
    institutions: InstitutionRepository,
    rateLimitStore: RateLimitStore? = null,
    auditLog: PlatformAuditLog? = null,
) {
    if (!settings.hasAuthentication) return
    val resolver = NativeActorResolver(accounts, sessions, AuthTokenService(settings), identity)
    val limiter = LoginAttemptLimiter(maxAttempts = 5, store = rateLimitStore)
    val service = InstitutionOwnerProvisionService(accounts, institutions, mfa)

    suspend fun recordAudit(
        actorId: String,
        action: String,
        institutionId: String,
        operationId: String,
        outcome: String,
    ) {
        auditLog?.record(
            PlatformAuditEvent.create(
                actorId = actorId,
                action = action,
                resourceType = "INSTITUTION",
                resourceId = institutionId,
                operationId = operationId,
                outcome = outcome,
                createdAt = java.time.Instant.now(),
            ),
        )
    }

    suspend fun ApplicationCall.owner() =
        resolver.resolve(
            principal<JWTPrincipal>()?.payload ?: throw ApiException.Unauthorized(),
        )

    fun InstitutionProvisionBody.command(): InstitutionProvisionCommand =
        InstitutionProvisionCommand(institutionId, displayName, operationId)

    authenticate(AuthTokenService.PROVIDER_NAME) {
        route("/admin/institutions") {
            post("/prepare") {
                val actor = call.owner()
                limiter.acquire(actor.id, call.clientAddress())?.let {
                    call.response.header(HttpHeaders.RetryAfter, it.toString())
                    throw ApiException.RateLimited()
                }
                val body = call.boundedBody(InstitutionProvisionBody.serializer(), AUTH_BODY_MAX_BYTES)
                val digest = service.prepare(actor, body.command())
                recordAudit(
                    actor.id,
                    "INSTITUTION_PREPARE",
                    body.institutionId,
                    body.operationId,
                    "SUCCEEDED",
                )
                call.respond(
                    ApiResponse(
                        InstitutionProvisionPreparation(
                            body.operationId,
                            InstitutionOwnerProvisionService.RESOURCE_ID,
                            digest,
                        ),
                        call.requestId(),
                    ),
                )
            }
            post {
                val actor = call.owner()
                limiter.acquire(actor.id, call.clientAddress())?.let {
                    call.response.header(HttpHeaders.RetryAfter, it.toString())
                    throw ApiException.RateLimited()
                }
                val body = call.boundedBody(InstitutionProvisionBody.serializer(), AUTH_BODY_MAX_BYTES)
                val proof = body.proof ?: throw ApiException.Forbidden("Scoped MFA proof is required")
                recordAudit(
                    actor.id,
                    "INSTITUTION_CREATE",
                    body.institutionId,
                    body.operationId,
                    "STARTED",
                )
                val result = service.create(actor, body.command(), proof)
                recordAudit(
                    actor.id,
                    "INSTITUTION_CREATE",
                    body.institutionId,
                    body.operationId,
                    "SUCCEEDED",
                )
                call.respond(HttpStatusCode.Created, ApiResponse(result, call.requestId()))
            }
        }
    }
}
