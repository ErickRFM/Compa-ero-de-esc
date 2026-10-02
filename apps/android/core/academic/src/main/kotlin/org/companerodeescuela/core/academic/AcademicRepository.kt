package org.companerodeescuela.core.academic

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import java.time.Clock
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.database.AcademicSnapshotCache
import org.companerodeescuela.core.network.apiCall
import org.companerodeescuela.core.network.requireBody
import org.companerodeescuela.core.security.PlatformSessionClaims
import org.companerodeescuela.core.security.SessionTokenInspector
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.AcademicLoadResponse
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.ScheduleEntry

data class AcademicContent(
    val academic: AcademicLoadResponse,
    val fromCache: Boolean,
    val updatedAtEpochSeconds: Long,
)

/**
 * Single owner of academic network + cache behavior.
 *
 * Features never decide independently which student's cache to read. The
 * active platform session scopes every lookup, and Room remains available when
 * the institutional dependency or network is temporarily unavailable.
 */
class AcademicRepository(
    private val client: HttpClient,
    private val tokenStore: SessionTokenStore,
    private val cache: AcademicSnapshotCache,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun load(): Outcome<AcademicContent> {
        val session = activeSession() ?: return Outcome.Failure(AppError.Http(status = 401))
        val (token, claims) = session

        val remote = apiCall {
            client.get("academic/load") {
                bearerAuth(token)
            }.requireBody<ApiResponse<AcademicLoadResponse>>()
        }.map { it.data }

        return when (remote) {
            is Outcome.Success -> {
                val value = remote.value
                if (value.student.id != claims.userId || value.schedule.ownerId != claims.userId) {
                    Outcome.Failure(
                        AppError.Serialization(
                            technicalDetail = "Academic response owner did not match authenticated subject",
                        ),
                    )
                } else {
                    try {
                        cache.write(value)
                    } catch (_: Exception) {
                        // Fresh remote data is still usable when a cache write fails.
                    }
                    Outcome.Success(
                        AcademicContent(
                            academic = value,
                            fromCache = false,
                            updatedAtEpochSeconds = clock.instant().epochSecond,
                        ),
                    )
                }
            }
            is Outcome.Failure -> {
                if (remote.error is AppError.Http && remote.error.status == 401) {
                    tokenStore.clear()
                    remote
                } else {
                    val cached = runCatching { cache.read(claims.userId) }.getOrNull()
                    if (cached != null) {
                        Outcome.Success(
                            AcademicContent(
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

    suspend fun readWeeklySchedule(): Outcome<List<ScheduleEntry>> {
        val session = activeSession() ?: return Outcome.Failure(AppError.Http(status = 401))
        val cached = runCatching { cache.read(session.second.userId) }
            .getOrElse {
                return Outcome.Failure(
                    AppError.Unknown(
                        technicalDetail = "Could not read academic cache: " + it::class.simpleName,
                    ),
                )
            }

        return Outcome.Success(cached?.value?.schedule?.entries.orEmpty())
    }

    private suspend fun activeSession(): Pair<String, PlatformSessionClaims>? {
        val token = runCatching { tokenStore.readAccessToken() }.getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: return null
        if (!SessionTokenInspector.isUsable(token, clock)) {
            runCatching { tokenStore.clear() }
            return null
        }
        val claims = SessionTokenInspector.inspect(token) ?: run {
            runCatching { tokenStore.clear() }
            return null
        }
        return token to claims
    }
}
