package org.companerodeescuela.feature.auth

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.companerodeescuela.core.network.ApiEnvironment
import org.companerodeescuela.core.network.createApiClient
import org.companerodeescuela.core.security.RefreshSessionCredentials
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.UserRole

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val viewModels = mutableListOf<SessionViewModel>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() = runBlocking {
        // Finish session observers before removing their Main dispatcher.
        viewModels.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
        Dispatchers.resetMain()
    }

    @Test
    fun `expired restored session refreshes access and current roles`() = runTest {
        val currentEpoch = System.currentTimeMillis() / 1000
        val refreshedToken = token(
            subject = "teacher-1",
            sessionId = "session-1",
            expiresAt = currentEpoch + 3600,
            roles = listOf("TEACHER"),
        )
        val tokenStore = FakeSessionStore(
            accessToken = token(
                subject = "teacher-1",
                sessionId = "session-1",
                expiresAt = currentEpoch - 100,
                roles = listOf("STUDENT"),
            ),
            refreshSession = RefreshSessionCredentials("session-1", "refresh-before"),
        )
        val clock = java.time.Clock.fixed(
            java.time.Instant.ofEpochSecond(currentEpoch),
            java.time.ZoneId.of("UTC"),
        )
        val repository = AuthRepository(
            client = createApiClient(
                ApiEnvironment("https://example.test/", "test"),
                MockEngine { request ->
                    respond(
                        content = if (request.url.encodedPath == "/auth/me") {
                            """{"data":{"id":"teacher-1","displayName":"Elena Ríos","email":"elena@example.edu","roles":["teacher"],"active":true}}"""
                        } else refreshResponse(refreshedToken, currentEpoch + 3600),
                        status = HttpStatusCode.OK,
                        headers = headersOf(
                            HttpHeaders.ContentType,
                            ContentType.Application.Json.toString(),
                        ),
                    )
                },
            ),
            tokenStore = tokenStore,
            clock = clock,
        )

        val viewModel = SessionViewModel(
            repository = repository,
            tokenStore = tokenStore,
            clock = clock,
        ).also(viewModels::add)
        val state = viewModel.state.first { !it.checking }

        assertTrue(
            state.authenticated,
            "state: $state",
        )
        assertEquals("teacher-1", state.userId)
        assertEquals(setOf(UserRole.TEACHER), state.roles)
        assertEquals(refreshedToken, tokenStore.accessToken.value)
        assertEquals("refresh-after", tokenStore.refreshSession?.refreshToken)
    }

    @Test
    fun `refresh failure is shown as session notice not credential error`() = runTest {
        val currentEpoch = System.currentTimeMillis() / 1000
        val tokenStore = FakeSessionStore(
            accessToken = token(
                subject = "student-1",
                sessionId = "session-1",
                expiresAt = currentEpoch - 100,
                roles = listOf("STUDENT"),
            ),
            refreshSession = RefreshSessionCredentials("session-1", "refresh-before"),
        )
        val clock = java.time.Clock.fixed(
            java.time.Instant.ofEpochSecond(currentEpoch),
            java.time.ZoneId.of("UTC"),
        )
        val repository = AuthRepository(
            client = createApiClient(
                ApiEnvironment("https://example.test/", "test"),
                MockEngine {
                    respond(
                        content = """{"error":"temporarily unavailable"}""",
                        status = HttpStatusCode.ServiceUnavailable,
                        headers = headersOf(
                            HttpHeaders.ContentType,
                            ContentType.Application.Json.toString(),
                        ),
                    )
                },
            ),
            tokenStore = tokenStore,
            clock = clock,
        )

        val viewModel = SessionViewModel(
            repository = repository,
            tokenStore = tokenStore,
            clock = clock,
        ).also(viewModels::add)
        val state = viewModel.state.first { !it.checking }

        assertTrue(!state.authenticated)
        assertNull(state.errorMessage)
        assertEquals(
            "No pudimos renovar tu sesión. Revisa tu conexión e inténtalo de nuevo.",
            state.noticeMessage,
        )
    }

    private class FakeSessionStore(
        accessToken: String?,
        var refreshSession: RefreshSessionCredentials?,
    ) : SessionTokenStore {
        val accessToken = MutableStateFlow(accessToken)

        override suspend fun readAccessToken(): String? = accessToken.value
        override suspend fun readRefreshSession(): RefreshSessionCredentials? = refreshSession
        override fun observeAccessToken(): Flow<String?> = accessToken

        override suspend fun writeAccessToken(token: String) {
            accessToken.value = token
        }

        override suspend fun writeSession(
            accessToken: String,
            sessionId: String,
            refreshToken: String,
        ) {
            this.accessToken.value = accessToken
            refreshSession = RefreshSessionCredentials(sessionId, refreshToken)
        }

        override suspend fun clear() {
            accessToken.value = null
            refreshSession = null
        }
    }

    private fun token(
        subject: String,
        sessionId: String,
        expiresAt: Long,
        roles: List<String>,
    ): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val encodedRoles = roles.joinToString(",") { "\"$it\"" }
        val payload = """{"sub":"$subject","session_id":"$sessionId","exp":$expiresAt,"roles":[$encodedRoles]}"""
        return listOf(
            encoder.encodeToString("{}".toByteArray()),
            encoder.encodeToString(payload.toByteArray()),
            "test",
        ).joinToString(".")
    }

    private fun refreshResponse(accessToken: String, expiresAt: Long): String = """
        {
          "data": {
            "accessToken": "$accessToken",
            "expiresAtEpochSeconds": $expiresAt,
            "sessionId": "session-1",
            "refreshToken": "refresh-after",
            "user": {
              "id": "teacher-1",
              "displayName": "Elena Ríos",
              "email": "elena@example.edu",
              "roles": ["teacher"],
              "active": true
            }
          }
        }
    """.trimIndent()
}