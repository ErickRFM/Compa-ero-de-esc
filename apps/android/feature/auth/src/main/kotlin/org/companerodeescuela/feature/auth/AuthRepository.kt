package org.companerodeescuela.feature.auth

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.network.apiCall
import org.companerodeescuela.core.network.requireBody
import org.companerodeescuela.core.network.requireUnit
import org.companerodeescuela.core.network.SessionRefreshCoordinator
import org.companerodeescuela.core.security.SessionTokenInspector
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.LoginRequest
import org.companerodeescuela.shared.contracts.LoginResponse
import org.companerodeescuela.shared.contracts.RegisterRequest
import org.companerodeescuela.shared.contracts.RegistrationAccountType
import org.companerodeescuela.shared.contracts.UserSummary

import java.time.Clock

class AuthRepository(
    private val client: HttpClient,
    private val tokenStore: SessionTokenStore,
    private val clock: Clock = Clock.systemUTC(),
    private val refreshCoordinator: SessionRefreshCoordinator =
        SessionRefreshCoordinator(client, tokenStore, clock),
) {
    /** Restored sessions must pass the server's live identity/generation authority. */
    suspend fun currentUser(): Outcome<UserSummary> {
        val current = refreshCoordinator.currentAccessToken()
        if (current is Outcome.Failure) return current
        return refreshCoordinator.execute((current as Outcome.Success).value) { token ->
            apiCall {
                client.get("auth/me") {
                    header(HttpHeaders.Authorization, "Bearer " + token)
                }.requireBody<ApiResponse<UserSummary>>()
            }.map { it.data }
        }
    }

    suspend fun hasSession(): Boolean {
        return refreshCoordinator.currentAccessToken() is Outcome.Success
    }

    suspend fun refreshSession(): Outcome<String> = refreshCoordinator.refreshSession()

    suspend fun login(
        username: String,
        password: String,
    ): Outcome<UserSummary> {
        val result = apiCall {
            client.post("auth/login") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(LoginRequest(username = username, password = password))
            }.requireBody<ApiResponse<LoginResponse>>()
        }.map { it.data }
        return persistSession(result)
    }

    suspend fun register(displayName: String, email: String, password: String, accountType: RegistrationAccountType): Outcome<UserSummary> =
        register(RegisterRequest(displayName, email, password, accountType))

    suspend fun register(request: RegisterRequest): Outcome<UserSummary> = persistSession(apiCall {
        client.post("auth/register") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(request)
        }.requireBody<ApiResponse<LoginResponse>>()
    }.map { it.data })

    suspend fun institutions(): Outcome<List<org.companerodeescuela.shared.contracts.InstitutionSummary>> = apiCall {
        client.get("institutions").requireBody<ApiResponse<List<org.companerodeescuela.shared.contracts.InstitutionSummary>>>()
    }.map { it.data }

    suspend fun confirmVerification(token: String): Outcome<UserSummary> = persistSession(protectedCall { access ->
        apiCall {
            client.post("auth/verification/confirm") {
                header(HttpHeaders.Authorization, "Bearer " + access)
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(org.companerodeescuela.shared.contracts.VerifyEmailRequest(token))
            }.requireBody<ApiResponse<LoginResponse>>()
        }.map { it.data }
    })

    suspend fun resendVerification(): Outcome<org.companerodeescuela.shared.contracts.VerificationDeliveryReceipt> = protectedCall { access ->
        apiCall {
            client.post("auth/verification/resend") {
                header(HttpHeaders.Authorization, "Bearer " + access)
            }.requireBody<ApiResponse<org.companerodeescuela.shared.contracts.VerificationDeliveryReceipt>>()
        }.map { it.data }
    }

    private suspend fun <T> protectedCall(action: suspend (String) -> Outcome<T>): Outcome<T> {
        return when (val current = refreshCoordinator.currentAccessToken()) {
            is Outcome.Failure -> current
            is Outcome.Success -> refreshCoordinator.execute(current.value, action)
        }
    }

    private suspend fun persistSession(
        result: Outcome<LoginResponse>,
    ): Outcome<UserSummary> = when (result) {
        is Outcome.Success -> {
            if (!SessionTokenInspector.isUsable(result.value.accessToken)) {
                Outcome.Failure(
                    AppError.Serialization(
                        technicalDetail = "Authentication returned a missing, malformed, or expired access token",
                    ),
                )
            } else {
                try {
                    tokenStore.writeSession(
                        accessToken = result.value.accessToken,
                        sessionId = result.value.sessionId,
                        refreshToken = result.value.refreshToken,
                    )
                    Outcome.Success(result.value.user)
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Outcome.Failure(
                        AppError.Storage(
                            technicalDetail = "Could not persist session: " + error::class.simpleName,
                        ),
                    )
                }
            }
        }
        is Outcome.Failure -> result
    }

    suspend fun logout() {
        try {
            val refreshSession = runCatching { tokenStore.readRefreshSession() }.getOrNull()
            if (refreshSession != null) {
                apiCall<Unit> {
                    client.post("auth/logout") {
                        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        setBody(
                            org.companerodeescuela.shared.contracts.RefreshSessionRequest(
                                sessionId = refreshSession.sessionId,
                                refreshToken = refreshSession.refreshToken,
                            ),
                        )
                    }.requireUnit()
                }
            }
        } finally {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { tokenStore.clear() }
        }
    }
}
