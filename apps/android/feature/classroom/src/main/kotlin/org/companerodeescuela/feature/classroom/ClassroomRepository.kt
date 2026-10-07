package org.companerodeescuela.feature.classroom

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.network.SessionRefreshCoordinator
import org.companerodeescuela.core.network.apiCall
import org.companerodeescuela.core.network.requireBody
import org.companerodeescuela.core.network.requireUnit
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.ClassInvite
import org.companerodeescuela.shared.contracts.ClassroomSummary
import org.companerodeescuela.shared.contracts.CreateClassInviteRequest
import org.companerodeescuela.shared.contracts.CreateClassroomRequest
import org.companerodeescuela.shared.contracts.JoinClassInviteRequest
import org.companerodeescuela.shared.contracts.JoinClassInviteResponse

class ClassroomRepository(
    private val client: HttpClient,
    private val tokenStore: SessionTokenStore,
    private val refreshCoordinator: SessionRefreshCoordinator =
        SessionRefreshCoordinator(client, tokenStore),
) {
    suspend fun classrooms(): Outcome<List<ClassroomSummary>> =
        authorized { token ->
            apiCall {
                client.get("classrooms") { bearerAuth(token) }
                    .requireBody<ApiResponse<List<ClassroomSummary>>>()
            }.map { it.data }
        }

    suspend fun create(
        name: String,
        description: String?,
        room: String?,
        groupId: String,
        groupName: String,
        teacherId: String,
        teacherDisplayName: String,
    ): Outcome<ClassroomSummary> =
        authorized { token ->
            apiCall {
                client.post("classrooms") {
                    bearerAuth(token)
                    setBody(
                        CreateClassroomRequest(
                            name = name,
                            description = description,
                            room = room,
                            groupId = groupId,
                            groupName = groupName,
                            teacherId = teacherId,
                            teacherDisplayName = teacherDisplayName,
                        ),
                    )
                }.requireBody<ApiResponse<ClassroomSummary>>()
            }.map { it.data }
        }

    suspend fun join(code: String): Outcome<JoinClassInviteResponse> =
        authorized { token ->
            apiCall {
                client.post("classrooms/join") {
                    bearerAuth(token)
                    setBody(JoinClassInviteRequest(code = code))
                }.requireBody<ApiResponse<JoinClassInviteResponse>>()
            }.map { it.data }
        }

    suspend fun createInvite(
        classroomId: String,
        ttlMinutes: Int = 30,
    ): Outcome<ClassInvite> =
        authorized { token ->
            apiCall {
                client.post("classrooms/$classroomId/invites") {
                    bearerAuth(token)
                    setBody(CreateClassInviteRequest(ttlMinutes = ttlMinutes))
                }.requireBody<ApiResponse<ClassInvite>>()
            }.map { it.data }
        }

    suspend fun revokeInvite(
        classroomId: String,
        inviteId: String,
    ): Outcome<Unit> =
        authorized { token ->
            apiCall {
                client.delete("classrooms/$classroomId/invites/$inviteId") {
                    bearerAuth(token)
                }.requireUnit()
            }
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
