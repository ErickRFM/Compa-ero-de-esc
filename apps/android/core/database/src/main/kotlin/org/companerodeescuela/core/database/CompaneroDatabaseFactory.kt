package org.companerodeescuela.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object CompaneroDatabaseFactory {
    fun create(context: Context): CompaneroDatabase =
        Room.databaseBuilder(
            context.applicationContext,
            CompaneroDatabase::class.java,
            "companero-cache.db",
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .build()

    internal val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE attendance_outbox ADD COLUMN schoolSsid TEXT",
            )
            db.execSQL(
                "ALTER TABLE attendance_outbox ADD COLUMN schoolBssid TEXT",
            )
        }
    }

    internal val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE personal_schedule_entries ADD COLUMN recurrence TEXT NOT NULL DEFAULT 'WEEKLY'",
            )
            db.execSQL(
                "ALTER TABLE personal_schedule_entries ADD COLUMN seriesId TEXT",
            )
            db.execSQL(
                "ALTER TABLE personal_schedule_entries ADD COLUMN effectiveDate TEXT",
            )
        }
    }

    internal val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS personal_schedule_entries (
                    id TEXT NOT NULL,
                    ownerId TEXT NOT NULL,
                    subjectCode TEXT NOT NULL,
                    subjectName TEXT NOT NULL,
                    groupName TEXT NOT NULL,
                    teacherName TEXT NOT NULL,
                    dayOfWeek TEXT NOT NULL,
                    startsAt TEXT NOT NULL,
                    endsAt TEXT NOT NULL,
                    classroomName TEXT,
                    buildingName TEXT,
                    source TEXT NOT NULL,
                    updatedAtEpochSeconds INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_personal_schedule_entries_ownerId " +
                    "ON personal_schedule_entries(ownerId)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_personal_schedule_entries_ownerId_dayOfWeek_startsAt " +
                    "ON personal_schedule_entries(ownerId, dayOfWeek, startsAt)",
            )
        }
    }

    internal val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE attendance_outbox ADD COLUMN qrToken TEXT",
            )
        }
    }

    internal val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS attendance_outbox (
                    operationId TEXT NOT NULL,
                    ownerId TEXT NOT NULL,
                    sessionId TEXT NOT NULL,
                    deviceTimestampEpochSeconds INTEGER NOT NULL,
                    createdAtEpochSeconds INTEGER NOT NULL,
                    attemptCount INTEGER NOT NULL,
                    nextAttemptAtEpochSeconds INTEGER NOT NULL,
                    lastErrorCode TEXT,
                    state TEXT NOT NULL,
                    PRIMARY KEY(operationId)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_attendance_outbox_ownerId_state " +
                    "ON attendance_outbox(ownerId, state)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_attendance_outbox_nextAttemptAtEpochSeconds " +
                    "ON attendance_outbox(nextAttemptAtEpochSeconds)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS attendance_local_records (
                    operationId TEXT NOT NULL,
                    ownerId TEXT NOT NULL,
                    sessionId TEXT NOT NULL,
                    syncState TEXT NOT NULL,
                    attendanceStatus TEXT,
                    reasonCode TEXT,
                    updatedAtEpochSeconds INTEGER NOT NULL,
                    PRIMARY KEY(operationId)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_attendance_local_records_ownerId_updatedAtEpochSeconds " +
                    "ON attendance_local_records(ownerId, updatedAtEpochSeconds)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_attendance_local_records_sessionId " +
                    "ON attendance_local_records(sessionId)",
            )
        }
    }
}
