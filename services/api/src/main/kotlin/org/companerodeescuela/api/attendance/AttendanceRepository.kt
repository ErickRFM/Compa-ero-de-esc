package org.companerodeescuela.api.attendance

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.companerodeescuela.shared.contracts.AttendanceRecordResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionStatus

sealed interface AttemptWriteResult {
    data class Created(val record: AttendanceRecordResponse) : AttemptWriteResult
    data class Existing(val record: AttendanceRecordResponse) : AttemptWriteResult
    data object OperationConflict : AttemptWriteResult
}

sealed interface SessionWriteResult {
    data class Created(val session: AttendanceSessionResponse) : SessionWriteResult
    data class Existing(val session: AttendanceSessionResponse) : SessionWriteResult
}

interface AttendanceRepository {
    suspend fun createSession(session: AttendanceSessionResponse): SessionWriteResult
    suspend fun findSession(id: String): AttendanceSessionResponse?
    suspend fun findSessionByOccurrence(occurrenceId: String): AttendanceSessionResponse?
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
    private val occurrenceToSession = linkedMapOf<String, String>()
    private val records = linkedMapOf<String, AttendanceRecordResponse>()
    private val operationToRecord = linkedMapOf<String, String>()

    override suspend fun createSession(session: AttendanceSessionResponse): SessionWriteResult =
        mutex.withLock {
            val existing = occurrenceToSession[session.occurrenceId]?.let(sessions::get)
            if (existing != null) {
                return@withLock SessionWriteResult.Existing(existing)
            }
            sessions[session.id] = session
            occurrenceToSession[session.occurrenceId] = session.id
            SessionWriteResult.Created(session)
        }

    override suspend fun findSession(id: String): AttendanceSessionResponse? =
        mutex.withLock { sessions[id] }

    override suspend fun findSessionByOccurrence(occurrenceId: String): AttendanceSessionResponse? =
        mutex.withLock { occurrenceToSession[occurrenceId]?.let(sessions::get) }

    override suspend fun findOpenSessions(): List<AttendanceSessionResponse> =
        mutex.withLock {
            sessions.values.filter { it.status == AttendanceSessionStatus.OPEN }
        }

    override suspend fun replaceSession(session: AttendanceSessionResponse) {
        mutex.withLock {
            sessions[session.id] = session
            occurrenceToSession[session.occurrenceId] = session.id
        }
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

            val existingByRecord = records[record.id]
            if (existingByRecord != null) {
                operationToRecord[record.operationId] = existingByRecord.id
                return@withLock AttemptWriteResult.Existing(existingByRecord)
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
