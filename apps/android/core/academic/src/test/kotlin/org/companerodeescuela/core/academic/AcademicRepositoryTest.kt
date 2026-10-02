package org.companerodeescuela.core.academic

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.database.AcademicSnapshotCache
import org.companerodeescuela.core.database.CachedAcademicLoad
import org.companerodeescuela.core.network.ApiEnvironment
import org.companerodeescuela.core.network.createApiClient
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.AcademicLoadResponse
import org.companerodeescuela.shared.contracts.AcademicProfile
import org.companerodeescuela.shared.contracts.AcademicScheduleResponse

class AcademicRepositoryTest {

    @Test
    fun `network failure falls back only to active student's cache`() = runTest {
        val tokenStore = FakeTokenStore(platformToken("student-1"))
        val cache = FakeCache(
            mapOf(
                "student-1" to cached("student-1", "Ana"),
                "student-2" to cached("student-2", "Luis"),
            ),
        )
        val repository = repository(
            engine = MockEngine {
                respond(content = "{}", status = HttpStatusCode.ServiceUnavailable)
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
    fun `unauthorized response clears local session instead of showing stale cache`() = runTest {
        val tokenStore = FakeTokenStore(platformToken("student-1"))
        val cache = FakeCache(mapOf("student-1" to cached("student-1", "Ana")))
        val repository = repository(
            engine = MockEngine {
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
    fun `remote academic identity must match authenticated subject`() = runTest {
        val tokenStore = FakeTokenStore(platformToken("student-1"))
        val cache = FakeCache(emptyMap())
        val repository = repository(
            engine = MockEngine {
                respond(
                    content = """
                        {
                          "data": {
                            "student": {"id":"student-2","displayName":"Luis"},
                            "schedule": {"ownerId":"student-2","entries":[]}
                          }
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(
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

    private fun repository(
        engine: MockEngine,
        tokenStore: FakeTokenStore,
        cache: FakeCache,
    ): AcademicRepository = AcademicRepository(
        client = createApiClient(
            environment = ApiEnvironment("https://example.test/", "test"),
            engine = engine,
        ),
        tokenStore = tokenStore,
        cache = cache,
    )

    private fun cached(userId: String, name: String): CachedAcademicLoad =
        CachedAcademicLoad(
            value = AcademicLoadResponse(
                student = AcademicProfile(userId, name),
                schedule = AcademicScheduleResponse(userId, emptyList()),
            ),
            updatedAtEpochSeconds = 100,
        )

    private fun platformToken(userId: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("{}".toByteArray())
        val payload = encoder.encodeToString(
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
