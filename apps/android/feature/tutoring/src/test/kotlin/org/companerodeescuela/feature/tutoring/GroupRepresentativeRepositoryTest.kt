package org.companerodeescuela.feature.tutoring

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.network.ApiEnvironment
import org.companerodeescuela.core.network.SessionRefreshCoordinator
import org.companerodeescuela.core.network.createApiClient
import org.companerodeescuela.core.security.RefreshSessionCredentials
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.GroupRepresentativeAssignment
import org.companerodeescuela.shared.contracts.GroupRepresentativesOverview
import org.companerodeescuela.shared.contracts.LoginResponse
import org.companerodeescuela.shared.contracts.RepresentativePosition
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GroupRepresentativeRepositoryTest {
    private val assignment =
        GroupRepresentativeAssignment(
            id = "inv-1",
            academicGroupId = "9A",
            academicGroupName = "9 A",
            studentUserId = "student-1",
            position = RepresentativePosition.CHIEF,
            appointedBy = "tutor-1",
            appointedAtEpochSeconds = 1L,
        )
    private val overview = GroupRepresentativesOverview("9A", "9 A", pendingInvitations = listOf(assignment))

    @Test fun `all representative operations use the current bearer session`() =
        runTest {
            val store = Store(token("tutor-1", "session-1"))
            val seen = mutableListOf<String>()
            val client =
                createApiClient(
                    ApiEnvironment("https://example.test/", "test"),
                    MockEngine { request ->
                        assertEquals("Bearer ${store.value.value}", request.headers[HttpHeaders.Authorization])
                        val path = request.url.encodedPath
                        seen += path
                        val data =
                            when {
                                path == "/academic/groups/9A/representatives" && request.method.value == "GET" ->
                                    Json.encodeToString(ApiResponse(overview))
                                path == "/representatives/me" -> Json.encodeToString(ApiResponse(listOf(assignment)))
                                else -> Json.encodeToString(ApiResponse(assignment))
                            }
                        respond(data, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                    },
                )
            try {
                val repo = GroupRepresentativeRepository(client, SessionRefreshCoordinator(client, store))
                assertIs<Outcome.Success<*>>(repo.getOverview("9A"))
                assertIs<Outcome.Success<*>>(repo.appoint("9A", "student-1", RepresentativePosition.CHIEF))
                assertIs<Outcome.Success<*>>(repo.getMyAssignments())
                assertIs<Outcome.Success<*>>(repo.accept("inv-1"))
                assertIs<Outcome.Success<*>>(repo.decline("inv-1"))
                assertIs<Outcome.Success<*>>(repo.revoke("inv-1"))
                assertEquals(
                    listOf(
                        "/academic/groups/9A/representatives",
                        "/academic/groups/9A/representatives",
                        "/representatives/me",
                        "/representatives/inv-1/accept",
                        "/representatives/inv-1/decline",
                        "/representatives/inv-1/revoke",
                    ),
                    seen,
                )
            } finally {
                client.close()
            }
        }

    @Test fun `a missing session cannot contact the representatives API`() =
        runTest {
            val store = Store(null)
            var called = 0
            val client =
                createApiClient(
                    ApiEnvironment("https://example.test/", "test"),
                    MockEngine {
                        called++
                        respond("{}", HttpStatusCode.OK)
                    },
                )
            try {
                val result = GroupRepresentativeRepository(client, SessionRefreshCoordinator(client, store)).getMyAssignments()
                assertIs<Outcome.Failure>(result)
                assertEquals(401, (result.error as AppError.Http).status)
                assertEquals(0, called)
            } finally {
                client.close()
            }
        }

    @Test fun `401 refreshes once and retries using the rotated JWT`() =
        runTest {
            val old = token("student-1", "session-1")
            val rotated = token("student-1", "session-1", nonce = "new")
            val store = Store(old, RefreshSessionCredentials("session-1", "refresh-1"))
            val seen = mutableListOf<String>()
            val client =
                createApiClient(
                    ApiEnvironment("https://example.test/", "test"),
                    MockEngine { request ->
                        val path = request.url.encodedPath
                        seen += path
                        when {
                            path == "/auth/refresh" ->
                                respond(
                                    Json.encodeToString(
                                        ApiResponse(
                                            LoginResponse(
                                                accessToken = rotated,
                                                expiresAtEpochSeconds = System.currentTimeMillis() / 1000 + 3600,
                                                sessionId = "session-1",
                                                refreshToken = "refresh-2",
                                                user = UserSummary("student-1", "Student", roles = setOf(UserRole.STUDENT)),
                                            ),
                                        ),
                                    ),
                                    HttpStatusCode.OK,
                                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                                )
                            request.headers[HttpHeaders.Authorization] == "Bearer $old" ->
                                respond(
                                    """{"code":"unauthorized","message":"expired"}""",
                                    HttpStatusCode.Unauthorized,
                                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                                )
                            else -> {
                                assertEquals("Bearer $rotated", request.headers[HttpHeaders.Authorization])
                                respond(
                                    Json.encodeToString(ApiResponse(listOf(assignment))),
                                    HttpStatusCode.OK,
                                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                                )
                            }
                        }
                    },
                )
            try {
                val result = GroupRepresentativeRepository(client, SessionRefreshCoordinator(client, store)).getMyAssignments()
                assertIs<Outcome.Success<*>>(result)
                assertEquals(listOf("/representatives/me", "/auth/refresh", "/representatives/me"), seen)
                assertEquals(rotated, store.value.value)
            } finally {
                client.close()
            }
        }

    private class Store(
        access: String?,
        private var credentials: RefreshSessionCredentials? = null,
    ) : SessionTokenStore {
        val value = MutableStateFlow(access)

        override suspend fun readAccessToken(): String? = value.value

        override suspend fun writeAccessToken(token: String) {
            value.value = token
        }

        override suspend fun readRefreshSession(): RefreshSessionCredentials? = credentials

        override suspend fun writeSession(
            accessToken: String,
            sessionId: String,
            refreshToken: String,
        ) {
            value.value = accessToken
            credentials = RefreshSessionCredentials(sessionId, refreshToken)
        }

        override suspend fun clear() {
            value.value = null
            credentials = null
        }

        override fun observeAccessToken() = value
    }

    private fun token(
        owner: String,
        session: String,
        nonce: String = "old",
    ): String {
        val exp = System.currentTimeMillis() / 1000 + 3600
        val payload = """{"sub":"$owner","session_id":"$session","exp":$exp,"roles":["STUDENT"],"nonce":"$nonce"}"""
        return "e30." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray()) + ".test"
    }
}
