package org.companerodeescuela.feature.grading

import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.network.SessionRefreshCoordinator
import org.companerodeescuela.core.network.apiCall
import org.companerodeescuela.core.network.requireBody
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.GradeSyncRequest
import org.companerodeescuela.shared.contracts.GradeSyncResult

class GradebookRepository(
    private val client: HttpClient,
    private val tokenStore: SessionTokenStore,
    private val refreshCoordinator: SessionRefreshCoordinator =
        SessionRefreshCoordinator(client, tokenStore),
) {
    suspend fun sync(request: GradeSyncRequest): Outcome<GradeSyncResult> =
        authorized { token ->
            apiCall {
                client.post("grading/sync") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }.requireBody<ApiResponse<GradeSyncResult>>()
            }.map { it.data }
        }

    private suspend fun <T> authorized(block: suspend (String) -> Outcome<T>): Outcome<T> {
        val token = when (val result = refreshCoordinator.currentAccessToken()) {
            is Outcome.Success -> result.value
            is Outcome.Failure -> return result
        }
        return refreshCoordinator.execute(token, block)
    }
}
