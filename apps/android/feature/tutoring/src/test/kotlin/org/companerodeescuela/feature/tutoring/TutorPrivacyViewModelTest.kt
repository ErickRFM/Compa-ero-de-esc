package org.companerodeescuela.feature.tutoring

import androidx.lifecycle.viewModelScope
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.companerodeescuela.core.network.ApiEnvironment
import org.companerodeescuela.core.network.createApiClient
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.AcademicGroupSummary
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.ExcuseRequestSummary
import org.companerodeescuela.shared.contracts.ExcuseStatus
import org.companerodeescuela.shared.contracts.TutorCaseStatus
import org.companerodeescuela.shared.contracts.TutorCaseSummary
import org.companerodeescuela.shared.contracts.TutorScopeSummary
import org.companerodeescuela.shared.contracts.TutorStudentSummary

@OptIn(ExperimentalCoroutinesApi::class)
class TutorPrivacyViewModelTest {
    @Test
    fun `failed scope revalidation clears cases roster and group identifiers`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            fixture.populate()
            fixture.scopeStatus = HttpStatusCode.Forbidden
            fixture.model.refresh()
            fixture.model.state.first { !it.loading }
            assertPrivateCleared(fixture.model.state.value)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test
    fun `empty current scope removes roster and stale case response`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            fixture.populate()
            fixture.groups = emptyList()
            fixture.model.refresh()
            fixture.model.state.first { !it.loading }
            assertPrivateCleared(fixture.model.state.value)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test
    fun `account change clears private data even when new scope is unavailable`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            fixture.populate()
            fixture.scopeStatus = HttpStatusCode.ServiceUnavailable
            fixture.store.token.value = token("other-tutor", "other-session")
            runCurrent()
            assertPrivateCleared(fixture.model.state.value)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test
    fun `late roster response cannot restore data after scope revocation`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            fixture.model.state.first { !it.loading }
            fixture.rosterGate = CompletableDeferred()
            fixture.model.loadRoster("9A")
            fixture.rosterStarted.await()
            fixture.scopeStatus = HttpStatusCode.Forbidden
            fixture.model.refresh()
            fixture.model.state.first { !it.loading }
            fixture.rosterGate!!.complete(Unit)
            fixture.rosterResponded.await()
            runCurrent()
            assertPrivateCleared(fixture.model.state.value)
        } finally { fixture.rosterGate?.complete(Unit); fixture.close(); Dispatchers.resetMain() }
    }

    @Test
    fun `denied note mutation removes retained private information`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            fixture.populate()
            fixture.noteStatus = HttpStatusCode.Forbidden
            fixture.model.addNote("case-1", "A private observation", false)
            fixture.model.state.first { !it.submitting }
            assertEquals(1, fixture.noteRequests, "The note mutation must actually reach the API")
            assertPrivateCleared(fixture.model.state.value)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    private fun assertPrivateCleared(state: TutorUiState) {
        assertTrue(state.cases.isEmpty())
        assertTrue(state.roster.isEmpty())
        assertTrue(state.requests.isEmpty())
        assertEquals(null, state.rosterGroupId)
    }

    @Test
    fun `review refuses a request outside the current scope`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            fixture.populate()
            fixture.model.review("unknown-request", true, "Old draft")
            fixture.model.state.first { !it.submitting }
            assertEquals(0, fixture.reviewRequests)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test
    fun `authorized pending review reaches the API`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            fixture.populate()
            fixture.model.review("request-1", true, "Reviewed")
            fixture.model.state.first { !it.loading && !it.submitting }
            assertEquals(1, fixture.reviewRequests)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test
    fun `old confirmation cannot mutate even when replacement account sees the same resource`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            fixture.populate()
            val origin = fixture.model.state.value.scopeGeneration
            fixture.store.token.value = token("other-tutor", "other-session")
            fixture.model.state.first { !it.loading && it.scopeGeneration != origin }
            fixture.model.review("request-1", true, "Private old comment", origin)
            fixture.model.createCase("9A", "student-1", "Private old draft", origin)
            fixture.model.addNote("case-1", "Private old note", false, origin)
            runCurrent()
            assertEquals(0, fixture.reviewRequests)
            assertEquals(0, fixture.noteRequests)
            assertEquals(0, fixture.createRequests)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    private class Store : SessionTokenStore {
        val token = MutableStateFlow<String?>(token("tutor-1", "session-1"))
        override suspend fun readAccessToken() = token.value
        override suspend fun writeAccessToken(token: String) { this.token.value = token }
        override suspend fun clear() { token.value = null }
        override fun observeAccessToken() = token
    }

    private class Fixture {
        val store = Store()
        var scopeStatus = HttpStatusCode.OK
        var noteStatus = HttpStatusCode.OK
        var noteRequests = 0
        var reviewRequests = 0
        var createRequests = 0
        var groups = listOf(AcademicGroupSummary("9A", "9 A", true))
        var rosterGate: CompletableDeferred<Unit>? = null
        val rosterStarted = CompletableDeferred<Unit>()
        val rosterResponded = CompletableDeferred<Unit>()
        private val record = TutorCaseSummary("case-1", "9A", "student-1", "Academic support", TutorCaseStatus.OPEN, "tutor-1", 1, 1)
        private val excuse = ExcuseRequestSummary("request-1", "student-1", "9A", "2026-10-09", "QA reason", status = ExcuseStatus.PENDING, submittedAtEpochSeconds = 1)
        private val client = createApiClient(ApiEnvironment("https://example.test/", "test"), MockEngine { request ->
            val path = request.url.encodedPath
            if (path.endsWith("/notes")) noteRequests++
            if (path.endsWith("/review")) reviewRequests++
            if (path == "/tutoring/cases" && request.method.value == "POST") createRequests++
            val status = when {
                path == "/tutoring/me" -> scopeStatus
                path.endsWith("/notes") -> noteStatus
                else -> HttpStatusCode.OK
            }
            val body = when {
                status != HttpStatusCode.OK -> """{"code":"forbidden","message":"Unavailable"}"""
                path == "/tutoring/me" -> Json.encodeToString(ApiResponse(TutorScopeSummary("tutor-1", groups)))
                path == "/excuses" -> Json.encodeToString(ApiResponse(listOf(excuse)))
                path.endsWith("/review") -> Json.encodeToString(ApiResponse(excuse))
                path == "/tutoring/cases" -> Json.encodeToString(ApiResponse(listOf(record)))
                path.endsWith("/students") -> {
                    rosterStarted.complete(Unit)
                    rosterGate?.let { withContext(NonCancellable) { it.await() } }
                    rosterResponded.complete(Unit)
                    Json.encodeToString(ApiResponse(listOf(TutorStudentSummary("student-1", "9A", "QA Student"))))
                }
                path.endsWith("/notes") -> Json.encodeToString(ApiResponse(record))
                else -> """{"data":[]}"""
            }
            respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        })
        val model = TutorViewModel(TutorRepository(client, store))
        suspend fun populate() {
            model.state.first { !it.loading }
            assertEquals(1, model.state.value.cases.size)
            model.loadRoster("9A")
            model.state.first { !it.rosterLoading }
            assertEquals(1, model.state.value.roster.size)
        }
        suspend fun close() {
            model.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin()
            client.close()
        }
    }

    companion object {
        private fun token(owner: String, session: String): String {
            val payload = """{"sub":"$owner","session_id":"$session","exp":${System.currentTimeMillis()/1000+3600},"roles":["TUTOR"]}"""
            return "e30." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray()) + ".test"
        }
    }
}
