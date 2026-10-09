package org.companerodeescuela.core.database

import java.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.companerodeescuela.shared.contracts.AttendanceReasonCode
import org.companerodeescuela.shared.contracts.AttendanceRecordResponse
import org.companerodeescuela.shared.contracts.AttendanceStatus
import org.companerodeescuela.shared.contracts.SchoolNetworkEvidence

enum class LocalAttendanceSyncState {
    PENDING,
    AUTH_REQUIRED,
    SYNCED,
    REVIEW_REQUIRED,
    REJECTED,
}

data class PendingAttendanceOperation(
    val operationId: String,
    val ownerId: String,
    val sessionId: String,
    val deviceTimestampEpochSeconds: Long,
    val qrToken: String?,
    val schoolNetwork: SchoolNetworkEvidence?,
    val attemptCount: Int,
    val nextAttemptAtEpochSeconds: Long,
    val lastErrorCode: String?,
)

data class LocalAttendanceRecord(
    val operationId: String,
    val ownerId: String,
    val sessionId: String,
    val syncState: LocalAttendanceSyncState,
    val attendanceStatus: AttendanceStatus?,
    val reasonCode: AttendanceReasonCode?,
    val updatedAtEpochSeconds: Long,
)

interface AttendanceLocalStore {
    suspend fun enqueue(
        operationId: String,
        ownerId: String,
        sessionId: String,
        deviceTimestampEpochSeconds: Long,
        qrToken: String? = null,
        schoolNetwork: SchoolNetworkEvidence? = null,
    ): LocalAttendanceRecord

    suspend fun nextReady(nowEpochSeconds: Long, ownerId: String): PendingAttendanceOperation?

    suspend fun recordRetry(
        operationId: String,
        nextAttemptAtEpochSeconds: Long,
        errorCode: String,
    )

    suspend fun markAuthRequired(operationId: String)

    suspend fun markRejected(
        operationId: String,
        reasonCode: AttendanceReasonCode,
    )

    suspend fun markSynced(
        operationId: String,
        response: AttendanceRecordResponse,
    )

    suspend fun findLocal(operationId: String): LocalAttendanceRecord?

    fun observe(ownerId: String): Flow<List<LocalAttendanceRecord>>

    suspend fun clearOwner(ownerId: String)
}

