package org.companerodeescuela

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.cancel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.companerodeescuela.core.designsystem.theme.CompaneroTheme
import org.companerodeescuela.core.network.ApiEnvironment
import org.companerodeescuela.core.network.createApiClient
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.feature.channel.ChannelRepository
import org.companerodeescuela.feature.channel.ChannelScreen
import org.companerodeescuela.feature.channel.ChannelViewModel
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.ChannelTask
import org.companerodeescuela.shared.contracts.ClassChannelSummary
import org.companerodeescuela.shared.contracts.TaskStatus
import org.companerodeescuela.shared.contracts.TaskSubmission
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Base64

/** Synthetic transport; exercises the actual repository, ViewModel and task sheets at 200% text. */
class ChannelTaskLayoutUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun teacherCanReachLastSubmissionAndReviewActions() =
        withChannel(teacher = true) {
            openTask()
            compose
                .onNode(hasScrollAction() and hasAnyDescendant(hasText("Publicada por Docente QA")))
                .performScrollToNode(hasText("Alumno QA 8"))
            compose.onNodeWithText("Marcar revisada").performScrollTo().assertIsDisplayed()
            capture("teacher-review-200")
        }

    @Test
    fun teacherSeesPersistedScoreAfterReview() =
        withChannel(teacher = true) {
            openTask()
            compose.onNodeWithText("Calificación de tarea: 8.5 / 10").performScrollTo().assertIsDisplayed()
        }

    @Test
    fun studentCanReachSubmissionAfterLongInstructions() =
        withChannel(teacher = false) {
            openTask()
            compose.onNodeWithText("Enviar entrega").performScrollTo().assertIsDisplayed()
            capture("student-submit-200")
        }

    @Test
    fun taskComposerKeepsPublishActionReachableAtLargeText() =
        withChannel(teacher = true) {
            scrollChannelTo("Crear tarea")
            val layouts = mutableListOf<TextLayoutResult>()
            compose
                .onNodeWithText(
                    "Tareas",
                    useUnmergedTree = true,
                ).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals("La pestaña debe conservar una palabra completa al 200%", 1, layouts.single().lineCount)
            compose.onNodeWithText("Crear tarea").performClick()
            compose.onNodeWithText("Publicar tarea").performScrollTo().assertIsDisplayed()
            capture("task-composer-200")
        }

    private fun openTask() {
        scrollChannelTo("Proyecto QA")
        compose.onNodeWithText("Proyecto QA").performClick()
        compose.waitForIdle()
    }

    private fun scrollChannelTo(text: String) {
        compose
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToNode(hasText(text))
    }

    private fun withChannel(
        teacher: Boolean,
        block: () -> Unit,
    ) {
        val fixture = Fixture(teacher)
        try {
            compose.setContent {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides compose.activity) {
                    AuthTestLocale {
                        CompaneroTheme {
                            val density = LocalDensity.current.density
                            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                                Box(Modifier.width(360.dp).height(540.dp)) {
                                    ChannelScreen(requestedTab = 2, viewModel = fixture.model)
                                }
                            }
                        }
                    }
                }
            }
            compose.waitUntil(30_000) {
                val state = fixture.model.state.value
                !state.loading && !state.tasksLoading && state.tasks.isNotEmpty()
            }
            block()
        } finally {
            compose.runOnIdle { fixture.model.viewModelScope.cancel() }
            fixture.client.close()
        }
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = File(instrumentation.targetContext.filesDir, "audit-task-layout").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    private class Fixture(
        teacher: Boolean,
    ) {
        private val channel =
            ClassChannelSummary("class", "class", "QA", "Materia QA", "Grupo QA", "2026", "teacher", "Docente QA", teacher)
        private val task =
            ChannelTask(
                id = "task",
                channelId = "class",
                teacherId = "teacher",
                teacherDisplayName = "Docente QA",
                title = "Proyecto QA",
                instructions = "Instrucciones extensas para comprobar el acceso a todas las acciones. ".repeat(15),
                dueDateEpochSeconds = 4_102_444_800,
                createdAtEpochSeconds = 1,
            )
        private val submissions =
            if (teacher) {
                (1..8).map { index ->
                    TaskSubmission(
                        id = "submission-$index",
                        taskId = "task",
                        channelId = "class",
                        studentId = "student-$index",
                        studentDisplayName = "Alumno QA $index",
                        status = if (index == 8) TaskStatus.SUBMITTED else TaskStatus.REVIEWED,
                        submittedAtEpochSeconds = 1,
                        score = if (index == 1) 8.5 else null,
                    )
                }
            } else {
                emptyList()
            }
        val client =
            createApiClient(
                ApiEnvironment("https://synthetic-qa.test/", "task-layout-fixture"),
                MockEngine { request ->
                    val body =
                        when (request.url.encodedPath) {
                            "/channels" -> Json.encodeToString(ApiResponse(listOf(channel)))
                            "/channels/class/tasks" -> Json.encodeToString(ApiResponse(listOf(task)))
                            "/channels/class/tasks/task/submissions" -> Json.encodeToString(ApiResponse(submissions))
                            else -> """{"data":[]}"""
                        }
                    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                },
            )
        private val store =
            object : SessionTokenStore {
                private val payload =
                    Base64.getUrlEncoder().withoutPadding().encodeToString(
                        """{"sub":"qa-user","roles":["TEACHER"],"session_id":"qa-session","exp":4102444800}""".toByteArray(),
                    )

                override suspend fun readAccessToken() = "e30.$payload.signature"

                override suspend fun writeAccessToken(token: String) = Unit

                override suspend fun clear() = Unit
            }
        val model = ChannelViewModel(ChannelRepository(client, store))
    }
}
