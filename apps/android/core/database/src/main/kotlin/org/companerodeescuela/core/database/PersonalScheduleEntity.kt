package org.companerodeescuela.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "personal_schedule_entries",
    indices = [
        Index(value = ["ownerId"]),
        Index(value = ["ownerId", "dayOfWeek", "startsAt"]),
    ],
)
internal data class PersonalScheduleEntity(
    @PrimaryKey
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
    val source: String,
    val updatedAtEpochSeconds: Long,
)
