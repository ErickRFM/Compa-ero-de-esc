package org.companerodeescuela.feature.auth

import androidx.lifecycle.viewModelScope
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.companerodeescuela.core.network.ApiEnvironment
import org.companerodeescuela.core.network.createApiClient
import org.companerodeescuela.core.security.RefreshSessionCredentials
import org.companerodeescuela.core.security.SessionTokenStore

@OptIn(ExperimentalCoroutinesApi::class)
class SessionConcurrencyTest {
    @Test fun twoImmediateClicksSendOnlyOneLoginRequest() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var calls = 0
        val client = createApiClient(ApiEnvironment("https://example.test/", "test"), MockEngine {
            calls++
            respond("{}", HttpStatusCode.Unauthorized)
        })
        val store = EmptySessionStore()
        val vm = SessionViewModel(AuthRepository(client, store), store)
        try {
            runCurrent()
            vm.login("student", "wrong")
            val submittingImmediately = vm.state.value.submitting
            vm.login("student", "wrong")
            assertTrue(submittingImmediately)
            vm.state.first { it.errorMessage != null }
            assertEquals(1, calls)
            assertFalse(vm.state.value.authenticated)
        } finally {
            vm.viewModelScope.cancel()
            client.close()
            Dispatchers.resetMain()
        }
    }
}

private class EmptySessionStore : SessionTokenStore {
    private val access = MutableStateFlow<String?>(null)
    override fun observeAccessToken(): Flow<String?> = access
    override suspend fun readAccessToken(): String? = access.value
    override suspend fun readRefreshSession(): RefreshSessionCredentials? = null
    override suspend fun writeAccessToken(token: String) { access.value = token }
    override suspend fun writeSession(accessToken: String, sessionId: String, refreshToken: String) { access.value = accessToken }
    override suspend fun clear() { access.value = null }
}
