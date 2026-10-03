package org.companerodeescuela.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attendance_outbox",
    indices = [
        Index(value = ["ownerId", "state"]),
        Index(value = ["nextAttemptAtEpochSeconds"]),
    ],
)
internal data class AttendanceOutboxEntity(
    @PrimaryKey
    val operationId: String,
    val ownerId: String,
    val sessionId: String,
    val deviceTimestampEpochSeconds: Long,
    val qrToken: String?,
    val createdAtEpochSeconds: Long,
    val attemptCount: Int,
    val nextAttemptAtEpochSeconds: Long,
    val lastErrorCode: String?,
    val state: String,
)

@Entity(
    tableName = "attendance_local_records",
    indices = [
        Index(value = ["ownerId", "updatedAtEpochSeconds"]),
        Index(value = ["sessionId"]),
    ],
)
internal data class AttendanceLocalRecordEntity(
    @PrimaryKey
    val operationId: String,
    val ownerId: String,
    val sessionId: String,
    val syncState: String,
    val attendanceStatus: String?,
    val reasonCode: String?,
    val updatedAtEpochSeconds: Long,
)
