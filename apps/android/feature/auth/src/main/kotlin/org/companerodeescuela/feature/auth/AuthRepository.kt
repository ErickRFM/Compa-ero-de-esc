package org.companerodeescuela.feature.auth

import io.ktor.client.HttpClient
import io.ktor.client.request.contentType
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.network.apiCall
import org.companerodeescuela.core.network.requireBody
import org.companerodeescuela.core.security.SessionTokenInspector
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.LoginRequest
import org.companerodeescuela.shared.contracts.LoginResponse
import org.companerodeescuela.shared.contracts.UserSummary

class AuthRepository(
    private val client: HttpClient,
    private val tokenStore: SessionTokenStore,
) {
    suspend fun hasSession(): Boolean {
        val token = runCatching { tokenStore.readAccessToken() }.getOrNull()
        val usable = SessionTokenInspector.isUsable(token)
        if (!usable && token != null) {
            runCatching { tokenStore.clear() }
        }
        return usable
    }

    suspend fun login(
        username: String,
        password: String,
    ): Outcome<UserSummary> {
        val result = apiCall {
            client.post("auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(username = username, password = password))
            }.requireBody<ApiResponse<LoginResponse>>()
        }.map { it.data }

        return when (result) {
            is Outcome.Success -> {
                if (!SessionTokenInspector.isUsable(result.value.accessToken)) {
                    Outcome.Failure(
                        AppError.Serialization(
                            technicalDetail = "Login returned a missing, malformed, or expired access token",
                        ),
                    )
                } else {
                    try {
                        tokenStore.writeAccessToken(result.value.accessToken)
                        Outcome.Success(result.value.user)
                    } catch (error: Exception) {
                        Outcome.Failure(
                            AppError.Unknown(
                                technicalDetail = "Could not persist session: " + error::class.simpleName,
                            ),
                        )
                    }
                }
            }
            is Outcome.Failure -> result
        }
    }

    suspend fun logout() {
        tokenStore.clear()
    }
}
