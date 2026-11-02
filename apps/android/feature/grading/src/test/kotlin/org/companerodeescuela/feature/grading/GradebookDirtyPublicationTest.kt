package org.companerodeescuela.feature.grading

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.companerodeescuela.core.network.ApiEnvironment
import org.companerodeescuela.core.network.createApiClient
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.feature.classroom.ClassroomRepository
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class GradebookDirtyPublicationTest {
    @Test
    fun `editing a saved draft or replacing its import requires saving before publication`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            var draftSaves = 0
            var publications = 0
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
                                        "/grading/classrooms/A" -> """{"data":null}"""
                                        "/grading/draft" -> response(++draftSaves, "draft")
                                        "/grading/publish" -> {
                                            publications++
                                            response(draftSaves, "published")
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
                vm.updateCategory(0, "Examen", 100.0)
                vm.importSpreadsheet("first.csv", "Matricula,Examen\nA001,9".toByteArray())
                vm.saveDraft()
                runCurrent()
                assertEquals(1L, vm.state.value.gradebookRevision)

                vm.updateCategory(0, weight = 50.0)
                vm.publishDraft()
                runCurrent()
                assertEquals(0, publications, "Unsaved percentage must not publish the previous revision")

                vm.updateCategory(0, weight = 100.0)
                vm.importSpreadsheet("replacement.csv", "Matricula,Examen\nA001,8".toByteArray())
                vm.publishDraft()
                runCurrent()
                assertEquals(0, publications, "Unsaved import must not publish the previous revision")

                vm.saveDraft()
                runCurrent()
                assertEquals(2L, vm.state.value.gradebookRevision)
                vm.publishDraft()
                runCurrent()
                assertEquals(1, publications)
            } finally {
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
          "categories":[{"id":"exam","name":"Examen","weightPercent":100.0}],
          "rows":[{"studentId":"A001","scores":{"exam":8.0}}],
          "updatedAtEpochSeconds":1791892800,"publishedAtEpochSeconds":1791892800,"history":[]
        }}
        """.trimIndent()
}
