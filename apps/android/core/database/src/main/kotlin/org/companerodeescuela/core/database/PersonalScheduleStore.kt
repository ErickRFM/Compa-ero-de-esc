package org.companerodeescuela.core.database

import org.companerodeescuela.shared.contracts.ScheduleRecurrence
import org.companerodeescuela.shared.contracts.ScheduleSource

data class PersonalScheduleItem(
    val id: String,
    val ownerId: String,
    val subjectCode: String,
    val subjectName: String,
    val groupName: String,
    val teacherName: String,
    val dayOfWeek: String,
    val startsAt: String,
    val endsAt: String,
    val classroomName: String?,
    val buildingName: String?,
    val source: ScheduleSource,
    val recurrence: ScheduleRecurrence = ScheduleRecurrence.WEEKLY,
    val seriesId: String? = null,
    val effectiveDate: String? = null,
    val updatedAtEpochSeconds: Long,
)

interface PersonalScheduleStore {
    suspend fun list(ownerId: String): List<PersonalScheduleItem>
    suspend fun upsert(item: PersonalScheduleItem)
    suspend fun replaceBySource(
        ownerId: String,
        source: ScheduleSource,
        items: List<PersonalScheduleItem>,
    )
    suspend fun delete(ownerId: String, id: String)
}

internal class RoomPersonalScheduleStore(
    private val dao: PersonalScheduleDao,
) : PersonalScheduleStore {
    override suspend fun list(ownerId: String): List<PersonalScheduleItem> =
        dao.list(ownerId).map(PersonalScheduleEntity::toItem)

    override suspend fun upsert(item: PersonalScheduleItem) {
        dao.upsert(item.toEntity())
    }

    override suspend fun replaceBySource(
        ownerId: String,
        source: ScheduleSource,
        items: List<PersonalScheduleItem>,
    ) {
        require(items.all { it.ownerId == ownerId && it.source == source })
        dao.replaceBySource(
            ownerId = ownerId,
            source = source.name,
            entries = items.map(PersonalScheduleItem::toEntity),
        )
    }

    override suspend fun delete(ownerId: String, id: String) {
        dao.delete(ownerId, id)
    }
}

object PersonalScheduleStoreFactory {
    fun create(database: CompaneroDatabase): PersonalScheduleStore =
        RoomPersonalScheduleStore(database.personalScheduleDao())
}

private fun PersonalScheduleEntity.toItem(): PersonalScheduleItem =
    PersonalScheduleItem(
        id = id,
        ownerId = ownerId,
        subjectCode = subjectCode,
        subjectName = subjectName,
        groupName = groupName,
        teacherName = teacherName,
        dayOfWeek = dayOfWeek,
        startsAt = startsAt,
        endsAt = endsAt,
        classroomName = classroomName,
        buildingName = buildingName,
        source = runCatching { ScheduleSource.valueOf(source) }
            .getOrDefault(ScheduleSource.MANUAL),
        recurrence = runCatching { ScheduleRecurrence.valueOf(recurrence) }
            .getOrDefault(ScheduleRecurrence.WEEKLY),
        seriesId = seriesId,
        effectiveDate = effectiveDate,
        updatedAtEpochSeconds = updatedAtEpochSeconds,
    )

private fun PersonalScheduleItem.toEntity(): PersonalScheduleEntity =
    PersonalScheduleEntity(
        id = id,
        ownerId = ownerId,
        subjectCode = subjectCode,
        subjectName = subjectName,
        groupName = groupName,
        teacherName = teacherName,
        dayOfWeek = dayOfWeek,
        startsAt = startsAt,
        endsAt = endsAt,
        classroomName = classroomName,
        buildingName = buildingName,
        source = source.name,
        recurrence = recurrence.name,
        seriesId = seriesId,
        effectiveDate = effectiveDate,
        updatedAtEpochSeconds = updatedAtEpochSeconds,
    )
