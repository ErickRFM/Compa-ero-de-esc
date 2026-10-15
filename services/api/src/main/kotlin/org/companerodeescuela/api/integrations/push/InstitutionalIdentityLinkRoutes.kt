package org.companerodeescuela.api.integrations.push

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
import kotlinx.coroutines.CancellationException
import org.companerodeescuela.api.auth.AuthTokenService
import org.companerodeescuela.api.auth.LoginAttemptLimiter
import org.companerodeescuela.api.auth.PlatformAccountRepository
import org.companerodeescuela.api.auth.RefreshSessionRepository
import org.companerodeescuela.api.auth.boundedAuthRequest
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.identity.IdentityProvider
import org.companerodeescuela.api.plugins.requestId
import org.companerodeescuela.api.security.NativeActorResolver
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.InstitutionalIdentityLinkRequest

fun Route.institutionalIdentityLinkRoutes(
    settings: ApiSettings,
    identity: IdentityProvider,
    sessions: RefreshSessionRepository,
    accounts: PlatformAccountRepository,
    service: InstitutionalIdentityLinkService,
) {
    if (!settings.hasAuthentication) return

    val resolver = NativeActorResolver(accounts, sessions, AuthTokenService(settings), identity)
    val limiter = LoginAttemptLimiter(maxAttempts = 20)
    authenticate(AuthTokenService.PROVIDER_NAME) {
        route("/admin/accounts") {
            post("/institutional-identity-links") {
                call.response.header(HttpHeaders.CacheControl, "no-store")
                val principal = call.principal<JWTPrincipal>() ?: throw ApiException.Unauthorized()
                val actor = resolver.resolve(principal.payload)
                limiter.acquire(actor.id, "native-administration")?.let { retryAfter ->
                    call.response.header(HttpHeaders.RetryAfter, retryAfter.toString())
                    throw ApiException.RateLimited()
                }
                val request = call.identityLinkRequest()
                call.respond(
                    HttpStatusCode.Created,
                    ApiResponse(service.link(actor.toUserSummary(), request), call.requestId()),
                )
            }
        }
    }
}

private suspend fun ApplicationCall.identityLinkRequest(): InstitutionalIdentityLinkRequest =
    try {
        boundedAuthRequest(InstitutionalIdentityLinkRequest.serializer())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        throw ApiException.Validation("Invalid institutional identity link request")
    }
