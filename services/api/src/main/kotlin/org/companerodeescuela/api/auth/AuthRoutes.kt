package org.companerodeescuela.api.auth

import java.time.Clock
import org.companerodeescuela.api.institutions.InstitutionRepository
import org.companerodeescuela.api.institutions.InMemoryInstitutionRepository
import org.companerodeescuela.api.mail.VerificationEmailGateway
import org.companerodeescuela.api.mail.UnavailableVerificationEmailGateway
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.identity.IdentityProvider
import org.companerodeescuela.api.plugins.requestId
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.LoginRequest
import org.companerodeescuela.shared.contracts.RefreshSessionRequest
import org.companerodeescuela.shared.contracts.VerifyEmailRequest
import org.companerodeescuela.shared.contracts.RegisterRequest

/**
 * Platform authentication endpoints.
 *
 * When JWT is not configured (allowed only for local foundation work), the
 * routes fail explicitly instead of silently minting an insecure token.
 */
fun Route.authRoutes(
    settings: ApiSettings,
    identityProvider: IdentityProvider,
    loginAttemptLimiter: LoginAttemptLimiter = LoginAttemptLimiter(),
    sessions: RefreshSessionRepository = InMemoryRefreshSessionRepository(),
    accounts: PlatformAccountRepository = InMemoryPlatformAccountRepository(),
    institutions: InstitutionRepository = InMemoryInstitutionRepository(),
    verificationEmail: VerificationEmailGateway = UnavailableVerificationEmailGateway,
    verificationClock: Clock = Clock.systemUTC(),
) {
    val tokenService = settings.jwtSecret?.let { AuthTokenService(settings) }
    val verification = AccountVerificationService(accounts, verificationEmail, verificationClock)
    val registrationLimiter = LoginAttemptLimiter()
    val verificationLimiter = LoginAttemptLimiter(maxAttempts = 8)

    route("/auth") {
        post("/login") {
            val service = tokenService?.let {
                AuthService(identityProvider, it, sessions, accounts = accounts, institutions = institutions, verification = verification)
            }
                ?: throw ApiException.DependencyUnavailable(
                    "Authentication is not configured",
                )

            val request = runCatching { call.receive<LoginRequest>() }
                .getOrElse {
                    throw ApiException.Validation("Invalid login request")
                }

            val clientAddress = call.request.local.remoteAddress
            loginAttemptLimiter.acquire(request.username, clientAddress)?.let { retryAfter ->
                call.response.header(HttpHeaders.RetryAfter, retryAfter.toString())
                throw ApiException.RateLimited()
            }

            val result = service.login(request)
            loginAttemptLimiter.reset(request.username, clientAddress)

            call.respond(
                status = HttpStatusCode.OK,
                message = ApiResponse(
                    data = result,
                    requestId = call.requestId(),
                ),
            )
        }

        post("/register") {
            val service = tokenService?.let {
                AuthService(identityProvider, it, sessions, accounts = accounts, institutions = institutions, verification = verification)
            } ?: throw ApiException.DependencyUnavailable(
                "Authentication is not configured",
            )
            val request = runCatching { call.boundedAuthRequest(RegisterRequest.serializer()) }
                .getOrElse { throw ApiException.Validation("Invalid registration request") }
            registrationLimiter.acquire(request.email, call.request.local.remoteAddress)?.let { retryAfter ->
                call.response.header(HttpHeaders.RetryAfter, retryAfter.toString())
                throw ApiException.RateLimited()
            }
            call.respond(
                status = HttpStatusCode.Created,
                message = ApiResponse(
                    data = service.register(request),
                    requestId = call.requestId(),
                ),
            )
        }

        post("/refresh") {
            val service = tokenService?.let {
                AuthService(identityProvider, it, sessions, accounts = accounts, institutions = institutions, verification = verification)
            }
                ?: throw ApiException.DependencyUnavailable("Authentication is not configured")
            val request = runCatching { call.receive<RefreshSessionRequest>() }
                .getOrElse { throw ApiException.Unauthorized() }
            call.respond(
                ApiResponse(
                    data = service.refresh(request),
                    requestId = call.requestId(),
                ),
            )
        }

        post("/logout") {
            val service = tokenService?.let {
                AuthService(identityProvider, it, sessions, accounts = accounts, institutions = institutions, verification = verification)
            }
                ?: throw ApiException.DependencyUnavailable("Authentication is not configured")
            val request = runCatching { call.receive<RefreshSessionRequest>() }
                .getOrElse { throw ApiException.Unauthorized() }
            service.logout(request)
            call.respond(HttpStatusCode.NoContent)
        }

        if (tokenService == null) {
            get("/me") {
                throw ApiException.DependencyUnavailable(
                    "Authentication is not configured",
                )
            }
        } else {
            authenticate(AuthTokenService.PROVIDER_NAME) {
                post("/verification/resend") {
                    val user = tokenService.userFrom(call.principal<JWTPrincipal>()?.payload ?: throw ApiException.Unauthorized())
                    val result = verification.issue(user.id)
                    call.respond(ApiResponse(result, call.requestId()))
                }
                post("/verification/confirm") {
                    val user = tokenService.userFrom(call.principal<JWTPrincipal>()?.payload ?: throw ApiException.Unauthorized())
                    verificationLimiter.acquire(user.id, call.request.local.remoteAddress)?.let { retryAfter ->
                        call.response.header(HttpHeaders.RetryAfter, retryAfter.toString())
                        throw ApiException.RateLimited()
                    }
                    val request = runCatching { call.boundedAuthRequest(VerifyEmailRequest.serializer()) }
                        .getOrElse { throw ApiException.Validation("Invalid email verification request") }
                    val service = AuthService(identityProvider, tokenService, sessions, accounts = accounts,
                        institutions = institutions, verification = verification)
                    call.respond(ApiResponse(service.verifyEmail(user.id, request.token), call.requestId()))
                }
                get("/me") {
                    val payload = call.principal<JWTPrincipal>()?.payload ?: throw ApiException.Unauthorized()
                    val session = tokenService.sessionIdFrom(payload)?.let { authenticationDependency { sessions.find(it) } }
                        ?: throw ApiException.Unauthorized()
                    val live = PlatformSessionAuthority(accounts, identityProvider).currentUser(session) ?: throw ApiException.Unauthorized()
                    val claimed = tokenService.userFrom(payload)
                    if (live.id != claimed.id || live.authRevision != claimed.authRevision || live.roles != claimed.roles ||
                        live.accountStatus != claimed.accountStatus || live.institutionId != claimed.institutionId) throw ApiException.Unauthorized()
                    call.respond(ApiResponse(live, call.requestId()))
                }
            }
        }
    }
}
