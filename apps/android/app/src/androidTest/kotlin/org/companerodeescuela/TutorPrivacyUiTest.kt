package org.companerodeescuela

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.viewModelScope
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.util.Base64
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.companerodeescuela.core.designsystem.theme.CompaneroTheme
import org.companerodeescuela.core.network.ApiEnvironment
import org.companerodeescuela.core.network.createApiClient
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.feature.tutoring.GroupRepresentativeRepository
import org.companerodeescuela.feature.tutoring.TutorGroupsScreen
import org.companerodeescuela.feature.tutoring.TutorRepository
import org.companerodeescuela.feature.tutoring.TutorRequestsScreen
import org.companerodeescuela.feature.tutoring.TutorViewModel
import org.companerodeescuela.shared.contracts.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Real Compose screens and ViewModel; all identities/data are isolated test fixtures. */
class TutorPrivacyUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var fixture: Fixture

    @After fun close() {
        if (::fixture.isInitialized) {
            compose.runOnIdle { fixture.model.viewModelScope.cancel() }
            fixture.client.close()
        }
    }

    @Test fun openReviewDialogDisappearsOnRevocation() {
        fixture = Fixture()
        compose.setContent { CompaneroTheme { TutorRequestsScreen(viewModel = fixture.model) } }
        awaitText("Aprobar")
        compose.onNodeWithText("Aprobar").performClick()
        compose.onNodeWithText("Comentario de revisión (opcional)").performTextInput("PRIVATE_REVIEW_DRAFT")
        compose.onNodeWithText("PRIVATE_REVIEW_DRAFT").assertExists()
        compose.runOnIdle { fixture.scopeStatus = HttpStatusCode.Forbidden; fixture.model.refresh() }
        compose.waitUntil(10_000) { !fixture.model.state.value.loading }
        compose.waitForIdle()
        compose.onNodeWithText("Aprobar justificación").assertDoesNotExist()
        compose.onNodeWithText("PRIVATE_REVIEW_DRAFT").assertDoesNotExist()
        assertEquals(0, fixture.reviewRequests)
    }

    @Test fun privateCaseDraftDoesNotReturnUnderReplacementAccount() {
        fixture = Fixture()
        compose.setContent { CompaneroTheme { TutorGroupsScreen(viewModel = fixture.model) } }
        awaitText("Ver seguimiento")
        compose.onNodeWithText("Ver seguimiento").performScrollTo().performClick()
        compose.onNodeWithText("Motivo de acompañamiento").performScrollTo().performTextInput("PRIVATE_CASE_DRAFT")
        // A normal recomposition keeps the draft; account replacement must discard it.
        compose.onNodeWithText("PRIVATE_CASE_DRAFT").assertExists()
        compose.runOnIdle { fixture.store.token.value = token("replacement", "session-2") }
        compose.waitUntil(10_000) { fixture.scopeRequests >= 2 && !fixture.model.state.value.loading }
        awaitText("Ver seguimiento")
        compose.onNodeWithText("Ver seguimiento").performScrollTo().performClick()
        compose.onNodeWithText("Motivo de acompañamiento").performScrollTo().assertExists()
        compose.onNodeWithText("PRIVATE_CASE_DRAFT").assertDoesNotExist()
    }

    private fun awaitText(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
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
        @Volatile var scopeStatus = HttpStatusCode.OK
        @Volatile var scopeRequests = 0
        @Volatile var reviewRequests = 0
        private val excuse = ExcuseRequestSummary("request-1", "student-1", "9A", "2026-10-09", "QA request reason", status = ExcuseStatus.PENDING, submittedAtEpochSeconds = 1)
        val client = createApiClient(ApiEnvironment("https://example.test/", "test"), MockEngine { request ->
            val path = request.url.encodedPath
            val status = if (path == "/tutoring/me") { scopeRequests++; scopeStatus } else HttpStatusCode.OK
            val body = when {
                status != HttpStatusCode.OK -> """{"code":"forbidden","message":"Unavailable"}"""
                path == "/tutoring/me" -> Json.encodeToString(ApiResponse(TutorScopeSummary("tutor-1", listOf(AcademicGroupSummary("9A", "9 A", true)))))
                path == "/excuses" -> Json.encodeToString(ApiResponse(listOf(excuse)))
                path.endsWith("/review") -> { reviewRequests++; Json.encodeToString(ApiResponse(excuse)) }
                path.endsWith("/students") -> Json.encodeToString(ApiResponse(listOf(TutorStudentSummary("student-1", "9A", "QA Student"))))
                else -> """{"data":[]}"""
            }
            respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        })
        val model = TutorViewModel(TutorRepository(client, store), GroupRepresentativeRepository(client))
    }

    companion object {
        private fun token(owner: String, session: String): String {
            val payload = """{"sub":"$owner","session_id":"$session","exp":${System.currentTimeMillis()/1000+3600},"roles":["TUTOR"]}"""
            return "e30." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray()) + ".test"
        }
    }
}
