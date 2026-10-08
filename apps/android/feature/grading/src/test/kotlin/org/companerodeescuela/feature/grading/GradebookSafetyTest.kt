package org.companerodeescuela.feature.grading

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.companerodeescuela.core.network.ApiEnvironment
import org.companerodeescuela.core.network.createApiClient
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.feature.classroom.ClassroomRepository
import org.companerodeescuela.shared.contracts.GradeCategoryDraft

@OptIn(ExperimentalCoroutinesApi::class)
class GradebookSafetyTest {
    @Test
    fun `100 percent with blank duplicate or negative activities is invalid`() {
        assertFalse(GradebookUiState(categories = listOf(GradeCategoryDraft("", 100.0))).schemeComplete)
        assertFalse(GradebookUiState(categories = listOf(GradeCategoryDraft("Examen", 50.0), GradeCategoryDraft(" examen ", 50.0))).schemeComplete)
        assertFalse(GradebookUiState(categories = listOf(GradeCategoryDraft("Examen", 110.0), GradeCategoryDraft("Tarea", -10.0))).schemeComplete)
        assertTrue(GradebookUiState(categories = listOf(GradeCategoryDraft("Examen", 70.0), GradeCategoryDraft("Tarea", 30.0))).schemeComplete)
    }

    @Test
    fun `failed replacement import cannot keep previous grades`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = object : SessionTokenStore {
            override suspend fun readAccessToken(): String? = null
            override suspend fun writeAccessToken(token: String) = Unit
            override suspend fun clear() = Unit
        }
        val client = createApiClient(ApiEnvironment("https://example.test/", "test"), MockEngine {
            respond("{\"data\":[]}", headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        })
        try {
            val vm = GradebookViewModel(GradebookRepository(client, store), ClassroomRepository(client, store), store)
            vm.importSpreadsheet("valid.csv", "Matricula,Examen\nA001,9".toByteArray())
            assertTrue(vm.state.value.preview != null)
            vm.importSpreadsheet("invalid.csv", "Examen\n9".toByteArray())
            assertNull(vm.state.value.preview)
            assertNull(vm.state.value.importedFileName)
            assertTrue(vm.state.value.errorMessage != null)
        } finally {
            client.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `spreadsheet reports missing IDs duplicate IDs empty and invalid grades`() {
        val preview = SpreadsheetReader.read("invalid.csv", "Matricula,Examen\n,9\nA001,\nA001,11\nA002,NaN".toByteArray())
        assertTrue(preview.warnings.any { it.contains("matrícula", ignoreCase = true) })
        assertTrue(preview.warnings.any { it.contains("duplic", ignoreCase = true) })
        assertTrue(preview.warnings.any { it.contains("vac", ignoreCase = true) })
        assertTrue(preview.warnings.any { it.contains("0", ignoreCase = true) && it.contains("10") })
    }
}
