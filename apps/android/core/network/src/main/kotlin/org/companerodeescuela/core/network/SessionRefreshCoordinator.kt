package org.companerodeescuela.core.network

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import java.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.security.RefreshSessionCredentials
import org.companerodeescuela.core.security.SessionTokenInspector
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.LoginResponse
import org.companerodeescuela.shared.contracts.RefreshSessionRequest

/** Serializes refreshes and retries each protected operation no more than once. */
class SessionRefreshCoordinator(
    private val client: HttpClient,
    private val tokenStore: SessionTokenStore,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val refreshMutex = Mutex()

    suspend fun currentAccessToken(): Outcome<String> {
        val current = runCatching { tokenStore.readAccessToken() }.getOrNull()
            ?.takeIf(String::isNotBlank)
        current
            ?.takeIf { SessionTokenInspector.isUsable(it, clock) }
            ?.let { return Outcome.Success(it) }

        val oldSessionId = current?.let(SessionTokenInspector::inspect)?.sessionId
            ?: tokenStore.readRefreshSession()?.sessionId
        return refreshAccessToken(current, oldSessionId)
    }

    suspend fun refreshSession(): Outcome<String> {
        val current = runCatching { tokenStore.readAccessToken() }.getOrNull()
        val sessionId = current?.let(SessionTokenInspector::inspect)?.sessionId
            ?: tokenStore.readRefreshSession()?.sessionId
        return refreshAccessToken(current, sessionId)
    }

    suspend fun <T> execute(
        initialAccessToken: String,
        request: suspend (String) -> Outcome<T>,
    ): Outcome<T> {
        val claims = SessionTokenInspector.inspect(initialAccessToken)
        val credentials = tokenStore.readRefreshSession()
        val expectedSessionId = claims?.sessionId ?: credentials?.sessionId

        val firstToken = if (SessionTokenInspector.isUsable(initialAccessToken, clock)) {
            initialAccessToken
        } else {
            when (val refreshed = refreshAfterUnauthorized(initialAccessToken, expectedSessionId)) {
                is Outcome.Success -> refreshed.value
                is Outcome.Failure -> return refreshed
            }
        }

        val first = request(firstToken)
        if (!first.isUnauthorized()) return first

        val nextToken = when (val refreshed = refreshAfterUnauthorized(firstToken, expectedSessionId)) {
            is Outcome.Success -> refreshed.value
            is Outcome.Failure -> return refreshed
        }
        val retried = request(nextToken)
        if (retried.isUnauthorized()) clearIfSessionMatches(expectedSessionId)
        return retried
    }

    private suspend fun refreshAfterUnauthorized(
        failedAccessToken: String,
        expectedSessionId: String?,
    ): Outcome<String> = refreshMutex.withLock {
        val credentials = tokenStore.readRefreshSession() ?: run {
            clearIfSessionMatches(expectedSessionId)
            return@withLock unauthorized()
        }
        if (expectedSessionId != null && credentials.sessionId != expectedSessionId) {
            return@withLock unauthorized()
        }

        val latestAccess = tokenStore.readAccessToken()
        val latestClaims = latestAccess?.let(SessionTokenInspector::inspect)
        if (
            latestAccess != null &&
            latestAccess != failedAccessToken &&
            SessionTokenInspector.isUsable(latestAccess, clock) &&
            latestClaims?.sessionId == credentials.sessionId
        ) {
            return@withLock Outcome.Success(latestAccess)
        }

        when (val refresh = performRefresh(credentials)) {
            is Outcome.Success -> Outcome.Success(refresh.value.accessToken)
            is Outcome.Failure -> {
                val refreshError = refresh.error
                if (refreshError is AppError.Http && refreshError.status == 401) {
                    clearIfSessionMatches(credentials.sessionId)
                }
                Outcome.Failure(refreshError)
            }
        }
    }

    private suspend fun refreshAccessToken(
        previousAccessToken: String?,
        expectedSessionId: String?,
    ): Outcome<String> = refreshMutex.withLock {
        val credentials = runCatching { tokenStore.readRefreshSession() }.getOrNull()
            ?: return@withLock unauthorized()
        if (expectedSessionId != null && credentials.sessionId != expectedSessionId) {
            return@withLock unauthorized()
        }
        val latestAccess = runCatching { tokenStore.readAccessToken() }.getOrNull()
        val latestClaims = latestAccess?.let(SessionTokenInspector::inspect)
        if (
            latestAccess != null &&
            latestAccess != previousAccessToken &&
            SessionTokenInspector.isUsable(latestAccess, clock) &&
            latestClaims?.sessionId == credentials.sessionId
        ) {
            return@withLock Outcome.Success(latestAccess)
        }
        when (val refreshed = performRefresh(credentials)) {
            is Outcome.Success -> Outcome.Success(refreshed.value.accessToken)
            is Outcome.Failure -> {
                val refreshError = refreshed.error
                if (refreshError is AppError.Http && refreshError.status == 401) {
                    clearIfSessionMatches(credentials.sessionId)
                }
                Outcome.Failure(refreshError)
            }
        }
    }

    private suspend fun performRefresh(
        credentials: RefreshSessionCredentials,
    ): Outcome<LoginResponse> {
        val result = apiCall {
            client.post("auth/refresh") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(
                    RefreshSessionRequest(
                        sessionId = credentials.sessionId,
                        refreshToken = credentials.refreshToken,
                    ),
                )
            }.requireBody<ApiResponse<LoginResponse>>()
        }.map { it.data }

        return when (result) {
            is Outcome.Failure -> result
            is Outcome.Success -> {
                val response = result.value
                val claims = SessionTokenInspector.inspect(response.accessToken)
                if (
                    response.sessionId != credentials.sessionId ||
                    claims?.sessionId != credentials.sessionId ||
                    !SessionTokenInspector.isUsable(response.accessToken, clock)
                ) {
                    clearIfSessionMatches(credentials.sessionId)
                    Outcome.Failure(
                        AppError.Serialization(
                            technicalDetail = "Refresh returned an invalid or mismatched session token",
                        ),
                    )
                } else {
                    try {
                        tokenStore.writeSession(
                            accessToken = response.accessToken,
                            sessionId = response.sessionId,
                            refreshToken = response.refreshToken,
                        )
                        Outcome.Success(response)
                    } catch (error: Exception) {
                        Outcome.Failure(
                            AppError.Storage(
                                technicalDetail = "Could not persist refreshed session: " +
                                    error::class.simpleName,
                            ),
                        )
                    }
                }
            }
        }
    }

    private suspend fun clearIfSessionMatches(sessionId: String?) {
        val stored = tokenStore.readRefreshSession()
        if (sessionId == null || stored?.sessionId == sessionId) {
            runCatching { tokenStore.clear() }
        }
    }

    private fun <T> Outcome<T>.isUnauthorized(): Boolean {
        val requestError = (this as? Outcome.Failure)?.error
        return requestError is AppError.Http && requestError.status == 401
    }

    private fun unauthorized(): Outcome.Failure = Outcome.Failure(AppError.Http(status = 401))
}