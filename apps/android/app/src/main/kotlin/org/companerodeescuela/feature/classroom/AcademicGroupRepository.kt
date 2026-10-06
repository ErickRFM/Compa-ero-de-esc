package org.companerodeescuela.feature.classroom

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.network.SessionRefreshCoordinator
import org.companerodeescuela.core.network.apiCall
import org.companerodeescuela.core.network.requireBody
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.AcademicGroupMembership
import org.companerodeescuela.shared.contracts.AcademicGroupSummary
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.AssignAcademicGroupMemberRequest
import org.companerodeescuela.shared.contracts.CreateAcademicGroupRequest

class AcademicGroupRepository(
    private val client: HttpClient,
    private val tokenStore: SessionTokenStore,
    private val refreshCoordinator: SessionRefreshCoordinator =
        SessionRefreshCoordinator(client, tokenStore),
) {
    suspend fun groups(): Outcome<List<AcademicGroupSummary>> =
        authorized { token ->
            apiCall {
                client.get("academic/groups") { bearerAuth(token) }
                    .requireBody<ApiResponse<List<AcademicGroupSummary>>>()
            }.map { it.data }
        }

    suspend fun create(
        id: String,
        name: String,
    ): Outcome<AcademicGroupSummary> =
        authorized { token ->
            apiCall {
                client.post("academic/groups") {
                    bearerAuth(token)
                    setBody(CreateAcademicGroupRequest(id = id, name = name))
                }.requireBody<ApiResponse<AcademicGroupSummary>>()
            }.map { it.data }
        }

    suspend fun assignMember(
        groupId: String,
        userId: String,
    ): Outcome<AcademicGroupMembership> =
        authorized { token ->
            apiCall {
                client.post("academic/groups/$groupId/members") {
                    bearerAuth(token)
                    setBody(AssignAcademicGroupMemberRequest(userId = userId))
                }.requireBody<ApiResponse<AcademicGroupMembership>>()
            }.map { it.data }
        }

    private suspend fun <T> authorized(
        block: suspend (String) -> Outcome<T>,
    ): Outcome<T> {
        val token = when (val result = refreshCoordinator.currentAccessToken()) {
            is Outcome.Success -> result.value
            is Outcome.Failure -> return result
        }
        return refreshCoordinator.execute(token, block)
    }
}