internal class RoomAttendanceLocalStore(
    private val dao: AttendanceOutboxDao,
    private val clock: Clock = Clock.systemUTC(),
) : AttendanceLocalStore {

    override suspend fun enqueue(
        operationId: String,
        ownerId: String,
        sessionId: String,
        deviceTimestampEpochSeconds: Long,
        qrToken: String?,
        schoolNetwork: SchoolNetworkEvidence?,
    ): LocalAttendanceRecord {
        require(operationId.isNotBlank()) { "operationId must not be blank" }
        require(ownerId.isNotBlank()) { "ownerId must not be blank" }
        require(sessionId.isNotBlank()) { "sessionId must not be blank" }

        val now = clock.instant().epochSecond
        dao.insertOutbox(
            AttendanceOutboxEntity(
                operationId = operationId,
                ownerId = ownerId,
                sessionId = sessionId,
                deviceTimestampEpochSeconds = deviceTimestampEpochSeconds,
                qrToken = qrToken,
                schoolSsid = schoolNetwork?.ssid,
                schoolBssid = schoolNetwork?.bssid,
                createdAtEpochSeconds = now,
                attemptCount = 0,
                nextAttemptAtEpochSeconds = now,
                lastErrorCode = null,
                state = LocalAttendanceSyncState.PENDING.name,
            ),
        )
        val local = AttendanceLocalRecordEntity(
            operationId = operationId,
            ownerId = ownerId,
            sessionId = sessionId,
            syncState = LocalAttendanceSyncState.PENDING.name,
            attendanceStatus = null,
            reasonCode = null,
            updatedAtEpochSeconds = now,
        )
        dao.upsertLocal(local)
        return local.toDomain()
    }

    override suspend fun nextReady(nowEpochSeconds: Long, ownerId: String): PendingAttendanceOperation? =
        dao.nextReady(nowEpochSeconds, ownerId)?.let { entity ->
            PendingAttendanceOperation(
                operationId = entity.operationId,
                ownerId = entity.ownerId,
                sessionId = entity.sessionId,
                deviceTimestampEpochSeconds = entity.deviceTimestampEpochSeconds,
                qrToken = entity.qrToken,
                schoolNetwork = SchoolNetworkEvidence(
                    ssid = entity.schoolSsid,
                    bssid = entity.schoolBssid,
                ).takeIf { it.ssid != null || it.bssid != null },
                attemptCount = entity.attemptCount,
                nextAttemptAtEpochSeconds = entity.nextAttemptAtEpochSeconds,
                lastErrorCode = entity.lastErrorCode,
            )
        }

    override suspend fun recordRetry(
        operationId: String,
        nextAttemptAtEpochSeconds: Long,
        errorCode: String,
    ) {
        dao.recordFailure(operationId, nextAttemptAtEpochSeconds, errorCode)
    }

    override suspend fun markAuthRequired(operationId: String) {
        val existing = dao.findLocal(operationId) ?: return
        dao.upsertLocal(
            existing.copy(
                syncState = LocalAttendanceSyncState.AUTH_REQUIRED.name,
                updatedAtEpochSeconds = clock.instant().epochSecond,
            ),
        )
    }

    override suspend fun markRejected(
        operationId: String,
        reasonCode: AttendanceReasonCode,
    ) {
        val existing = dao.findLocal(operationId) ?: return
        dao.upsertLocal(
            existing.copy(
                syncState = LocalAttendanceSyncState.REJECTED.name,
                attendanceStatus = AttendanceStatus.REJECTED.name,
                reasonCode = reasonCode.name,
                updatedAtEpochSeconds = clock.instant().epochSecond,
            ),
        )
        dao.deleteOutbox(operationId)
    }

    override suspend fun markSynced(
        operationId: String,
        response: AttendanceRecordResponse,
    ) {
        val existing = dao.findLocal(operationId) ?: return
        val localState = when (response.status) {
            AttendanceStatus.REVIEW_REQUIRED -> LocalAttendanceSyncState.REVIEW_REQUIRED
            AttendanceStatus.REJECTED -> LocalAttendanceSyncState.REJECTED
            AttendanceStatus.VERIFIED,
            AttendanceStatus.LIKELY,
            -> LocalAttendanceSyncState.SYNCED
        }
        dao.upsertLocal(
            existing.copy(
                syncState = localState.name,
                attendanceStatus = response.status.name,
                reasonCode = response.reasonCode.name,
                updatedAtEpochSeconds = clock.instant().epochSecond,
            ),
        )
        dao.deleteOutbox(operationId)
    }

    override suspend fun findLocal(operationId: String): LocalAttendanceRecord? =
        dao.findLocal(operationId)?.toDomain()

    override fun observe(ownerId: String): Flow<List<LocalAttendanceRecord>> =
        dao.observeLocal(ownerId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun clearOwner(ownerId: String) {
        dao.clearOutbox(ownerId)
        dao.clearLocal(ownerId)
    }

    private fun AttendanceLocalRecordEntity.toDomain(): LocalAttendanceRecord =
        LocalAttendanceRecord(
            operationId = operationId,
            ownerId = ownerId,
            sessionId = sessionId,
            syncState = LocalAttendanceSyncState.valueOf(syncState),
            attendanceStatus = attendanceStatus?.let(AttendanceStatus::valueOf),
            reasonCode = reasonCode?.let(AttendanceReasonCode::valueOf),
            updatedAtEpochSeconds = updatedAtEpochSeconds,
        )
}

object AttendanceLocalStoreFactory {
    fun create(database: CompaneroDatabase): AttendanceLocalStore =
        RoomAttendanceLocalStore(database.attendanceOutboxDao())
}
