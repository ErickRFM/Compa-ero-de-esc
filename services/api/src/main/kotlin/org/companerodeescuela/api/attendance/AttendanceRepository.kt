package org.companerodeescuela.api.attendance

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.companerodeescuela.shared.contracts.AttendanceRecordResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse

sealed interface AttemptWriteResult {
    data class Created(val record: AttendanceRecordResponse) : AttemptWriteResult
    data class Existing(val record: AttendanceRecordResponse) : AttemptWriteResult
    data object OperationConflict : AttemptWriteResult
}

interface AttendanceRepository {
    suspend fun createSession(session: AttendanceSessionResponse): AttendanceSessionResponse
    suspend fun findSession(id: String): AttendanceSessionResponse?
    suspend fun findOpenSessions(): List<AttendanceSessionResponse>
    suspend fun replaceSession(session: AttendanceSessionResponse)
    suspend fun writeAttempt(record: AttendanceRecordResponse): AttemptWriteResult
    suspend fun recordsForSession(sessionId: String): List<AttendanceRecordResponse>
    suspend fun replaceRecord(record: AttendanceRecordResponse)
    suspend fun findRecord(id: String): AttendanceRecordResponse?
}

class InMemoryAttendanceRepository : AttendanceRepository {
    private val mutex = Mutex()
    private val sessions = linkedMapOf<String, AttendanceSessionResponse>()
    private val records = linkedMapOf<String, AttendanceRecordResponse>()
    private val operationToRecord = linkedMapOf<String, String>()

    override suspend fun createSession(
        session: AttendanceSessionResponse,
    ): AttendanceSessionResponse = mutex.withLock {
        sessions[session.id] = session
        session
    }

    override suspend fun findSession(id: String): AttendanceSessionResponse? =
        mutex.withLock { sessions[id] }

    override suspend fun findOpenSessions(): List<AttendanceSessionResponse> =
        mutex.withLock { sessions.values.filter { it.status.name == "OPEN" } }

    override suspend fun replaceSession(session: AttendanceSessionResponse) {
        mutex.withLock { sessions[session.id] = session }
    }

    override suspend fun writeAttempt(record: AttendanceRecordResponse): AttemptWriteResult =
        mutex.withLock {
            val existingByOperation = operationToRecord[record.operationId]
                ?.let(records::get)
            if (existingByOperation != null) {
                return@withLock if (
                    existingByOperation.sessionId == record.sessionId &&
                    existingByOperation.studentId == record.studentId
                ) {
                    AttemptWriteResult.Existing(existingByOperation)
                } else {
                    AttemptWriteResult.OperationConflict
                }
            }

            val existing = records[record.id]
            if (existing != null) {
                operationToRecord[record.operationId] = existing.id
                return@withLock AttemptWriteResult.Existing(existing)
            }

            records[record.id] = record
            operationToRecord[record.operationId] = record.id
            AttemptWriteResult.Created(record)
        }

    override suspend fun recordsForSession(sessionId: String): List<AttendanceRecordResponse> =
        mutex.withLock {
            records.values.filter { it.sessionId == sessionId }
        }

    override suspend fun replaceRecord(record: AttendanceRecordResponse) {
        mutex.withLock {
            records[record.id] = record
            operationToRecord[record.operationId] = record.id
        }
    }

    override suspend fun findRecord(id: String): AttendanceRecordResponse? =
        mutex.withLock { records[id] }
}
