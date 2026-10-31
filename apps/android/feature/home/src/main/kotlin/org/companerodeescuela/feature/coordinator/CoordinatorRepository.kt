package org.companerodeescuela.feature.coordinator

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.network.SessionRefreshCoordinator
import org.companerodeescuela.core.network.apiCall
import org.companerodeescuela.core.network.requireBody
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.CoordinationOverview

class CoordinatorRepository(
    private val client: HttpClient,
    private val tokenStore: SessionTokenStore,
    private val refreshCoordinator: SessionRefreshCoordinator = SessionRefreshCoordinator(client, tokenStore),
) {
    suspend fun overview(): Outcome<CoordinationOverview> {
        val token =
            when (val current = refreshCoordinator.currentAccessToken()) {
                is Outcome.Success -> current.value
                is Outcome.Failure -> return current
            }
        return refreshCoordinator.execute(token) { authorized ->
            apiCall {
                client
                    .get("coordination/overview") { bearerAuth(authorized) }
                    .requireBody<ApiResponse<CoordinationOverview>>()
            }.map { it.data }
        }
    }
}
