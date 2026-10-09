package org.companerodeescuela.feature.tutoring

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.contentType
import io.ktor.http.ContentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.network.SessionRefreshCoordinator
import org.companerodeescuela.core.network.apiCall
import org.companerodeescuela.core.network.requireBody
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.core.security.SessionTokenInspector
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.ExcuseRequestSummary
import org.companerodeescuela.shared.contracts.ExcuseStatus
import org.companerodeescuela.shared.contracts.ReviewExcuseRequest
import org.companerodeescuela.shared.contracts.TutorScopeSummary
import org.companerodeescuela.shared.contracts.TutorStudentSummary
import org.companerodeescuela.shared.contracts.TutorCaseSummary
import org.companerodeescuela.shared.contracts.CreateTutorCaseRequest
import org.companerodeescuela.shared.contracts.AddTutorCaseNoteRequest
import org.companerodeescuela.shared.contracts.UserRole

class TutorRepository(
    private val client: HttpClient,
    private val tokenStore: SessionTokenStore,
    private val refreshCoordinator: SessionRefreshCoordinator =
        SessionRefreshCoordinator(client, tokenStore),
) {
    /** Local ownership signal for clearing UI data, never server authorization. */
    fun observeScopeIdentity(): Flow<String?> = tokenStore.observeAccessToken()
        .map { token ->
            token?.let(SessionTokenInspector::inspect)?.takeIf { UserRole.TUTOR in it.roles }
                ?.let { claims -> claims.sessionId?.let { "${claims.userId}:$it:${claims.roles.sortedBy { role -> role.name }}" } }
        }
        .catch { emit(null) }
        .distinctUntilChanged()

    suspend fun scope(): Outcome<TutorScopeSummary> = authorized { token ->
        apiCall {
            client.get("tutoring/me") { bearerAuth(token) }
                .requireBody<ApiResponse<TutorScopeSummary>>()
        }.map { it.data }
    }

    suspend fun requests(): Outcome<List<ExcuseRequestSummary>> = authorized { token ->
        apiCall {
            client.get("excuses") { bearerAuth(token) }
                .requireBody<ApiResponse<List<ExcuseRequestSummary>>>()
        }.map { it.data }
    }

    suspend fun review(id: String, approved: Boolean, comment: String?): Outcome<ExcuseRequestSummary> =
        authorized { token ->
            apiCall {
                client.patch("excuses/$id/review") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(
                        ReviewExcuseRequest(
                            status = if (approved) ExcuseStatus.APPROVED else ExcuseStatus.REJECTED,
                            comment = comment?.trim()?.takeIf(String::isNotBlank),
                        ),
                    )
                }.requireBody<ApiResponse<ExcuseRequestSummary>>()
            }.map { it.data }
        }

    suspend fun students(groupId: String): Outcome<List<TutorStudentSummary>> = authorized { token ->
        apiCall {
            client.get("tutoring/groups/$groupId/students") { bearerAuth(token) }
                .requireBody<ApiResponse<List<TutorStudentSummary>>>()
        }.map { it.data }
    }

    suspend fun cases(): Outcome<List<TutorCaseSummary>> = authorized { token ->
        apiCall {
            client.get("tutoring/cases") { bearerAuth(token) }
                .requireBody<ApiResponse<List<TutorCaseSummary>>>()
        }.map { it.data }
    }

    suspend fun createCase(request: CreateTutorCaseRequest): Outcome<TutorCaseSummary> = authorized { token ->
        apiCall {
            client.post("tutoring/cases") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(request)
            }.requireBody<ApiResponse<TutorCaseSummary>>()
        }.map { it.data }
    }

    suspend fun addNote(caseId: String, request: AddTutorCaseNoteRequest): Outcome<TutorCaseSummary> =
        authorized { token ->
            apiCall {
                client.post("tutoring/cases/$caseId/notes") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }.requireBody<ApiResponse<TutorCaseSummary>>()
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
