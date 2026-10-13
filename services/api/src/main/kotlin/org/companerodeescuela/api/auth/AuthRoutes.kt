package org.companerodeescuela.api.auth

import java.time.Clock
import kotlinx.coroutines.delay
import org.companerodeescuela.api.institutions.InstitutionRepository
import org.companerodeescuela.api.institutions.InMemoryInstitutionRepository
import org.companerodeescuela.api.mail.VerificationEmailGateway
import org.companerodeescuela.api.mail.UnavailableVerificationEmailGateway
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.identity.IdentityProvider
import org.companerodeescuela.api.plugins.clientAddress
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
    loginAttemptLimiter: LoginAttemptLimiter? = null,
    sessions: RefreshSessionRepository = InMemoryRefreshSessionRepository(),
    accounts: PlatformAccountRepository = InMemoryPlatformAccountRepository(),
    institutions: InstitutionRepository = InMemoryInstitutionRepository(),
    verificationEmail: VerificationEmailGateway = UnavailableVerificationEmailGateway,
    verificationClock: Clock = Clock.systemUTC(),
) {
    val tokenService = settings.jwtSecret?.let { AuthTokenService(settings) }
    val verification = AccountVerificationService(accounts, verificationEmail, verificationClock)
    val registrationLimiter = LoginAttemptLimiter()
    val registrationAddressLimiter = LoginAttemptLimiter(maxAttempts = 20)
    val verificationLimiter = LoginAttemptLimiter(maxAttempts = 8)
    val refreshLogoutLimiter = LoginAttemptLimiter(maxAttempts = 30, window = java.time.Duration.ofMinutes(5))
    val loginLimiter = loginAttemptLimiter ?: LoginAttemptLimiter(
        progressiveDelayBase = java.time.Duration.ofSeconds(settings.loginProgressiveDelayBaseSeconds),
        progressiveDelayMaximum = java.time.Duration.ofSeconds(settings.loginProgressiveDelayMaxSeconds),
    )

    route("/auth") {
        post("/login") {
            val service = tokenService?.let {
                AuthService(
                    identityProvider, it, sessions, accounts = accounts, institutions = institutions, verification = verification,
                    refreshRotationGrace = java.time.Duration.ofSeconds(settings.refreshRotationGraceSeconds),
                    maximumRefreshSessionAge = java.time.Duration.ofDays(settings.maximumRefreshSessionAgeDays),
                )
            }
                ?: throw ApiException.DependencyUnavailable(
                    "Authentication is not configured",
                )

            val request = runCatching { call.boundedBody(LoginRequest.serializer(), AUTH_BODY_MAX_BYTES) }
                .getOrElse {
                    throw ApiException.Validation("Invalid login request")
                }

            val clientAddress = call.clientAddress()
            val result = try {
                service.login(request)
            } catch (error: ApiException.Unauthorized) {
                val failure = loginLimiter.recordLoginFailure(request.username.trim(), clientAddress)
                if (!failure.progressiveDelay.isZero) delay(failure.progressiveDelay.toMillis())
                failure.retryAfterSeconds?.let { retryAfter ->
                    call.response.header(HttpHeaders.RetryAfter, retryAfter.toString())
                    throw ApiException.RateLimited()
                }
                throw error
            }
            loginLimiter.resetLogin(request.username.trim(), clientAddress)

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
            val clientAddress = call.clientAddress()
            registrationAddressLimiter.acquireAddress(clientAddress)?.let { retryAfter ->
                call.response.header(HttpHeaders.RetryAfter, retryAfter.toString())
                throw ApiException.RateLimited()
            }
            val request = runCatching { call.boundedBody(RegisterRequest.serializer(), AUTH_BODY_MAX_BYTES) }
                .getOrElse { throw ApiException.Validation("Invalid registration request") }
            registrationLimiter.acquire(request.email, clientAddress)?.let { retryAfter ->
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
            refreshLogoutLimiter.acquireAddress(call.clientAddress())?.let { retryAfter ->
                call.response.header(HttpHeaders.RetryAfter, retryAfter.toString())
                throw ApiException.RateLimited()
            }
            val service = tokenService?.let {
                AuthService(
                    identityProvider, it, sessions, accounts = accounts, institutions = institutions, verification = verification,
                    refreshRotationGrace = java.time.Duration.ofSeconds(settings.refreshRotationGraceSeconds),
                    maximumRefreshSessionAge = java.time.Duration.ofDays(settings.maximumRefreshSessionAgeDays),
                )
            }
                ?: throw ApiException.DependencyUnavailable("Authentication is not configured")
            val request = runCatching { call.boundedBody(RefreshSessionRequest.serializer(), AUTH_BODY_MAX_BYTES) }
                .getOrElse { throw ApiException.Unauthorized() }
            call.respond(
                ApiResponse(
                    data = service.refresh(request),
                    requestId = call.requestId(),
                ),
            )
        }

        post("/logout") {
            refreshLogoutLimiter.acquireAddress(call.clientAddress())?.let { retryAfter ->
                call.response.header(HttpHeaders.RetryAfter, retryAfter.toString())
                throw ApiException.RateLimited()
            }
            val service = tokenService?.let {
                AuthService(
                    identityProvider, it, sessions, accounts = accounts, institutions = institutions, verification = verification,
                    refreshRotationGrace = java.time.Duration.ofSeconds(settings.refreshRotationGraceSeconds),
                    maximumRefreshSessionAge = java.time.Duration.ofDays(settings.maximumRefreshSessionAgeDays),
                )
            }
                ?: throw ApiException.DependencyUnavailable("Authentication is not configured")
            val request = runCatching { call.boundedBody(RefreshSessionRequest.serializer(), AUTH_BODY_MAX_BYTES) }
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
                    if (call.verifiedSessionSource() != SessionIdentitySource.NATIVE) {
                        throw ApiException.Forbidden("A native account is required for email verification")
                    }
                    val user = tokenService.userFrom(call.principal<JWTPrincipal>()?.payload ?: throw ApiException.Unauthorized())
                    val result = verification.issue(user.id)
                    call.respond(ApiResponse(result, call.requestId()))
                }
                post("/verification/confirm") {
                    if (call.verifiedSessionSource() != SessionIdentitySource.NATIVE) {
                        throw ApiException.Forbidden("A native account is required for email verification")
                    }
                    val user = tokenService.userFrom(call.principal<JWTPrincipal>()?.payload ?: throw ApiException.Unauthorized())
                    verificationLimiter.acquire(user.id, call.clientAddress())?.let { retryAfter ->
                        call.response.header(HttpHeaders.RetryAfter, retryAfter.toString())
                        throw ApiException.RateLimited()
                    }
                    val request = runCatching { call.boundedBody(VerifyEmailRequest.serializer(), AUTH_BODY_MAX_BYTES) }
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
