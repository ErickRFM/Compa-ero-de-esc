package org.companerodeescuela.feature.schedule

import androidx.lifecycle.viewModelScope
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.companerodeescuela.core.academic.AcademicRepository
import org.companerodeescuela.core.academic.PersonalScheduleDraft
import org.companerodeescuela.core.academic.PersonalScheduleRepository
import org.companerodeescuela.core.database.AcademicSnapshotCache
import org.companerodeescuela.core.database.CachedAcademicLoad
import org.companerodeescuela.core.database.PersonalScheduleItem
import org.companerodeescuela.core.database.PersonalScheduleStore
import org.companerodeescuela.core.network.ApiEnvironment
import org.companerodeescuela.core.network.createApiClient
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.AcademicLoadResponse
import org.companerodeescuela.shared.contracts.ScheduleSource
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleAuthorityTest {
    @Test
    fun `503 and revoked authority remain visible and retain only independently stored personal entries`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val status = AtomicInteger(200)
            var blockPersonalRead = false
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val finished = CompletableDeferred<Unit>()
            val client =
                createApiClient(
                    ApiEnvironment("https://synthetic.test/", "test"),
                    MockEngine {
                        respond(
                            if (status.get() == 200) PAYLOAD else "{}",
                            HttpStatusCode.fromValue(status.get()),
                            headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    },
                )
            val tokens =
                object : SessionTokenStore {
                    var value: String? =
                        "e30." +
                            Base64.getUrlEncoder().withoutPadding().encodeToString(
                                """{"sub":"qa","roles":["STUDENT"],"exp":4102444800}""".toByteArray(),
                            ) + ".test"

                    override suspend fun readAccessToken() = value

                    override suspend fun writeAccessToken(token: String) {
                        value = token
                    }

                    override suspend fun clear() {
                        value = null
                    }
                }
            val cache =
                object : AcademicSnapshotCache {
                    var value: AcademicLoadResponse? = null

                    override suspend fun read(ownerId: String) = value?.let { CachedAcademicLoad(it, 1) }

                    override suspend fun write(value: AcademicLoadResponse) {
                        this.value = value
                    }

                    override suspend fun clear(ownerId: String) {
                        value = null
                    }
                }
            val store =
                object : PersonalScheduleStore {
                    val values = mutableListOf<PersonalScheduleItem>()

                    override suspend fun list(ownerId: String): List<PersonalScheduleItem> {
                        if (blockPersonalRead) {
                            blockPersonalRead = false
                            entered.complete(Unit)
                            withContext(NonCancellable) { release.await() }
                            finished.complete(Unit)
                            throw CancellationException("Synthetic canceled storage read")
                        }
                        return values.filter { it.ownerId == ownerId }
                    }

                    override suspend fun upsert(item: PersonalScheduleItem) {
                        values += item
                    }

                    override suspend fun delete(
                        ownerId: String,
                        id: String,
                    ) {
                        values.removeAll { it.ownerId == ownerId && it.id == id }
                    }

                    override suspend fun replaceBySource(
                        ownerId: String,
                        source: ScheduleSource,
                        items: List<PersonalScheduleItem>,
                    ) {
                        values.removeAll { it.ownerId == ownerId && it.source == source }
                        values += items
                    }
                }
            val personal = PersonalScheduleRepository(tokens, store)
            personal.save(
                PersonalScheduleDraft(subjectName = "Personal import", dayOfWeek = "MONDAY", startsAt = "11:00", endsAt = "12:00"),
            )
            val vm = ScheduleViewModel(AcademicRepository(client, tokens, cache, store), personal)

            suspend fun loaded() = withContext(Dispatchers.Default) { withTimeout(5000) { vm.state.first { !it.loading } } }
            try {
                assertEquals(2, loaded().entries.size)
                for (failure in listOf(503, 403)) {
                    status.set(failure)
                    vm.load()
                    val denied = loaded()
                    assertNotNull(denied.errorMessage)
                    assertEquals(listOf("Personal import"), denied.entries.map { it.subjectName })
                }
                status.set(503)
                blockPersonalRead = true
                vm.load()
                entered.await()
                status.set(200)
                vm.load()
                assertEquals(2, loaded().entries.size)
                release.complete(Unit)
                finished.await()
                assertNull(vm.state.value.errorMessage, "Canceled storage result must not replace its successor")
                assertEquals(2, vm.state.value.entries.size)
                assertEquals(1, store.values.size)
            } finally {
                vm.viewModelScope.cancel()
                client.close()
                Dispatchers.resetMain()
            }
        }

    companion object {
        private const val PAYLOAD = """{"data":{"student":{"id":"qa","displayName":"Synthetic"},
        "institutionalSource":"SIII","schedule":{"ownerId":"qa","entries":[
        {"courseId":"section","subjectCode":"S","subjectName":"Official subject","groupName":"G",
        "teacherName":"Teacher","dayOfWeek":"MONDAY","startsAt":"09:00","endsAt":"10:00"}]}}}"""
    }
}
