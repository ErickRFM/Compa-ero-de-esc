package org.companerodeescuela.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * On-device cache/outbox only. This database is never the system of record.
 */
@Database(
    entities = [
        AcademicSnapshotEntity::class,
        AttendanceOutboxEntity::class,
        AttendanceLocalRecordEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class CompaneroDatabase : RoomDatabase() {
    internal abstract fun academicSnapshotDao(): AcademicSnapshotDao
    internal abstract fun attendanceOutboxDao(): AttendanceOutboxDao
}
