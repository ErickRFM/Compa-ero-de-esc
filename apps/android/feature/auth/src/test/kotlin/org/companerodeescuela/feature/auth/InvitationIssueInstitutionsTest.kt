package org.companerodeescuela.feature.auth

import androidx.lifecycle.viewModelScope
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.network.ApiEnvironment
import org.companerodeescuela.core.network.MfaRepository
import org.companerodeescuela.core.network.createApiClient
import org.companerodeescuela.core.security.SessionTokenStore
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InvitationIssueInstitutionsTest {
    private class TokenStore : SessionTokenStore {
        private val token = MutableStateFlow<String?>(null)

        override suspend fun readAccessToken(): String? = token.value

        override suspend fun writeAccessToken(token: String) {
            this.token.value = token
        }

        override suspend fun clear() {
            token.value = null
        }

        override fun observeAccessToken(): Flow<String?> = token
    }

    @Test
    fun `institution load HTTP error is surfaced and recovers on explicit retry`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val attempts = AtomicInteger(0)
            val store = TokenStore()
            val client =
                createApiClient(
                    ApiEnvironment("https://example.test/", "test"),
                    MockEngine {
                        val attempt = attempts.incrementAndGet()
                        if (attempt == 1) {
                            respond(
                                """{"code":"dependency_unavailable","message":"Mongo down","requestId":"req-inst-503"}""",
                                HttpStatusCode.ServiceUnavailable,
                                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                            )
                        } else {
                            respond(
                                """{"data":[{"id":"UPTLAX","displayName":"UPTLAX"}],"requestId":"req-inst-200"}""",
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                            )
                        }
                    },
                )
            try {
                val vm = InvitationIssueViewModel(AuthRepository(client, store), MfaRepository(client, store))
                try {
                    val failedState =
                        withContext(Dispatchers.Default) {
                            withTimeout(5000) {
                                vm.state.first { !it.institutionsLoading && it.institutionsFailure != null }
                            }
                        }
                    val httpError = assertIs<AppError.Http>(failedState.institutionsFailure)
                    assertEquals(503, httpError.status)
                    assertEquals("req-inst-503", httpError.requestId)
                    val message = assertNotNull(failedState.institutionsErrorMessage)
                    assertTrue(message.contains("req-inst-503"))
                    assertTrue(failedState.institutions.isEmpty())

                    vm.loadInstitutions()
                    val recovered =
                        withContext(Dispatchers.Default) {
                            withTimeout(5000) {
                                vm.state.first { !it.institutionsLoading && it.institutions.isNotEmpty() }
                            }
                        }
                    assertNull(recovered.institutionsFailure)
                    assertNull(recovered.institutionsErrorMessage)
                    assertEquals("UPTLAX", recovered.institutions.single().id)
                } finally {
                    vm.viewModelScope.cancel()
                }
            } finally {
                client.close()
                Dispatchers.resetMain()
            }
        }
}
