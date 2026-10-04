package org.companerodeescuela.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * On-device cache/outbox/personal schedule only.
 * This database is never the institutional system of record.
 */
@Database(
    entities = [
        AcademicSnapshotEntity::class,
        AttendanceOutboxEntity::class,
        AttendanceLocalRecordEntity::class,
        PersonalScheduleEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class CompaneroDatabase : RoomDatabase() {
    internal abstract fun academicSnapshotDao(): AcademicSnapshotDao
    internal abstract fun attendanceOutboxDao(): AttendanceOutboxDao
    internal abstract fun personalScheduleDao(): PersonalScheduleDao
}
