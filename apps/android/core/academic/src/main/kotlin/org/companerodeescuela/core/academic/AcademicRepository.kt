package org.companerodeescuela.core.academic

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import java.time.Clock
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.database.AcademicSnapshotCache
import org.companerodeescuela.core.database.PersonalScheduleItem
import org.companerodeescuela.core.database.PersonalScheduleStore
import org.companerodeescuela.core.network.apiCall
import org.companerodeescuela.core.network.requireBody
import org.companerodeescuela.core.network.SessionRefreshCoordinator
import org.companerodeescuela.core.security.PlatformSessionClaims
import org.companerodeescuela.core.security.SessionTokenInspector
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.AcademicLoadResponse
import org.companerodeescuela.shared.contracts.AcademicProfile
import org.companerodeescuela.shared.contracts.AcademicScheduleResponse
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.ScheduleEntry

data class AcademicContent(
    val academic: AcademicLoadResponse,
    val fromCache: Boolean,
    val updatedAtEpochSeconds: Long,
)

/**
 * Single owner of academic network + institutional cache behavior.
 *
 * Personal schedule rows are stored separately and merged only for presentation.
 * They never become institutional enrollment and never grant attendance authority.
 */
class AcademicRepository(
    private val client: HttpClient,
    private val tokenStore: SessionTokenStore,
    private val cache: AcademicSnapshotCache,
    private val personalScheduleStore: PersonalScheduleStore? = null,
    private val clock: Clock = Clock.systemUTC(),
    private val refreshCoordinator: SessionRefreshCoordinator =
        SessionRefreshCoordinator(client, tokenStore, clock),
) {
    suspend fun load(): Outcome<AcademicContent> {
        val session = activeSession() ?: return Outcome.Failure(AppError.Http(status = 401))
        val (token, claims) = session

        val remote = refreshCoordinator.execute(token) { accessToken ->
            apiCall {
                client.get("academic/load") {
                    bearerAuth(accessToken)
                }.requireBody<ApiResponse<AcademicLoadResponse>>()
            }.map { it.data }
        }

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
                        // Fresh institutional data remains usable when cache write fails.
                    }
                    Outcome.Success(
                        AcademicContent(
                            academic = mergePersonal(value, claims.userId),
                            fromCache = false,
                            updatedAtEpochSeconds = clock.instant().epochSecond,
                        ),
                    )
                }
            }
            is Outcome.Failure -> {
                val error = remote.error
                if (error is AppError.Http && error.status == 401) {
                    remote
                } else {
                    val cached = runCatching { cache.read(claims.userId) }.getOrNull()
                    if (cached != null) {
                        Outcome.Success(
                            AcademicContent(
                                academic = mergePersonal(cached.value, claims.userId),
                                fromCache = true,
                                updatedAtEpochSeconds = cached.updatedAtEpochSeconds,
                            ),
                        )
                    } else {
                        localOnlyContent(claims) ?: remote
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

        val institutional = cached?.value?.schedule?.entries.orEmpty()
        val personal = personalEntries(session.second.userId)
        return Outcome.Success(mergeEntries(institutional, personal))
    }

    /**
     * Keeps the product usable when the institutional endpoint is unavailable
     * before we have ever written an institutional cache. PDF/image/manual
     * imports live in the personal schedule store and are a valid source for
     * Today/Agenda presentation, but never become institutional enrollment.
     */
    private suspend fun localOnlyContent(
        claims: PlatformSessionClaims,
    ): Outcome.Success<AcademicContent>? {
        val personal = personalEntries(claims.userId)
        if (personal.isEmpty()) return null

        val local = AcademicLoadResponse(
            student = AcademicProfile(
                id = claims.userId,
                displayName = claims.displayName ?: "Estudiante",
            ),
            schedule = AcademicScheduleResponse(
                ownerId = claims.userId,
                entries = mergeEntries(emptyList(), personal),
            ),
        )
        return Outcome.Success(
            AcademicContent(
                academic = local,
                fromCache = true,
                updatedAtEpochSeconds = clock.instant().epochSecond,
            ),
        )
    }

    private suspend fun mergePersonal(
        value: AcademicLoadResponse,
        ownerId: String,
    ): AcademicLoadResponse {
        val personal = personalEntries(ownerId)
        if (personal.isEmpty()) return value
        return value.copy(
            schedule = value.schedule.copy(
                entries = mergeEntries(value.schedule.entries, personal),
            ),
        )
    }

    private suspend fun personalEntries(ownerId: String): List<ScheduleEntry> {
        val store = personalScheduleStore ?: return emptyList()
        return runCatching {
            store.list(ownerId).map(PersonalScheduleItem::toScheduleEntry)
        }.getOrDefault(emptyList())
    }

    private fun mergeEntries(
        institutional: List<ScheduleEntry>,
        personal: List<ScheduleEntry>,
    ): List<ScheduleEntry> {
        val institutionalKeys = institutional.map(::scheduleKey).toSet()
        return (institutional + personal.filter { scheduleKey(it) !in institutionalKeys })
            .sortedWith(
                compareBy<ScheduleEntry>(
                    { dayOrder(it.dayOfWeek) },
                    { it.startsAt },
                    { it.subjectName },
                ),
            )
    }

    private suspend fun activeSession(): Pair<String, PlatformSessionClaims>? {
        val token = when (val result = refreshCoordinator.currentAccessToken()) {
            is Outcome.Success -> result.value
            is Outcome.Failure -> return null
        }
        val claims = SessionTokenInspector.inspect(token) ?: run {
            runCatching { tokenStore.clear() }
            return null
        }
        return token to claims
    }
}

private fun PersonalScheduleItem.toScheduleEntry(): ScheduleEntry =
    ScheduleEntry(
        courseId = id,
        subjectCode = subjectCode.ifBlank { "PERSONAL" },
        subjectName = subjectName,
        groupName = groupName,
        teacherName = teacherName,
        dayOfWeek = dayOfWeek,
        startsAt = startsAt,
        endsAt = endsAt,
        classroomName = classroomName,
        buildingName = buildingName,
        source = source,
        recurrence = recurrence,
        seriesId = seriesId,
        effectiveDate = effectiveDate,
    )

private fun scheduleKey(entry: ScheduleEntry): String =
    listOf(
        entry.dayOfWeek.trim().uppercase(),
        entry.startsAt.trim(),
        entry.endsAt.trim(),
        entry.subjectName.trim().lowercase(),
    ).joinToString("|")

private fun dayOrder(day: String): Int = when (day) {
    "MONDAY" -> 1
    "TUESDAY" -> 2
    "WEDNESDAY" -> 3
    "THURSDAY" -> 4
    "FRIDAY" -> 5
    "SATURDAY" -> 6
    "SUNDAY" -> 7
    else -> 8
}
