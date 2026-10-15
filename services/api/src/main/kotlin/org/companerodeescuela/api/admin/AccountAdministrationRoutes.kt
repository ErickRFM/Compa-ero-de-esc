package org.companerodeescuela.api.admin

import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.AuthenticationStrategy
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.DeserializationStrategy
import org.companerodeescuela.api.auth.*
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.integrations.identity.IdentityProvider
import org.companerodeescuela.api.integrations.push.InstitutionalIdentityLinkService
import org.companerodeescuela.api.security.NativeActorResolver
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.plugins.requestId
import org.companerodeescuela.shared.contracts.*

fun Route.accountAdministrationRoutes(
    settings: ApiSettings,
    identity: IdentityProvider,
    sessions: RefreshSessionRepository,
    accounts: PlatformAccountRepository,
    service: AccountAdministrationService,
    identityLinkService: InstitutionalIdentityLinkService,
) {
    if(!settings.hasAuthentication) return
    val tokens=AuthTokenService(settings);val resolver=NativeActorResolver(accounts,sessions,tokens,identity)
    val limiter=LoginAttemptLimiter(maxAttempts=20)
    suspend fun ApplicationCall.actor(mutation:Boolean=false):PlatformAccount {
        response.header(HttpHeaders.CacheControl,"no-store")
        val current=resolver.resolve(principal<JWTPrincipal>()?.payload ?: throw ApiException.Unauthorized())
        if(mutation) limiter.acquire(current.id,"native-administration")?.let { retry -> response.header(HttpHeaders.RetryAfter,retry.toString());throw ApiException.RateLimited() }
        return current
    }
    authenticate(
        AuthTokenService.ACTIVE_PROVIDER_NAME,
        AuthTokenService.PENDING_APPROVAL_PROVIDER_NAME,
        strategy = AuthenticationStrategy.FirstSuccessful,
    ) {
        route("/auth/staff-applications") {
            get {
                val actor=call.actor()
                if(actor.emailVerifiedAt==null) throw ApiException.Forbidden("Verified native identity is required")
                call.respond(ApiResponse(actor.staffApplications(),call.requestId()))
            }
            post {
                val actor=call.actor(true);val request=call.administrationRequest(TutorApplicationRequest.serializer())
                call.respond(HttpStatusCode.Created,ApiResponse(service.requestTutor(actor,request),call.requestId()))
            }
        }
    }
    authenticate(AuthTokenService.PROVIDER_NAME) {
        route("/admin/accounts") {
            get { call.respond(ApiResponse(service.overview(call.actor()),call.requestId())) }
            get("/academic-directory") { call.respond(ApiResponse(service.academicDirectory(call.actor()),call.requestId())) }
            get("/approval-groups") { call.respond(ApiResponse(service.approvalGroups(call.actor()),call.requestId())) }
            post("/institutional-identity-links") {
                val actor = call.actor(true)
                val request = call.administrationRequest(InstitutionalIdentityLinkRequest.serializer())
                call.response.header(HttpHeaders.CacheControl, "no-store")
                call.respond(
                    HttpStatusCode.Created,
                    ApiResponse(
                        identityLinkService.link(actor.toUserSummary(), request),
                        call.requestId(),
                    ),
                )
            }
            post("/{accountId}/staff-approval") {
                val actor=call.actor(true);val request=call.administrationRequest(ApproveStaffRequest.serializer())
                val id=call.parameters["accountId"] ?: throw ApiException.Validation("Account is required")
                call.respond(ApiResponse(service.approveStaff(actor,id,request),call.requestId()))
            }
            post("/{accountId}/lifecycle") {
                val actor=call.actor(true);val request=call.administrationRequest(AccountLifecycleRequest.serializer())
                val id=call.parameters["accountId"] ?: throw ApiException.Validation("Account is required")
                call.respond(ApiResponse(service.lifecycle(actor,id,request),call.requestId()))
            }
        }
    }
}
private suspend fun <T> ApplicationCall.administrationRequest(serializer:DeserializationStrategy<T>):T = try {
    boundedAuthRequest(serializer)
} catch(cancelled:CancellationException) { throw cancelled }
catch(_:Exception) { throw ApiException.Validation("Invalid administration request") }

