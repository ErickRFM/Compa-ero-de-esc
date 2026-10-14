package org.companerodeescuela.api.security

import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.DeserializationStrategy
import org.companerodeescuela.api.auth.*
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.identity.IdentityProvider
import org.companerodeescuela.api.plugins.requestId
import org.companerodeescuela.api.plugins.clientAddress
import org.companerodeescuela.shared.contracts.*

fun Route.mfaRoutes(settings:ApiSettings,identity:IdentityProvider,sessions:RefreshSessionRepository,accounts:PlatformAccountRepository,mfa:MfaService) {
    val tokens=settings.jwtSecret?.let { AuthTokenService(settings) }
    if(tokens==null) {
        route("/auth/mfa") {
            get("/status") { throw ApiException.DependencyUnavailable("Authentication is not configured") }
            for(path in listOf("/enrollment","/confirmation","/step-up")) post(path) { throw ApiException.DependencyUnavailable("Authentication is not configured") }
        }
        return
    }
    val resolver=NativeActorResolver(accounts,sessions,tokens,identity)
    val credentialLimiter=LoginAttemptLimiter(maxAttempts=5)
    val addressLimiter=LoginAttemptLimiter(maxAttempts=30)
    suspend fun ApplicationCall.actor(credentialOperation:String?=null):PlatformAccount {
        response.header(HttpHeaders.CacheControl,"no-store")
        val actor=resolver.resolve(principal<JWTPrincipal>()?.payload ?: throw ApiException.Unauthorized())
        if(credentialOperation!=null) {
            // Account counter is independent of source address; rotating addresses cannot evade it.
            val retry=credentialLimiter.acquire(actor.id+":"+credentialOperation,"mfa")
                ?: addressLimiter.acquire("mfa-credentials",clientAddress())
            if(retry!=null) { response.header(HttpHeaders.RetryAfter,retry.toString());throw ApiException.RateLimited() }
        }
        return actor
    }
    authenticate(AuthTokenService.PROVIDER_NAME) {
        route("/auth/mfa") {
            get("/status") { call.respond(ApiResponse(mfa.status(call.actor()),call.requestId())) }
            post("/enrollment") {
                val actor=call.actor("enrollment");val request=call.mfaRequest(MfaEnrollmentRequest.serializer())
                call.respond(ApiResponse(mfa.beginEnrollment(actor,request.password),call.requestId()))
            }
            post("/confirmation") {
                val actor=call.actor("confirmation");val request=call.mfaRequest(MfaConfirmationRequest.serializer())
                call.respond(ApiResponse(mfa.confirmEnrollment(actor,request.enrollmentId,request.otp),call.requestId()))
            }
            post("/step-up") {
                val actor=call.actor("step-up");val request=call.mfaRequest(MfaStepUpRequest.serializer())
                call.respond(ApiResponse(mfa.issueStepUp(actor,request.password,request.otp,request.action,request.resourceId,request.operationId,request.payloadDigest),call.requestId()))
            }
        }
    }
}
private suspend fun <T> ApplicationCall.mfaRequest(serializer:DeserializationStrategy<T>):T = try {
    boundedBody(serializer, AUTH_BODY_MAX_BYTES)
} catch(cancelled:CancellationException) { throw cancelled }
catch(_:Exception) { throw ApiException.Validation("Invalid MFA request") }
