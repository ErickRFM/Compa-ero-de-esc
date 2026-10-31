package org.companerodeescuela.feature.admin

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.network.SessionRefreshCoordinator
import org.companerodeescuela.core.network.apiCall
import org.companerodeescuela.core.network.requireBody
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.AcademicGroupSummary
import org.companerodeescuela.shared.contracts.AccountLifecycleRequest
import org.companerodeescuela.shared.contracts.AdminUserSummary
import org.companerodeescuela.shared.contracts.AdministrationOverview
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.ApproveStaffRequest
import org.companerodeescuela.shared.contracts.InstitutionSummary
import org.companerodeescuela.shared.contracts.RevokeTutorAssignmentRequest
import org.companerodeescuela.shared.contracts.TutorAssignmentSummary
import org.companerodeescuela.shared.contracts.UserSummary

/** The existing native session owns all administrative requests; results are never demo counters. */
class AdminRepository(
    private val client: HttpClient,
    private val tokens: SessionTokenStore,
    private val refresh: SessionRefreshCoordinator = SessionRefreshCoordinator(client, tokens),
) {
    suspend fun academicDirectory(): Outcome<List<UserSummary>> = get("admin/accounts/academic-directory")

    suspend fun currentUser(): Outcome<UserSummary> = get("auth/me")

    suspend fun institutions(): Outcome<List<InstitutionSummary>> = get("institutions")

    suspend fun overview(): Outcome<AdministrationOverview> = get("admin/accounts")

    suspend fun approvalGroups(): Outcome<List<AcademicGroupSummary>> = get("admin/accounts/approval-groups")

    suspend fun assignments(): Outcome<List<TutorAssignmentSummary>> = get("tutoring/assignments")

    suspend fun approve(
        target: String,
        request: ApproveStaffRequest,
    ): Outcome<AdminUserSummary> =
        post(
            "admin/accounts/" + segment(target) + "/staff-approval",
            request,
        )

    suspend fun lifecycle(
        target: String,
        request: AccountLifecycleRequest,
    ): Outcome<AdminUserSummary> =
        post(
            "admin/accounts/" + segment(target) + "/lifecycle",
            request,
        )

    suspend fun revokeAssignment(
        id: String,
        request: RevokeTutorAssignmentRequest,
    ): Outcome<TutorAssignmentSummary> =
        authorized { token ->
            apiCall {
                client
                    .patch("tutoring/assignments/" + segment(id) + "/revoke") {
                        bearerAuth(token)
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }.requireBody<ApiResponse<TutorAssignmentSummary>>()
            }.map { it.data }
        }

    private suspend inline fun <reified T> get(path: String): Outcome<T> =
        authorized { token ->
            apiCall {
                client.get(path) { bearerAuth(token) }.requireBody<ApiResponse<T>>()
            }.map { it.data }
        }

    private suspend inline fun <reified T, reified B> post(
        path: String,
        body: B,
    ): Outcome<T> =
        authorized { token ->
            apiCall {
                client
                    .post(path) {
                        bearerAuth(token)
                        contentType(ContentType.Application.Json)
                        setBody(body)
                    }.requireBody<ApiResponse<T>>()
            }.map { it.data }
        }

    private suspend fun <T> authorized(action: suspend (String) -> Outcome<T>): Outcome<T> =
        when (val access = refresh.currentAccessToken()) {
            is Outcome.Failure -> access
            is Outcome.Success -> refresh.execute(access.value, action)
        }

    private fun segment(value: String) =
        java.net.URLEncoder
            .encode(value, "UTF-8")
            .replace("+", "%20")
}
