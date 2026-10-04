package org.companerodeescuela.core.academic

import java.time.Clock
import java.time.LocalTime
import java.util.UUID
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.database.PersonalScheduleItem
import org.companerodeescuela.core.database.PersonalScheduleStore
import org.companerodeescuela.core.security.SessionTokenInspector
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.ScheduleEntry
import org.companerodeescuela.shared.contracts.ScheduleSource

data class PersonalScheduleDraft(
    val id: String? = null,
    val subjectCode: String = "",
    val subjectName: String,
    val groupName: String = "",
    val teacherName: String = "",
    val dayOfWeek: String,
    val startsAt: String,
    val endsAt: String,
    val classroomName: String? = null,
    val buildingName: String? = null,
    val source: ScheduleSource = ScheduleSource.MANUAL,
)

class PersonalScheduleRepository(
    private val tokenStore: SessionTokenStore,
    private val store: PersonalScheduleStore,
    private val clock: Clock = Clock.systemUTC(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun list(): Outcome<List<ScheduleEntry>> {
        val ownerId = ownerId() ?: return Outcome.Failure(AppError.Http(status = 401))
        return runCatching {
            store.list(ownerId).map(PersonalScheduleItem::toScheduleEntry)
        }.fold(
            onSuccess = { Outcome.Success(it) },
            onFailure = {
                Outcome.Failure(
                    AppError.Storage(
                        "Could not read personal schedule: " + it::class.simpleName,
                    ),
                )
            },
        )
    }

    suspend fun save(draft: PersonalScheduleDraft): Outcome<ScheduleEntry> {
        val ownerId = ownerId() ?: return Outcome.Failure(AppError.Http(status = 401))
        val item = normalize(ownerId, draft)
            ?: return Outcome.Failure(AppError.Http(status = 422))
        return runCatching {
            store.upsert(item)
            item.toScheduleEntry()
        }.fold(
            onSuccess = { Outcome.Success(it) },
            onFailure = {
                Outcome.Failure(
                    AppError.Storage(
                        "Could not save personal schedule: " + it::class.simpleName,
                    ),
                )
            },
        )
    }

    suspend fun replace(
        source: ScheduleSource,
        drafts: List<PersonalScheduleDraft>,
    ): Outcome<Unit> {
        if (source == ScheduleSource.INSTITUTIONAL) {
            return Outcome.Failure(AppError.Http(status = 422))
        }
        val ownerId = ownerId() ?: return Outcome.Failure(AppError.Http(status = 401))
        val items = drafts.map {
            normalize(ownerId, it.copy(source = source))
                ?: return Outcome.Failure(AppError.Http(status = 422))
        }
        return runCatching {
            store.replaceBySource(ownerId, source, items)
        }.fold(
            onSuccess = { Outcome.Success(Unit) },
            onFailure = {
                Outcome.Failure(
                    AppError.Storage(
                        "Could not replace personal schedule: " + it::class.simpleName,
                    ),
                )
            },
        )
    }

    suspend fun delete(id: String): Outcome<Unit> {
        val ownerId = ownerId() ?: return Outcome.Failure(AppError.Http(status = 401))
        return runCatching { store.delete(ownerId, id) }.fold(
            onSuccess = { Outcome.Success(Unit) },
            onFailure = {
                Outcome.Failure(
                    AppError.Storage(
                        "Could not delete personal schedule: " + it::class.simpleName,
                    ),
                )
            },
        )
    }

    private suspend fun ownerId(): String? {
        val token = runCatching { tokenStore.readAccessToken() }.getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: return null
        if (!SessionTokenInspector.isUsable(token, clock)) return null
        return SessionTokenInspector.inspect(token)?.userId
    }

    private fun normalize(
        ownerId: String,
        draft: PersonalScheduleDraft,
    ): PersonalScheduleItem? {
        val day = draft.dayOfWeek.trim().uppercase()
        if (day !in VALID_DAYS) return null
        val start = normalizeTime(draft.startsAt) ?: return null
        val end = normalizeTime(draft.endsAt) ?: return null
        if (!LocalTime.parse(start).isBefore(LocalTime.parse(end))) return null
        val subject = draft.subjectName.trim()
        if (subject.isBlank()) return null

        return PersonalScheduleItem(
            id = draft.id?.takeIf(String::isNotBlank) ?: newId(),
            ownerId = ownerId,
            subjectCode = draft.subjectCode.trim(),
            subjectName = subject,
            groupName = draft.groupName.trim(),
            teacherName = draft.teacherName.trim(),
            dayOfWeek = day,
            startsAt = start,
            endsAt = end,
            classroomName = draft.classroomName?.trim()?.takeIf(String::isNotBlank),
            buildingName = draft.buildingName?.trim()?.takeIf(String::isNotBlank),
            source = draft.source,
            updatedAtEpochSeconds = clock.instant().epochSecond,
        )
    }

    private fun normalizeTime(raw: String): String? {
        val candidate = raw.trim().replace('.', ':')
        val parsed = runCatching { LocalTime.parse(candidate) }.getOrNull() ?: return null
        return "%02d:%02d".format(parsed.hour, parsed.minute)
    }

    private companion object {
        val VALID_DAYS = setOf(
            "MONDAY",
            "TUESDAY",
            "WEDNESDAY",
            "THURSDAY",
            "FRIDAY",
            "SATURDAY",
            "SUNDAY",
        )
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
        campusName = null,
        source = source,
    )
