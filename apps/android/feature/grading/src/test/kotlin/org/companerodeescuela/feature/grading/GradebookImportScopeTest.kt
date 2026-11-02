package org.companerodeescuela.feature.grading

import io.ktor.client.engine.mock.MockEngine
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class GradebookImportScopeTest {
    @Test
    fun `a delayed import or failure cannot replace the current class or period preview`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val tokens =
                object : SessionTokenStore {
                    override suspend fun readAccessToken(): String? = null

                    override suspend fun writeAccessToken(token: String) = Unit

                    override suspend fun clear() = Unit
                }
            val client =
                createApiClient(
                    ApiEnvironment("https://example.test/", "test"),
                    MockEngine {
                        respond("{\"data\":[]}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    },
                )
            try {
                val vm = GradebookViewModel(GradebookRepository(client, tokens), ClassroomRepository(client, tokens), tokens)
                runCurrent()
                val bytes = "Matricula,Examen\nA001,9".toByteArray()
                vm.importSpreadsheet("current.csv", bytes)
                vm.importSpreadsheet("old-class.csv", bytes, expectedClassroomId = "other-class", expectedGradingPeriod = "")
                vm.importSpreadsheet("old-period.csv", bytes, expectedClassroomId = "", expectedGradingPeriod = "Parcial 2")
                vm.reportImportFailure("old failure", expectedClassroomId = "other-class", expectedGradingPeriod = "")
                assertEquals("current.csv", vm.state.value.importedFileName)
                assertNotNull(vm.state.value.preview)
            } finally {
                client.close()
                Dispatchers.resetMain()
            }
        }
}
