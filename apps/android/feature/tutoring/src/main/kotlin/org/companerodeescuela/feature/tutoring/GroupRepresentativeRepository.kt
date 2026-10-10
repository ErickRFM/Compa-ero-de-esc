package org.companerodeescuela.feature.tutoring

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import javax.inject.Inject
import javax.inject.Singleton
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.network.apiCall
import org.companerodeescuela.core.network.requireBody
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.AppointRepresentativeRequest
import org.companerodeescuela.shared.contracts.GroupRepresentativeAssignment
import org.companerodeescuela.shared.contracts.GroupRepresentativesOverview

@Singleton
class GroupRepresentativeRepository @Inject constructor(
    private val client: HttpClient,
) {
    suspend fun getOverview(groupId: String): Outcome<GroupRepresentativesOverview> = apiCall {
        client.get("academic/groups/$groupId/representatives") {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }.requireBody<ApiResponse<GroupRepresentativesOverview>>()
    }.map { it.data }

    suspend fun appoint(
        groupId: String,
        studentUserId: String,
        position: org.companerodeescuela.shared.contracts.RepresentativePosition,
    ): Outcome<GroupRepresentativeAssignment> = apiCall {
        client.post("academic/groups/$groupId/representatives") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                AppointRepresentativeRequest(
                    studentUserId = studentUserId,
                    position = position,
                ),
            )
        }.requireBody<ApiResponse<GroupRepresentativeAssignment>>()
    }.map { it.data }

    suspend fun getMyAssignments(): Outcome<List<GroupRepresentativeAssignment>> = apiCall {
        client.get("representatives/me") {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }.requireBody<ApiResponse<List<GroupRepresentativeAssignment>>>()
    }.map { it.data }

    suspend fun accept(id: String): Outcome<GroupRepresentativeAssignment> = apiCall {
        client.post("representatives/$id/accept") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        }.requireBody<ApiResponse<GroupRepresentativeAssignment>>()
    }.map { it.data }

    suspend fun decline(id: String): Outcome<GroupRepresentativeAssignment> = apiCall {
        client.post("representatives/$id/decline") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        }.requireBody<ApiResponse<GroupRepresentativeAssignment>>()
    }.map { it.data }

    suspend fun revoke(id: String): Outcome<GroupRepresentativeAssignment> = apiCall {
        client.patch("representatives/$id/revoke") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        }.requireBody<ApiResponse<GroupRepresentativeAssignment>>()
    }.map { it.data }
}
