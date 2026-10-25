package org.companerodeescuela.feature.classroom

import androidx.lifecycle.viewModelScope
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.companerodeescuela.core.academic.AcademicRepository
import org.companerodeescuela.core.database.AcademicSnapshotCache
import org.companerodeescuela.core.database.CachedAcademicLoad
import org.companerodeescuela.core.network.ApiEnvironment
import org.companerodeescuela.core.network.createApiClient
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.AcademicLoadResponse
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InstitutionalClassesViewModelTest {
    @Test
    fun `official catalog remains distinct from collaborative permission failures and revocation clears it`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val status = AtomicInteger(200)
            val payload = """{"data":{
            "student":{"id":"qa-user","displayName":"QA"},"schedule":{"ownerId":"qa-user","entries":[]},
            "institutionalSource":"SIII","institutionalSections":[
            {"id":"section","groupName":"GROUP_A","subjectId":"subject","subjectCode":"SUB_A",
            "subjectName":"Official subject","teacherNames":["Official teacher"],"timezone":"UTC",
            "sourceVersion":7,"syncedAt":"2026-10-25T12:00:00Z","occurrences":[]}]}}"""
            val client =
                createApiClient(
                    ApiEnvironment("https://example.test/", "test"),
                    MockEngine { request ->
                        val (code, body) =
                            when (request.url.encodedPath) {
                                "/academic/load" -> HttpStatusCode.fromValue(status.get()) to if (status.get() == 200) payload else "{}"
                                "/classrooms" -> HttpStatusCode.Forbidden to "{}"
                                "/academic/groups" -> HttpStatusCode.OK to """{"data":[]}"""
                                else -> error("Unexpected endpoint")
                            }
                        respond(body, code, headersOf(HttpHeaders.ContentType, "application/json"))
                    },
                )
            val store =
                object : SessionTokenStore {
                    private var token: String? =
                        "e30." +
                            Base64.getUrlEncoder().withoutPadding().encodeToString(
                                """{"sub":"qa-user","roles":["STUDENT"],"session_id":"qa-session","exp":4102444800}""".toByteArray(),
                            ) +
                            ".test"

                    override suspend fun readAccessToken() = token

                    override suspend fun writeAccessToken(token: String) {
                        this.token = token
                    }

                    override suspend fun clear() {
                        token = null
                    }
                }
            val cache =
                object : AcademicSnapshotCache {
                    private var value: AcademicLoadResponse? = null

                    override suspend fun read(ownerId: String) = value?.let { CachedAcademicLoad(it, 1) }

                    override suspend fun write(value: AcademicLoadResponse) {
                        this.value = value
                    }

                    override suspend fun clear(ownerId: String) {
                        value = null
                    }
                }
            val vm =
                ClassroomViewModel(
                    ClassroomRepository(client, store),
                    AcademicGroupRepository(client, store),
                    AcademicRepository(client, store, cache),
                )

            suspend fun completed() =
                withContext(Dispatchers.Default) {
                    withTimeout(5000) {
                        vm.state.first {
                            !it.loading &&
                                !it.institutionalLoading
                        }
                    }
                }
            try {
                val first = completed()
                assertEquals("Official subject", first.institutionalSections.single().subjectName)
                assertEquals("SIII", first.institutionalSource)
                assertNotNull(first.errorMessage)
                status.set(403)
                vm.refresh()
                val revoked = completed()
                assertTrue(revoked.institutionalSections.isEmpty())
                assertNotNull(revoked.institutionalErrorMessage)
            } finally {
                vm.viewModelScope.cancel()
                client.close()
                Dispatchers.resetMain()
            }
        }
}
