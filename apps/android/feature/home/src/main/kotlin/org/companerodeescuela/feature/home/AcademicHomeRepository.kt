package org.companerodeescuela.feature.home

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import java.time.Clock
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.database.AcademicSnapshotCache
import org.companerodeescuela.core.network.apiCall
import org.companerodeescuela.core.network.requireBody
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.AcademicLoadResponse
import org.companerodeescuela.shared.contracts.ApiResponse

data class HomeContent(
    val academic: AcademicLoadResponse,
    val fromCache: Boolean,
    val updatedAtEpochSeconds: Long,
)

class AcademicHomeRepository(
    private val client: HttpClient,
    private val tokenStore: SessionTokenStore,
    private val cache: AcademicSnapshotCache,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun load(): Outcome<HomeContent> {
        val token = try {
            tokenStore.readAccessToken()
        } catch (error: Exception) {
            return Outcome.Failure(
                AppError.Unknown("Could not read session: " + error::class.simpleName),
            )
        }

        if (token.isNullOrBlank()) {
            return Outcome.Failure(AppError.Http(status = 401))
        }

        val remote = apiCall {
            client.get("academic/load") {
                bearerAuth(token)
            }.requireBody<ApiResponse<AcademicLoadResponse>>()
        }.map { it.data }

        return when (remote) {
            is Outcome.Success -> {
                try {
                    cache.write(remote.value)
                } catch (_: Exception) {
                    // Fresh data remains usable even if a local cache write fails.
                }
                Outcome.Success(
                    HomeContent(
                        academic = remote.value,
                        fromCache = false,
                        updatedAtEpochSeconds = clock.instant().epochSecond,
                    ),
                )
            }
            is Outcome.Failure -> {
                val cached = runCatching { cache.read() }.getOrNull()
                if (cached != null) {
                    Outcome.Success(
                        HomeContent(
                            academic = cached.value,
                            fromCache = true,
                            updatedAtEpochSeconds = cached.updatedAtEpochSeconds,
                        ),
                    )
                } else {
                    remote
                }
            }
        }
    }
}
