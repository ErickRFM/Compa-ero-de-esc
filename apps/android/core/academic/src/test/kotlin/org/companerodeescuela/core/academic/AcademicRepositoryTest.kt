package org.companerodeescuela.core.academic

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.database.AcademicSnapshotCache
import org.companerodeescuela.core.database.CachedAcademicLoad
import org.companerodeescuela.core.database.PersonalScheduleItem
import org.companerodeescuela.core.database.PersonalScheduleStore
import org.companerodeescuela.core.network.ApiEnvironment
import org.companerodeescuela.core.network.createApiClient
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.AcademicLoadResponse
import org.companerodeescuela.shared.contracts.AcademicProfile
import org.companerodeescuela.shared.contracts.AcademicScheduleResponse
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.InstitutionalAcademicSection
import org.companerodeescuela.shared.contracts.ScheduleEntry
import org.companerodeescuela.shared.contracts.ScheduleRecurrence
import org.companerodeescuela.shared.contracts.ScheduleSource
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AcademicRepositoryTest {
    @Test
    fun `source authority 503 cannot be replaced by a historical SIII cache`() =
        runTest {
            val historical = cached("student-1", "Ana").let { it.copy(value = it.value.copy(institutionalSource = "SIII")) }
            val repo =
                repository(
                    MockEngine { respond("{}", HttpStatusCode.ServiceUnavailable) },
                    FakeTokenStore(platformToken("student-1")),
                    FakeCache(
                        mapOf(
                            "student-1" to historical,
                        ),
                    ),
                )
            val failure = assertIs<Outcome.Failure>(repo.load())
            assertEquals(503, assertIs<AppError.Http>(failure.error).status)
        }

    @Test
    fun `official catalog survives transport and overlapping personal imports remain separate`() =
        runTest {
            val official =
                ScheduleEntry(
                    "section-1",
                    "SUB_A",
                    "Official subject",
                    "GROUP_A",
                    "Official teacher",
                    "MONDAY",
                    "09:00",
                    "10:00",
                    effectiveDate = "2026-10-26",
                    recurrence = ScheduleRecurrence.ONE_TIME,
                )
            val section =
                InstitutionalAcademicSection(
                    "section-1",
                    "GROUP_A",
                    "subject-1",
                    "SUB_A",
                    "Official subject",
                    listOf("Official teacher"),
                    "UTC",
                    7,
                    "2026-10-25T12:00:00Z",
                    emptyList(),
                )
            val remote =
                AcademicLoadResponse(
                    AcademicProfile("student-1", "Ana"),
                    AcademicScheduleResponse("student-1", listOf(official)),
                    listOf(section),
                    "SIII",
                )
            val personal =
                PersonalScheduleItem(
                    "import-1",
                    "student-1",
                    "SUB_A",
                    "Official subject",
                    "Personal group",
                    "Personal teacher",
                    "MONDAY",
                    "09:00",
                    "10:00",
                    null,
                    null,
                    ScheduleSource.OCR_IMPORT,
                    ScheduleRecurrence.WEEKLY,
                    "import-series",
                    null,
                    100,
                )
            val store = FakePersonalScheduleStore(listOf(personal))
            val cache = FakeCache(emptyMap())
            val repo =
                repository(
                    MockEngine {
                        respond(
                            Json.encodeToString(ApiResponse(remote)),
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    },
                    FakeTokenStore(platformToken("student-1")),
                    cache,
                    store,
                )
            val content = assertIs<Outcome.Success<AcademicContent>>(repo.load()).value
            assertEquals(listOf(section), content.academic.institutionalSections)
            assertEquals(2, content.academic.schedule.entries.size)
            assertEquals(
                setOf(ScheduleSource.INSTITUTIONAL, ScheduleSource.OCR_IMPORT),
                content.academic.schedule.entries
                    .map { it.source }
                    .toSet(),
            )
            assertEquals(listOf(personal), store.list("student-1"))
            assertEquals(
                listOf(official),
                cache.values
                    .getValue("student-1")
                    .value.schedule.entries,
            )
        }

    @Test
    fun `network failure falls back only to active student's cache`() =
        runTest {
            val tokenStore = FakeTokenStore(platformToken("student-1"))
            val cache =
                FakeCache(
                    mapOf(
                        "student-1" to cached("student-1", "Ana"),
                        "student-2" to cached("student-2", "Luis"),
                    ),
                )
            val repository =
                repository(
                    engine =
                        MockEngine {
                            throw java.io.IOException("Synthetic offline transport")
                        },
                    tokenStore = tokenStore,
                    cache = cache,
                )

            val result = repository.load()

            val success = assertIs<Outcome.Success<AcademicContent>>(result)
            assertTrue(success.value.fromCache)
            assertEquals("student-1", success.value.academic.student.id)
            assertEquals("student-1", cache.lastReadOwner)
        }

    @Test
    fun `network failure without institutional cache still exposes imported local schedule`() =
        runTest {
            val tokenStore = FakeTokenStore(platformToken("student-1"))
            val cache = FakeCache(emptyMap())
            val personalStore =
                FakePersonalScheduleStore(
                    listOf(
                        PersonalScheduleItem(
                            id = "pdf-mobile-1",
                            ownerId = "student-1",
                            subjectCode = "PM",
                            subjectName = "Programación Móvil",
                            groupName = "9 A",
                            teacherName = "Saúl Olaf Loaiza",
                            dayOfWeek = "TUESDAY",
                            startsAt = "07:00",
                            endsAt = "08:00",
                            classroomName = null,
                            buildingName = null,
                            source = ScheduleSource.OCR_IMPORT,
                            recurrence = ScheduleRecurrence.WEEKLY,
                            seriesId = "pdf-mobile",
                            effectiveDate = null,
                            updatedAtEpochSeconds = 100,
                        ),
                    ),
                )
            val repository =
                repository(
                    engine =
                        MockEngine {
                            throw java.io.IOException("Synthetic offline transport")
                        },
                    tokenStore = tokenStore,
                    cache = cache,
                    personalScheduleStore = personalStore,
                )

            val result = repository.load()

            val success = assertIs<Outcome.Success<AcademicContent>>(result)
            assertTrue(success.value.fromCache)
            assertEquals("student-1", success.value.academic.student.id)
            assertEquals("Student", success.value.academic.student.displayName)
            assertEquals(1, success.value.academic.schedule.entries.size)
            assertEquals(
                "Programación Móvil",
                success.value.academic.schedule.entries
                    .single()
                    .subjectName,
            )
            assertEquals(
                "TUESDAY",
                success.value.academic.schedule.entries
                    .single()
                    .dayOfWeek,
            )
        }

    @Test
    fun `network failure without any local schedule remains a failure`() =
        runTest {
            val tokenStore = FakeTokenStore(platformToken("student-1"))
            val cache = FakeCache(emptyMap())
            val repository =
                repository(
                    engine =
                        MockEngine {
                            throw java.io.IOException("Synthetic offline transport")
                        },
                    tokenStore = tokenStore,
                    cache = cache,
                    personalScheduleStore = FakePersonalScheduleStore(emptyList()),
                )

            val result = repository.load()

            assertIs<Outcome.Failure>(result)
        }

    @Test
    fun `unauthorized response clears local session instead of showing stale cache`() =
        runTest {
            val tokenStore = FakeTokenStore(platformToken("student-1"))
            val cache = FakeCache(mapOf("student-1" to cached("student-1", "Ana")))
            val repository =
                repository(
                    engine =
                        MockEngine {
                            respond(content = "{}", status = HttpStatusCode.Unauthorized)
                        },
                    tokenStore = tokenStore,
                    cache = cache,
                )

            val result = repository.load()

            val failure = assertIs<Outcome.Failure>(result)
            val error = assertIs<AppError.Http>(failure.error)
            assertEquals(401, error.status)
            assertNull(tokenStore.token)
            assertNull(cache.lastReadOwner)
        }

    @Test
    fun forbidden_and_missing_authority_responses_do_not_fall_back_to_stale_cache() =
        runTest {
            listOf(HttpStatusCode.Forbidden, HttpStatusCode.NotFound).forEach { status ->
                val cache = FakeCache(mapOf("student-1" to cached("student-1", "Ana")))
                val repository =
                    repository(
                        engine = MockEngine { respond(content = "{}", status = status) },
                        tokenStore = FakeTokenStore(platformToken("student-1")),
                        cache = cache,
                    )
                val result = repository.load()
                val failure = assertIs<Outcome.Failure>(result)
                assertEquals(status.value, assertIs<AppError.Http>(failure.error).status)
                assertNull(cache.lastReadOwner)
            }
        }

    @Test
    fun `remote academic identity must match authenticated subject`() =
        runTest {
            val tokenStore = FakeTokenStore(platformToken("student-1"))
            val cache = FakeCache(emptyMap())
            val repository =
                repository(
                    engine =
                        MockEngine {
                            respond(
                                content =
                                    """
                                    {
                                      "data": {
                                        "student": {"id":"student-2","displayName":"Luis"},
                                        "schedule": {"ownerId":"student-2","entries":[]}
                                      }
                                    }
                                    """.trimIndent(),
                                status = HttpStatusCode.OK,
                                headers =
                                    headersOf(
                                        HttpHeaders.ContentType,
                                        ContentType.Application.Json.toString(),
                                    ),
                            )
                        },
                    tokenStore = tokenStore,
                    cache = cache,
                )

            val result = repository.load()

            val failure = assertIs<Outcome.Failure>(result)
            assertIs<AppError.Serialization>(failure.error)
            assertTrue(cache.values.isEmpty())
        }

    @Test
    fun `HTTP authority failures are explicit before first sync and with legacy caches`() =
        runTest {
            for (initial in listOf(emptyMap(), mapOf("student-1" to cached("student-1", "Ana")))) {
                val cache = FakeCache(initial)
                val repo =
                    repository(
                        MockEngine { respond("{}", HttpStatusCode.ServiceUnavailable) },
                        FakeTokenStore(platformToken("student-1")),
                        cache,
                    )
                val failure = assertIs<Outcome.Failure>(repo.load())
                assertEquals(503, assertIs<AppError.Http>(failure.error).status)
            }
        }

    @Test
    fun `shared repository serializes cache writes before a later revocation`() =
        runTest {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var requests = 0
            val cache = FakeCache(emptyMap())
            val repo =
                repository(
                    MockEngine {
                        requests++
                        if (requests == 1) {
                            started.complete(Unit)
                            release.await()
                            respond(
                                Json.encodeToString(ApiResponse(cached("student-1", "Ana").value)),
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        } else {
                            respond("{}", HttpStatusCode.Forbidden)
                        }
                    },
                    FakeTokenStore(platformToken("student-1")),
                    cache,
                )
            val initial = async(start = CoroutineStart.UNDISPATCHED) { repo.load() }
            started.await()
            val revoked = async(start = CoroutineStart.UNDISPATCHED) { repo.load() }
            release.complete(Unit)
            assertIs<Outcome.Success<AcademicContent>>(initial.await())
            assertEquals(403, assertIs<AppError.Http>(assertIs<Outcome.Failure>(revoked.await()).error).status)
            assertTrue(cache.values.isEmpty())
        }

    private fun repository(
        engine: MockEngine,
        tokenStore: FakeTokenStore,
        cache: FakeCache,
        personalScheduleStore: PersonalScheduleStore? = null,
    ): AcademicRepository =
        AcademicRepository(
            client =
                createApiClient(
                    environment = ApiEnvironment("https://example.test/", "test"),
                    engine = engine,
                ),
            tokenStore = tokenStore,
            cache = cache,
            personalScheduleStore = personalScheduleStore,
        )

    private fun cached(
        userId: String,
        name: String,
    ): CachedAcademicLoad =
        CachedAcademicLoad(
            value =
                AcademicLoadResponse(
                    student = AcademicProfile(userId, name),
                    schedule = AcademicScheduleResponse(userId, emptyList()),
                ),
            updatedAtEpochSeconds = 100,
        )

    private fun platformToken(userId: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("{}".toByteArray())
        val payload =
            encoder.encodeToString(
                """{"sub":"$userId","display_name":"Student","exp":4102444800}"""
                    .toByteArray(),
            )
        return listOf(header, payload, "test").joinToString(".")
    }

    private class FakeTokenStore(
        var token: String?,
    ) : SessionTokenStore {
        override suspend fun readAccessToken(): String? = token

        override suspend fun writeAccessToken(token: String) {
            this.token = token
        }

        override suspend fun clear() {
            token = null
        }
    }

    private class FakePersonalScheduleStore(
        private val items: List<PersonalScheduleItem>,
    ) : PersonalScheduleStore {
        override suspend fun list(ownerId: String): List<PersonalScheduleItem> = items.filter { it.ownerId == ownerId }

        override suspend fun upsert(item: PersonalScheduleItem) = Unit

        override suspend fun replaceBySource(
            ownerId: String,
            source: ScheduleSource,
            items: List<PersonalScheduleItem>,
        ) = Unit

        override suspend fun delete(
            ownerId: String,
            id: String,
        ) = Unit
    }

    private class FakeCache(
        initial: Map<String, CachedAcademicLoad>,
    ) : AcademicSnapshotCache {
        val values = initial.toMutableMap()
        var lastReadOwner: String? = null

        override suspend fun read(ownerId: String): CachedAcademicLoad? {
            lastReadOwner = ownerId
            return values[ownerId]
        }

        override suspend fun write(value: AcademicLoadResponse) {
            values[value.student.id] = CachedAcademicLoad(value, 200)
        }

        override suspend fun clear(ownerId: String) {
            values.remove(ownerId)
        }
    }
}
