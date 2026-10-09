package org.companerodeescuela.feature.auth

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.network.ApiEnvironment
import org.companerodeescuela.core.network.createApiClient
import org.companerodeescuela.core.security.RefreshSessionCredentials
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary

class AuthCurrentUserTest {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun restoringTheViewModelRequiresCurrentServerRoles() = runTest {
        kotlinx.coroutines.Dispatchers.setMain(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler))
        val store = MeSessionStore()
        val client = createApiClient(ApiEnvironment("https://example.test/", "test"), MockEngine {
            respond("""{"data":{"id":"user-1","displayName":"Elena","email":"e@example.edu","roles":["teacher","tutor"],"active":true}}""",
                HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        })
        val vm = SessionViewModel(AuthRepository(client, store), store)
        try {
            val state = vm.state.first { !it.checking }
            assertEquals(setOf(UserRole.TEACHER, UserRole.TUTOR), state.roles)
            kotlin.test.assertTrue(state.authenticated)
        } finally {
            vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin()
            client.close()
            kotlinx.coroutines.Dispatchers.resetMain()
        }
    }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun restoringTheViewModelRejectsARevokedUnexpiredToken() = runTest {
        kotlinx.coroutines.Dispatchers.setMain(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler))
        val store = MeSessionStore()
        val client = createApiClient(ApiEnvironment("https://example.test/", "test"), MockEngine { respond("{}", HttpStatusCode.Unauthorized) })
        val vm = SessionViewModel(AuthRepository(client, store), store)
        try {
            val state = vm.state.first { !it.checking }
            kotlin.test.assertFalse(state.authenticated)
            assertEquals(emptySet(), state.roles)
        } finally {
            vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin()
            client.close()
            kotlinx.coroutines.Dispatchers.resetMain()
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun aDelayedAuthorityResponseCannotReopenTheUiDuringLogout() = runTest {
        val dispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val meStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val meGate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val logoutGate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val store = MeSessionStore()
        val engine = MockEngine.create {
            this.dispatcher = dispatcher
            addHandler { request ->
                if (request.url.encodedPath == "/auth/me") {
                    meStarted.complete(Unit)
                    meGate.await()
                    respond("""{"data":{"id":"user-1","displayName":"Elena","email":"e@example.edu","roles":["teacher"],"active":true}}""",
                        HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                } else {
                    logoutGate.await()
                    respond("", HttpStatusCode.NoContent)
                }
            }
        }
        val client = createApiClient(ApiEnvironment("https://example.test/", "test"), engine)
        val vm = SessionViewModel(AuthRepository(client, store), store)
        try {
            meStarted.await()
            vm.logout()
            meGate.complete(Unit)
            runCurrent()
            kotlin.test.assertFalse(vm.state.value.authenticated)
            assertEquals(emptySet(), vm.state.value.roles)
        } finally {
            logoutGate.complete(Unit)
            vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin()
            client.close()
            kotlinx.coroutines.Dispatchers.resetMain()
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun aDelayedCredentialCompletionCannotOverwriteNewerServerAuthority() = runTest {
        val dispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val writeGate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val fixtureToken = MeSessionStore().readAccessToken()!!
        val access = MutableStateFlow<String?>(null)
        val store = object : SessionTokenStore by MeSessionStore() {
            override fun observeAccessToken(): Flow<String?> = access
            override suspend fun readAccessToken(): String? = access.value
            override suspend fun clear() { access.value = null }
            override suspend fun writeSession(accessToken: String, sessionId: String, refreshToken: String) {
                access.value = accessToken
                writeGate.await()
            }
        }
        val engine = MockEngine.create {
            this.dispatcher = dispatcher
            addHandler { request ->
                val response = if (request.url.encodedPath == "/auth/login") {
                    """{"data":{"accessToken":"$fixtureToken","expiresAtEpochSeconds":4102444800,"sessionId":"session-1","refreshToken":"fixture-refresh","user":{"id":"user-1","displayName":"Elena","email":"e@example.edu","roles":["student"],"active":true}}}"""
                } else {
                    """{"data":{"id":"user-1","displayName":"Elena","email":"e@example.edu","roles":["teacher","tutor"],"active":true}}"""
                }
                respond(response, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
            }
        }
        val client = createApiClient(ApiEnvironment("https://example.test/", "test"), engine)
        val vm = SessionViewModel(AuthRepository(client, store), store)
        try {
            vm.login("qa.alumno", "fixture-password")
            runCurrent()
            kotlin.test.assertTrue(vm.state.value.authenticated, "Valid login must reach live authority")
            val live = vm.state.value
            assertEquals(setOf(UserRole.TEACHER, UserRole.TUTOR), live.roles)
            writeGate.complete(Unit)
            runCurrent()
            assertEquals(setOf(UserRole.TEACHER, UserRole.TUTOR), vm.state.value.roles)
        } finally {
            writeGate.complete(Unit)
            vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin()
            client.close()
            kotlinx.coroutines.Dispatchers.resetMain()
        }
    }

    @Test fun currentRolesAreReadFromTheProtectedBackendContract() = runTest {
        val store = MeSessionStore()
        val client = createApiClient(ApiEnvironment("https://example.test/", "test"), MockEngine { request ->
            assertEquals("/auth/me", request.url.encodedPath)
            assertEquals("Bearer " + store.readAccessToken(), request.headers[HttpHeaders.Authorization])
            respond("""{"data":{"id":"user-1","displayName":"Elena","email":"e@example.edu","roles":["teacher","tutor"],"active":true}}""",
                HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        })
        client.use {
            val result = assertIs<Outcome.Success<UserSummary>>(AuthRepository(client, store).currentUser())
            assertEquals(setOf(UserRole.TEACHER, UserRole.TUTOR), result.value.roles)
        }
    }
    @Test fun revokedSessionCannotBeRestoredFromAnUnexpiredLocalToken() = runTest {
        val store = MeSessionStore()
        val client = createApiClient(ApiEnvironment("https://example.test/", "test"), MockEngine {
            respond("{}", HttpStatusCode.Unauthorized)
        })
        client.use {
            val result = assertIs<Outcome.Failure>(AuthRepository(client, store).currentUser())
            assertEquals(401, assertIs<AppError.Http>(result.error).status)
            assertNull(store.readAccessToken())
        }
    }
    @Test fun unavailableAuthorityDoesNotGrantAccessAndKeepsCredentialsForRetry() = runTest {
        val store = MeSessionStore()
        val oldToken = store.readAccessToken()
        val client = createApiClient(ApiEnvironment("https://example.test/", "test"), MockEngine {
            respond("{}", HttpStatusCode.ServiceUnavailable)
        })
        client.use {
            val result = assertIs<Outcome.Failure>(AuthRepository(client, store).currentUser())
            assertEquals(503, assertIs<AppError.Http>(result.error).status)
            assertEquals(oldToken, store.readAccessToken())
        }
    }
}

private class MeSessionStore : SessionTokenStore {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val access = MutableStateFlow<String?>(encoder.encodeToString("{}".toByteArray()) + "." +
        encoder.encodeToString("""{"sub":"user-1","session_id":"session-1","exp":4102444800,"roles":["STUDENT"]}""".toByteArray()) + ".test")
    private var refresh: RefreshSessionCredentials? = RefreshSessionCredentials("session-1", "fixture-refresh")
    override fun observeAccessToken(): Flow<String?> = access
    override suspend fun readAccessToken(): String? = access.value
    override suspend fun readRefreshSession(): RefreshSessionCredentials? = refresh
    override suspend fun writeAccessToken(token: String) { access.value = token }
    override suspend fun writeSession(accessToken: String, sessionId: String, refreshToken: String) {
        access.value = accessToken
        refresh = RefreshSessionCredentials(sessionId, refreshToken)
    }
    override suspend fun clear() { access.value = null; refresh = null }
}
