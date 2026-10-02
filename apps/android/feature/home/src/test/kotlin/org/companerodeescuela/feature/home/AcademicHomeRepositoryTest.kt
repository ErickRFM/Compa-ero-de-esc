package org.companerodeescuela.feature.home

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.database.AcademicSnapshotCache
import org.companerodeescuela.core.database.CachedAcademicLoad
import org.companerodeescuela.core.network.ApiEnvironment
import org.companerodeescuela.core.network.createApiClient
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.AcademicLoadResponse
import org.companerodeescuela.shared.contracts.AcademicProfile
import org.companerodeescuela.shared.contracts.AcademicScheduleResponse

class AcademicHomeRepositoryTest {
    @Test
    fun `network failure falls back to cached academic snapshot`() = runTest {
        val client = createApiClient(
            environment = ApiEnvironment("https://example.test/", "test"),
            engine = MockEngine {
                respond(content = "{}", status = HttpStatusCode.ServiceUnavailable)
            },
        )
        val repository = AcademicHomeRepository(
            client = client,
            tokenStore = FakeTokenStore("platform-token"),
            cache = FakeCache(
                CachedAcademicLoad(
                    value = AcademicLoadResponse(
                        student = AcademicProfile("student-1", "Ana"),
                        schedule = AcademicScheduleResponse("student-1", emptyList()),
                    ),
                    updatedAtEpochSeconds = 100,
                ),
            ),
        )
        val result = repository.load()
        val success = assertIs<Outcome.Success<HomeContent>>(result)
        assertTrue(success.value.fromCache)
    }

    private class FakeTokenStore(private val value: String?) : SessionTokenStore {
        override suspend fun readAccessToken(): String? = value
        override suspend fun writeAccessToken(token: String) = Unit
        override suspend fun clear() = Unit
    }

    private class FakeCache(
        private var value: CachedAcademicLoad?,
    ) : AcademicSnapshotCache {
        override suspend fun read(): CachedAcademicLoad? = value
        override suspend fun write(value: AcademicLoadResponse) {
            this.value = CachedAcademicLoad(value, 200)
        }
        override suspend fun clear() {
            value = null
        }
    }
}
