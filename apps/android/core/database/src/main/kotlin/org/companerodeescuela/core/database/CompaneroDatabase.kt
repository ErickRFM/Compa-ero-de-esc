package org.companerodeescuela.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * On-device cache only. This database is never the system of record.
 */
@Database(
    entities = [AcademicSnapshotEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class CompaneroDatabase : RoomDatabase() {
    internal abstract fun academicSnapshotDao(): AcademicSnapshotDao
}
