package org.companerodeescuela.feature.auth

import androidx.lifecycle.viewModelScope
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.companerodeescuela.core.common.result.*
import org.companerodeescuela.core.network.*
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.*
import java.util.Base64
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class StaffApplicationViewModelTest {
    private class Store : SessionTokenStore {
        val token = MutableStateFlow<String?>(jwt("teacher"))

        override suspend fun readAccessToken() = token.value

        override suspend fun writeAccessToken(token: String) {
            this.token.value = token
        }

        override suspend fun clear() {
            token.value = null
        }

        override fun observeAccessToken(): Flow<String?> = token
    }

    private class Fixture {
        val store = Store()
        val profile =
            UserSummary(
                "teacher",
                "Actual Teacher",
                roles = setOf(UserRole.TEACHER),
                authRevision = 7,
                institutionId = "campus",
                emailVerified = true,
            )
        var status = HttpStatusCode.OK
        var firstPost = HttpStatusCode.OK
        var gate: CompletableDeferred<Unit>? = null
        val started = CompletableDeferred<Unit>()
        val requests = mutableListOf<TutorApplicationRequest>()
        val application =
            StaffApplication(
                "application",
                StaffRole.TUTOR,
                "campus",
                "QA-ID",
                preferredGroup = "Group preference",
                requestedAtEpochSeconds = 1,
            )
        val client =
            createApiClient(
                ApiEnvironment("https://example.test/", "test"),
                MockEngine { request ->
                    var responseStatus = status
                    val body =
                        when (request.url.encodedPath) {
                            "/auth/me" -> Json.encodeToString(ApiResponse(profile))
                            "/auth/staff-applications" ->
                                if (request.method ==
                                    HttpMethod.Get
                                ) {
                                    Json.encodeToString(ApiResponse(emptyList<StaffApplication>()))
                                } else {
                                    requests += Json.decodeFromString<TutorApplicationRequest>((request.body as TextContent).text)
                                    started.complete(Unit)
                                    gate?.let { withContext(NonCancellable) { it.await() } }
                                    if (requests.size == 1) responseStatus = firstPost
                                    Json.encodeToString(ApiResponse(application))
                                }
                            else -> error("Unexpected endpoint")
                        }
                    respond(
                        if (responseStatus ==
                            HttpStatusCode.OK
                        ) {
                            body
                        } else {
                            "{}"
                        },
                        responseStatus,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                },
            )
        val repo = AuthRepository(client, store)
        val vm = StaffApplicationViewModel(repo, MfaRepository(client, store))

        suspend fun await(predicate: (StaffApplicationUiState) -> Boolean) =
            withContext(Dispatchers.Default) {
                withTimeout(5000) { vm.state.first(predicate) }
            }

        suspend fun ready() {
            await { !it.loading }
            assertEquals(profile, vm.state.value.user)
        }

        fun close() {
            gate?.complete(Unit)
            vm.viewModelScope.cancel()
            client.close()
        }
    }

    @Test fun `teacher tutor request uses actual profile revision and remains pending without granting local role`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val f = Fixture()
            try {
                f.ready()
                val original = f.store.token.value
                f.vm.submit(" QA-ID ", " Group preference ")
                f.await { !it.working && it.submitted }
                val request = f.requests.single()
                assertEquals(7, request.expectedAuthRevision)
                assertEquals("campus", request.institutionId)
                assertEquals("QA-ID", request.identityReference)
                assertEquals("Group preference", request.preferredGroup)
                assertEquals(listOf(f.application), f.vm.state.value.applications)
                assertEquals(
                    StaffApplicationStatus.PENDING,
                    f.vm.state.value.applications
                        .single()
                        .status,
                )
                assertEquals(
                    setOf(UserRole.TEACHER),
                    f.vm.state.value.user
                        ?.roles,
                )
                assertEquals(original, f.store.token.value)
            } finally {
                f.close()
                Dispatchers.resetMain()
            }
        }

    @Test fun `uncertain submission retry preserves exact operation and teacher revision`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val f = Fixture()
            f.firstPost = HttpStatusCode.ServiceUnavailable
            try {
                f.ready()
                f.vm.submit("QA-ID", "Group preference")
                f.await { !it.working && it.failure != null }
                f.vm.submit("QA-ID", "Group preference")
                f.await { !it.working && it.submitted }
                assertEquals(2, f.requests.size)
                assertEquals(f.requests[0], f.requests[1])
                assertNotNull(f.requests[0].operationId)
            } finally {
                f.close()
                Dispatchers.resetMain()
            }
        }

    @Test fun `staff scope denial clears profile and applications with no successful empty pending state`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val f = Fixture()
            try {
                f.ready()
                f.status = HttpStatusCode.Forbidden
                f.vm.refresh()
                f.await { !it.loading }
                assertNull(f.vm.state.value.user)
                assertTrue(
                    f.vm.state.value.applications
                        .isEmpty(),
                )
                assertEquals(403, assertIs<AppError.Http>(f.vm.state.value.failure).status)
                assertFalse(f.vm.state.value.submitted)
            } finally {
                f.close()
                Dispatchers.resetMain()
            }
        }

    @Test fun `late request from previous teacher cannot populate replacement account`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val f = Fixture()
            f.gate = CompletableDeferred()
            try {
                f.ready()
                f.vm.submit("QA-ID", "Group preference")
                withContext(Dispatchers.Default) { withTimeout(5000) { f.started.await() } }
                f.store.token.value = jwt("replacement")
                runCurrent()
                f.gate!!.complete(Unit)
                f.await { !it.loading && !it.working }
                assertFalse(f.vm.state.value.submitted)
                assertTrue(
                    f.vm.state.value.applications
                        .isEmpty(),
                )
                assertFalse(
                    f.vm.state.value
                        .toString()
                        .contains("QA-ID"),
                )
            } finally {
                f.close()
                Dispatchers.resetMain()
            }
        }

    @Test fun `typed staff repository preserves genuine forbidden and unavailable errors`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            try {
                for (status in listOf(HttpStatusCode.Forbidden, HttpStatusCode.ServiceUnavailable)) {
                    val f = Fixture()
                    f.status = status
                    f.firstPost = status
                    try {
                        assertEquals(
                            status.value,
                            assertIs<AppError.Http>(assertIs<Outcome.Failure>(f.repo.staffApplications()).error).status,
                        )
                        assertEquals(
                            status.value,
                            assertIs<AppError.Http>(
                                assertIs<Outcome.Failure>(
                                    f.repo.requestTutor(TutorApplicationRequest("operation", 7, "campus", "QA-ID")),
                                ).error,
                            ).status,
                        )
                    } finally {
                        f.close()
                    }
                }
            } finally {
                Dispatchers.resetMain()
            }
        }

    companion object {
        private fun jwt(id: String) =
            "e30." +
                Base64.getUrlEncoder().withoutPadding().encodeToString(
                    """{"sub":"$id","session_id":"session-$id","roles":["TEACHER"],"exp":4102444800}""".toByteArray(),
                ) +
                ".test"
    }
}
