package org.companerodeescuela.feature.grading

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.companerodeescuela.core.network.ApiEnvironment
import org.companerodeescuela.core.network.createApiClient
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.feature.classroom.ClassroomRepository
import org.companerodeescuela.shared.contracts.GradebookDraftRequest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GradebookConcurrentLoadTest {
    @Test
    fun `slow existing draft lookup preserves edits and supplies current revision for CAS`() = slowLookup(published = false)

    @Test
    fun `slow published lookup displays server grades and explains discarded local edits`() = slowLookup(published = true)

    private fun slowLookup(published: Boolean) =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val lookup = CompletableDeferred<Unit>()
            var saved: GradebookDraftRequest? = null
            val client =
                createApiClient(
                    ApiEnvironment("https://example.test/", "test"),
                    MockEngine(
                        MockEngineConfig().apply {
                            dispatcher = StandardTestDispatcher(testScheduler)
                            addHandler { request ->
                                val body =
                                    when (request.url.encodedPath) {
                                        "/classrooms" ->
                                            """{"data":[{"id":"A","name":"Matemáticas","teacherId":"T1","teacherDisplayName":"Docente","canManage":true}]}"""
                                        "/grading/capability" -> """{"data":{"available":false}}"""
                                        "/grading/classrooms/A" -> {
                                            lookup.await()
                                            response(3, if (published) "published" else "draft")
                                        }
                                        "/grading/draft" -> {
                                            saved = Json.decodeFromString<GradebookDraftRequest>((request.body as TextContent).text)
                                            response(4, "draft")
                                        }
                                        else -> error("Unexpected request: ${request.url.encodedPath}")
                                    }
                                respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                            }
                        },
                    ),
                )
            val payload =
                Base64.getUrlEncoder().withoutPadding().encodeToString(
                    """{"sub":"T1","roles":["TEACHER"],"exp":4102444800}""".toByteArray(),
                )
            val tokens =
                object : SessionTokenStore {
                    override suspend fun readAccessToken() = "e30.$payload.signature"

                    override suspend fun writeAccessToken(token: String) = Unit

                    override suspend fun clear() = Unit
                }
            try {
                val vm = GradebookViewModel(GradebookRepository(client, tokens), ClassroomRepository(client, tokens), tokens)
                runCurrent()
                vm.setGradingPeriod("Parcial 1")
                runCurrent()
                vm.addCategory()
                vm.updateCategory(0, "Edición local", 100.0)
                vm.importSpreadsheet("local.csv", "Matricula,Edición local\nA001,9".toByteArray())
                lookup.complete(Unit)
                runCurrent()

                assertEquals(3L, vm.state.value.gradebookRevision)
                assertEquals(published, vm.state.value.gradebookPublished)
                if (published) {
                    assertEquals(
                        "Guardado",
                        vm.state.value.categories
                            .single()
                            .name,
                    )
                    assertEquals(
                        5.0,
                        vm.state.value.preview
                            ?.rows
                            ?.single()
                            ?.values
                            ?.get("Guardado"),
                    )
                    assertFalse(vm.state.value.draftHasUnsavedChanges)
                    assertTrue(
                        vm.state.value.errorMessage
                            .orEmpty()
                            .contains("publicado"),
                    )
                    vm.updateCategory(0, name = "No debe editarse")
                    assertEquals(
                        "Guardado",
                        vm.state.value.categories
                            .single()
                            .name,
                    )
                } else {
                    assertEquals(
                        "Edición local",
                        vm.state.value.categories
                            .single()
                            .name,
                    )
                    assertEquals(
                        9.0,
                        vm.state.value.preview
                            ?.rows
                            ?.single()
                            ?.values
                            ?.get("Edición local"),
                    )
                    assertTrue(vm.state.value.draftHasUnsavedChanges)
                    vm.saveDraft()
                    runCurrent()
                    assertEquals(3L, assertNotNull(saved).expectedRevision)
                    assertEquals("Edición local", assertNotNull(saved).categories.single().name)
                }
            } finally {
                lookup.complete(Unit)
                client.close()
                Dispatchers.resetMain()
            }
        }

    private fun response(
        revision: Int,
        status: String,
    ) = """
        {"data":{
          "classroomId":"A","academicPeriodId":"2026-2","gradingPeriod":"Parcial 1",
          "teacherId":"T1","revision":$revision,"status":"$status",
          "categories":[{"id":"exam","name":"Guardado","weightPercent":100.0}],
          "rows":[{"studentId":"A001","scores":{"exam":5.0}}],
          "updatedAtEpochSeconds":1791892800,"publishedAtEpochSeconds":1791892800,"history":[]
        }}
        """.trimIndent()
}
