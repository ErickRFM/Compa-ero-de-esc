package org.companerodeescuela.api.auth

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
) {
    val tokenService = settings.jwtSecret?.let { AuthTokenService(settings) }

    route("/auth") {
        post("/login") {
            val service = tokenService?.let { AuthService(identityProvider, it) }
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

        if (tokenService == null) {
            get("/me") {
                throw ApiException.DependencyUnavailable(
                    "Authentication is not configured",
                )
            }
        } else {
            authenticate(AuthTokenService.PROVIDER_NAME) {
                get("/me") {
                    val principal = call.principal<JWTPrincipal>()
                        ?: throw ApiException.Unauthorized()
                    call.respond(
                        ApiResponse(
                            data = tokenService.userFrom(principal.payload),
                            requestId = call.requestId(),
                        ),
                    )
                }
            }
        }
    }
}
