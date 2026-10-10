package org.companerodeescuela.feature.tutoring

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.contentType
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import javax.inject.Inject
import javax.inject.Singleton
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.network.SessionRefreshCoordinator
import org.companerodeescuela.core.network.apiCall
import org.companerodeescuela.core.network.requireBody
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.AppointRepresentativeRequest
import org.companerodeescuela.shared.contracts.GroupRepresentativeAssignment
import org.companerodeescuela.shared.contracts.GroupRepresentativesOverview
import org.companerodeescuela.shared.contracts.RepresentativePosition

/**
 * Every representative request is scoped to the *current* native session.
 * The shared refresh coordinator rejects missing/revoked sessions and retries
 * a 401 at most once; the HTTP client never sends an anonymous operation.
 */
@Singleton
class GroupRepresentativeRepository @Inject constructor(
    private val client: HttpClient,
    private val refresh: SessionRefreshCoordinator,
) {
    suspend fun getOverview(groupId: String): Outcome<GroupRepresentativesOverview> =
        authorized { token ->
            apiCall {
                client.get("academic/groups/${segment(groupId)}/representatives") {
                    bearerAuth(token)
                }.requireBody<ApiResponse<GroupRepresentativesOverview>>()
            }.map { it.data }
        }

    suspend fun appoint(
        groupId: String,
        studentUserId: String,
        position: RepresentativePosition,
    ): Outcome<GroupRepresentativeAssignment> = authorized { token ->
        apiCall {
            client.post("academic/groups/${segment(groupId)}/representatives") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(AppointRepresentativeRequest(studentUserId = studentUserId, position = position))
            }.requireBody<ApiResponse<GroupRepresentativeAssignment>>()
        }.map { it.data }
    }

    suspend fun getMyAssignments(): Outcome<List<GroupRepresentativeAssignment>> = authorized { token ->
        apiCall {
            client.get("representatives/me") {
                bearerAuth(token)
            }.requireBody<ApiResponse<List<GroupRepresentativeAssignment>>>()
        }.map { it.data }
    }

    suspend fun accept(id: String): Outcome<GroupRepresentativeAssignment> = authorized { token ->
        apiCall {
            client.post("representatives/${segment(id)}/accept") {
                bearerAuth(token)
            }.requireBody<ApiResponse<GroupRepresentativeAssignment>>()
        }.map { it.data }
    }

    suspend fun decline(id: String): Outcome<GroupRepresentativeAssignment> = authorized { token ->
        apiCall {
            client.post("representatives/${segment(id)}/decline") {
                bearerAuth(token)
            }.requireBody<ApiResponse<GroupRepresentativeAssignment>>()
        }.map { it.data }
    }

    suspend fun revoke(id: String): Outcome<GroupRepresentativeAssignment> = authorized { token ->
        apiCall {
            client.patch("representatives/${segment(id)}/revoke") {
                bearerAuth(token)
            }.requireBody<ApiResponse<GroupRepresentativeAssignment>>()
        }.map { it.data }
    }

    private suspend fun <T> authorized(request: suspend (String) -> Outcome<T>): Outcome<T> =
        when (val access = refresh.currentAccessToken()) {
            is Outcome.Failure -> access
            is Outcome.Success -> refresh.execute(access.value, request)
        }

    private fun segment(raw: String): String =
        java.net.URLEncoder.encode(raw, "UTF-8").replace("+", "%20")
}
