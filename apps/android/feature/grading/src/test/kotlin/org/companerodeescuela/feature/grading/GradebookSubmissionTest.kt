package org.companerodeescuela.feature.grading

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
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

@OptIn(ExperimentalCoroutinesApi::class)
class GradebookSubmissionTest {
    @Test
    fun `duplicate normalized headers cannot silently replace grades`() {
        assertFailsWith<IllegalArgumentException> {
            SpreadsheetReader.read("grades.csv", "Matricula,Examen, examen \nA001,2,9".toByteArray())
        }
    }

    @Test
    fun `CSV preserves quoted student names and UTF8 BOM`() {
        val preview = SpreadsheetReader.read("grades.csv", "\uFEFFMatricula,Nombre,Examen\nA001,\"Pérez, Ana\",9".toByteArray())
        assertEquals("Pérez, Ana", preview.rows.single().displayName)
        assertEquals(9.0, preview.rows.single().values["Examen"])
        assertTrue(preview.warnings.isEmpty())
    }

    @Test
    fun `period categories and authorization refresh stay frozen during submission`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val response = CompletableDeferred<Unit>()
        var classRequests = 0
        val client = createApiClient(ApiEnvironment("https://example.test/", "test"), MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                val body = if (request.url.encodedPath == "/classrooms") {
                    classRequests++
                    """{"data":[{"id":"A","name":"Matemáticas","teacherId":"T1","teacherDisplayName":"Docente","canManage":true}]}"""
                } else {
                    response.await()
                    """{"data":{"status":"unavailable","acceptedStudentIds":[],"rejectedStudentIds":[]}}"""
                }
                respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }))
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"sub":"T1","roles":["TEACHER"],"exp":4102444800}""".toByteArray())
        val tokens = object : SessionTokenStore {
            override suspend fun readAccessToken() = "e30.$payload.signature"
            override suspend fun writeAccessToken(token: String) = Unit
            override suspend fun clear() = Unit
        }
        try {
            val vm = GradebookViewModel(GradebookRepository(client, tokens), ClassroomRepository(client, tokens), tokens)
            runCurrent()
            vm.setGradingPeriod("Parcial 1")
            vm.addCategory()
            vm.updateCategory(0, "Examen", 100.0)
            vm.importSpreadsheet("grades.csv", "Matricula,Examen\nA001,9".toByteArray())
            vm.sync()
            runCurrent()
            assertTrue(vm.state.value.submitting)
            val categories = vm.state.value.categories
            vm.setGradingPeriod("Parcial 2")
            vm.updateCategory(0, "Tarea", 50.0)
            vm.addCategory()
            vm.removeCategory(0)
            vm.refreshClassrooms()
            runCurrent()
            assertEquals("Parcial 1", vm.state.value.gradingPeriod)
            assertEquals(categories, vm.state.value.categories)
            assertEquals(1, classRequests)
            response.complete(Unit)
            runCurrent()
        } finally { client.close(); Dispatchers.resetMain() }
    }
}
