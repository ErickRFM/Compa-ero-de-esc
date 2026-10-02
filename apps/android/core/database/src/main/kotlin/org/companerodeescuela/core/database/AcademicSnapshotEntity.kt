package org.companerodeescuela.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "academic_snapshots")
internal data class AcademicSnapshotEntity(
    @PrimaryKey
    val cacheKey: String,
    val ownerId: String,
    val payloadJson: String,
    val updatedAtEpochSeconds: Long,
)
