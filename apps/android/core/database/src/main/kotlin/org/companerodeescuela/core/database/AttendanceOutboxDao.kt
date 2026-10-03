package org.companerodeescuela.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
internal interface AttendanceOutboxDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOutbox(value: AttendanceOutboxEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLocal(value: AttendanceLocalRecordEntity)

    @Query(
        """
        SELECT * FROM attendance_outbox
        WHERE state = 'PENDING' AND nextAttemptAtEpochSeconds <= :nowEpochSeconds
        ORDER BY createdAtEpochSeconds ASC
        LIMIT 1
        """,
    )
    suspend fun nextReady(nowEpochSeconds: Long): AttendanceOutboxEntity?

    @Query("SELECT * FROM attendance_outbox WHERE operationId = :operationId LIMIT 1")
    suspend fun findOutbox(operationId: String): AttendanceOutboxEntity?

    @Query(
        """
        UPDATE attendance_outbox
        SET attemptCount = attemptCount + 1,
            nextAttemptAtEpochSeconds = :nextAttemptAtEpochSeconds,
            lastErrorCode = :lastErrorCode
        WHERE operationId = :operationId
        """,
    )
    suspend fun recordFailure(
        operationId: String,
        nextAttemptAtEpochSeconds: Long,
        lastErrorCode: String,
    )

    @Query(
        """
        UPDATE attendance_outbox
        SET state = :state,
            lastErrorCode = :errorCode
        WHERE operationId = :operationId
        """,
    )
    suspend fun updateState(
        operationId: String,
        state: String,
        errorCode: String?,
    )

    @Query("DELETE FROM attendance_outbox WHERE operationId = :operationId")
    suspend fun deleteOutbox(operationId: String)

    @Query(
        """
        SELECT * FROM attendance_local_records
        WHERE ownerId = :ownerId
        ORDER BY updatedAtEpochSeconds DESC
        """,
    )
    fun observeLocal(ownerId: String): Flow<List<AttendanceLocalRecordEntity>>

    @Query(
        """
        SELECT * FROM attendance_local_records
        WHERE operationId = :operationId
        LIMIT 1
        """,
    )
    suspend fun findLocal(operationId: String): AttendanceLocalRecordEntity?

    @Query("DELETE FROM attendance_local_records WHERE ownerId = :ownerId")
    suspend fun clearLocal(ownerId: String)

    @Query("DELETE FROM attendance_outbox WHERE ownerId = :ownerId")
    suspend fun clearOutbox(ownerId: String)
}
