package org.companerodeescuela.feature.grading

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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

@OptIn(ExperimentalCoroutinesApi::class)
class GradeCapabilityPreflightTest {
    @Test fun `unavailable capability disables publishing without blocking review`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var publishRequests = 0
        val client = createApiClient(ApiEnvironment("https://example.test/", "test"), MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
            val body = when (request.url.encodedPath) {
                "/classrooms" -> """{"data":[{"id":"A","name":"Matemáticas","teacherId":"T1","teacherDisplayName":"Docente","canManage":true}]}"""
                "/grading/capability" -> """{"data":{"available":false}}"""
                else -> {
                    publishRequests++
                    """{"data":{"status":"synced","acceptedStudentIds":["A001"],"rejectedStudentIds":[]}}"""
                }
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }))
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"sub":"T1","roles":["TEACHER"],"exp":4102444800}""".toByteArray())
        val store = object : SessionTokenStore {
            override suspend fun readAccessToken() = "e30.$payload.signature"
            override suspend fun writeAccessToken(token: String) = Unit
            override suspend fun clear() = Unit
        }
        try {
            val vm = GradebookViewModel(GradebookRepository(client, store), ClassroomRepository(client, store), store)
            runCurrent()
            assertFalse(vm.state.value.capabilityLoading)
            assertFalse(vm.state.value.institutionalSyncAvailable)
            vm.setGradingPeriod("Parcial 1")
            vm.addCategory()
            vm.updateCategory(0, "Examen", 100.0)
            vm.importSpreadsheet("grades.csv", "Matricula,Examen\nA001,9".toByteArray())
            assertTrue(vm.state.value.preview != null)
            vm.sync()
            runCurrent()
            assertTrue(vm.state.value.errorMessage?.contains("no enviar", ignoreCase = true) == true)
            assertTrue(publishRequests == 0)
        } finally { client.close(); Dispatchers.resetMain() }
    }
}
