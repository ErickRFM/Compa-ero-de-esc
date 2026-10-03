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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

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
