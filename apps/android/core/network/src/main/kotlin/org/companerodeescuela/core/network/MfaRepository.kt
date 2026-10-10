package org.companerodeescuela.core.network

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.*
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.core.security.SessionTokenInspector
import org.companerodeescuela.core.common.result.*
import org.companerodeescuela.shared.contracts.*

/** One MFA transport for account setup and command-bound privilege proofs. No secret storage. */
class MfaRepository(private val client:HttpClient,private val tokens:SessionTokenStore,private val refresh:SessionRefreshCoordinator=SessionRefreshCoordinator(client,tokens)) {
    /** Local UI ownership signal only; the server still authorizes every request. */
    fun observeOwner():Flow<String?> = tokens.observeAccessToken().map { token ->
        token?.let(SessionTokenInspector::inspect)?.let { it.sessionId?.let { session -> it.userId+":"+session+":"+it.roles.sortedBy { role -> role.name }.joinToString() } }
    }.catch { emit(null) }.distinctUntilChanged()
    suspend fun status():Outcome<MfaStatus> = authorized { token -> apiCall {
        client.get("auth/mfa/status") { bearerAuth(token) }.requireBody<ApiResponse<MfaStatus>>()
    }.map { it.data } }
    suspend fun enroll(request:MfaEnrollmentRequest):Outcome<MfaEnrollment> = post("auth/mfa/enrollment",request)
    suspend fun confirm(request:MfaConfirmationRequest):Outcome<MfaStatus> = post("auth/mfa/confirmation",request)
    suspend fun stepUp(request:MfaStepUpRequest):Outcome<StepUpProof> = post("auth/mfa/step-up",request)
    private suspend inline fun <reified T,reified B> post(path:String,body:B):Outcome<T> = authorized { token -> apiCall {
        client.post(path) { bearerAuth(token);contentType(ContentType.Application.Json);setBody(body) }.requireBody<ApiResponse<T>>()
    }.map { it.data } }
    private suspend fun <T> authorized(action:suspend(String)->Outcome<T>):Outcome<T> = when(val access=refresh.currentAccessToken()) {
        is Outcome.Failure -> access
        is Outcome.Success -> refresh.execute(access.value,action)
    }
}
