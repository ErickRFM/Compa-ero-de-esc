package org.companerodeescuela.api.attendance

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.companerodeescuela.shared.contracts.AttendanceIdentitySource
import org.companerodeescuela.shared.contracts.AttendanceRecordResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionStatus

sealed interface AttemptWriteResult {
    data class Created(
        val record: AttendanceRecordResponse,
    ) : AttemptWriteResult

    data class Existing(
        val record: AttendanceRecordResponse,
    ) : AttemptWriteResult

    data object OperationConflict : AttemptWriteResult
}

enum class AttemptReservationResult {
    ACCEPTED,
    OPERATION_CONFLICT,
    LIMIT_REACHED,
}

sealed interface SessionWriteResult {
    data class Created(
        val session: AttendanceSessionResponse,
    ) : SessionWriteResult

    data class Existing(
        val session: AttendanceSessionResponse,
    ) : SessionWriteResult
}

interface AttendanceRepository {
    suspend fun createSession(session: AttendanceSessionResponse): SessionWriteResult

    suspend fun findSession(id: String): AttendanceSessionResponse?

    suspend fun findSessionByOccurrence(occurrenceId: String): AttendanceSessionResponse?

    suspend fun findOpenSessions(): List<AttendanceSessionResponse>

    suspend fun findSessionsOpenedBy(
        teacherId: String,
        limit: Int = 100,
    ): List<AttendanceSessionResponse>

    suspend fun findOpenSessions(
        courseGroups: Set<Pair<String, String>>,
        institutionId: String?,
        afterEpochSeconds: Long = 0,
        unscopedInstitutionalOnly: Boolean = false,
        limit: Int = MAX_OPEN_SESSION_RESULTS,
    ): List<AttendanceSessionResponse>

    suspend fun replaceSession(session: AttendanceSessionResponse)

    suspend fun writeAttempt(record: AttendanceRecordResponse): AttemptWriteResult

    suspend fun reserveAttempt(
        sessionId: String,
        studentId: String,
        operationId: String,
        requestFingerprint: String,
        maxAttempts: Int,
    ): AttemptReservationResult

    suspend fun replaceRejectedRecord(record: AttendanceRecordResponse): AttendanceRecordResponse

    suspend fun recordsForSession(sessionId: String): List<AttendanceRecordResponse>

    suspend fun replaceRecord(record: AttendanceRecordResponse)

    suspend fun findRecord(id: String): AttendanceRecordResponse?

    suspend fun findStudentSessionRecord(
        sessionId: String,
        studentId: String,
    ): AttendanceRecordResponse?

    suspend fun recordQrNonceUse(
        sessionId: String,
        nonce: String,
        studentId: String,
        expiresAtEpochSeconds: Long,
        nowEpochSeconds: Long,
    ): Long
}

class InMemoryAttendanceRepository : AttendanceRepository {
    private val mutex = Mutex()
    private val sessions = linkedMapOf<String, AttendanceSessionResponse>()
    private val occurrenceToSession = linkedMapOf<String, String>()
    private val records = linkedMapOf<String, AttendanceRecordResponse>()
    private val operationToRecord = linkedMapOf<String, String>()
    private val qrNonceUses = linkedMapOf<Pair<String, String>, MutableMap<String, Long>>()
    private val attemptsByStudent = linkedMapOf<Pair<String, String>, MutableSet<String>>()
    private val attemptOperationOwners = linkedMapOf<String, Pair<Pair<String, String>, String>>()

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

    override suspend fun findSession(id: String): AttendanceSessionResponse? = mutex.withLock { sessions[id] }

    override suspend fun findSessionByOccurrence(occurrenceId: String): AttendanceSessionResponse? =
        mutex.withLock { occurrenceToSession[occurrenceId]?.let(sessions::get) }

    override suspend fun findOpenSessions(): List<AttendanceSessionResponse> =
        mutex.withLock {
            sessions.values.filter { it.status == AttendanceSessionStatus.OPEN }
        }

    override suspend fun findSessionsOpenedBy(
        teacherId: String,
        limit: Int,
    ): List<AttendanceSessionResponse> =
        mutex.withLock {
            sessions.values
                .asSequence()
                .filter { it.openedBy == teacherId }
                .sortedByDescending { it.openedAtEpochSeconds }
                .take(limit.coerceIn(1, 100))
                .toList()
        }

    override suspend fun findOpenSessions(
        courseGroups: Set<Pair<String, String>>,
        institutionId: String?,
        afterEpochSeconds: Long,
        unscopedInstitutionalOnly: Boolean,
        limit: Int,
    ): List<AttendanceSessionResponse> =
        mutex.withLock {
            if (courseGroups.isEmpty()) return@withLock emptyList()
            sessions.values
                .asSequence()
                .filter { it.status == AttendanceSessionStatus.OPEN }
                .filter { (it.courseId to it.groupName) in courseGroups }
                .filter { it.institutionId == institutionId }
                .filter { it.closesAtEpochSeconds > afterEpochSeconds }
                .filter {
                    !unscopedInstitutionalOnly ||
                        (it.identitySource != AttendanceIdentitySource.NATIVE && it.groupId == null)
                }.take(limit.coerceIn(1, MAX_OPEN_SESSION_RESULTS))
                .toList()
        }

    override suspend fun replaceSession(session: AttendanceSessionResponse) {
        mutex.withLock {
            sessions[session.id] = session
            occurrenceToSession[session.occurrenceId] = session.id
        }
    }

    override suspend fun writeAttempt(record: AttendanceRecordResponse): AttemptWriteResult =
        mutex.withLock {
            val existingByOperation =
                operationToRecord[record.operationId]
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

            val existingByStudentSession =
                records.values.firstOrNull { it.sessionId == record.sessionId && it.studentId == record.studentId }
            if (existingByStudentSession != null) {
                operationToRecord[record.operationId] = existingByStudentSession.id
                return@withLock AttemptWriteResult.Existing(existingByStudentSession)
            }

            records[record.id] = record
            operationToRecord[record.operationId] = record.id
            AttemptWriteResult.Created(record)
        }

    override suspend fun reserveAttempt(
        sessionId: String,
        studentId: String,
        operationId: String,
        requestFingerprint: String,
        maxAttempts: Int,
    ): AttemptReservationResult =
        mutex.withLock {
            val key = sessionId to studentId
            val owner = attemptOperationOwners[operationId]
            if (owner != null) {
                return@withLock if (owner == (key to requestFingerprint)) {
                    AttemptReservationResult.ACCEPTED
                } else {
                    AttemptReservationResult.OPERATION_CONFLICT
                }
            }
            val operations = attemptsByStudent.getOrPut(key) { linkedSetOf() }
            if (operations.size >= maxAttempts) return@withLock AttemptReservationResult.LIMIT_REACHED
            operations += operationId
            attemptOperationOwners[operationId] = key to requestFingerprint
            AttemptReservationResult.ACCEPTED
        }

    override suspend fun replaceRejectedRecord(record: AttendanceRecordResponse): AttendanceRecordResponse =
        mutex.withLock {
            val current = records[record.id] ?: return@withLock record
            if (current.status == org.companerodeescuela.shared.contracts.AttendanceStatus.REJECTED && current.reviewedBy == null) {
                records[record.id] = record
                operationToRecord[record.operationId] = record.id
                record
            } else {
                current
            }
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

    override suspend fun findRecord(id: String): AttendanceRecordResponse? = mutex.withLock { records[id] }

    override suspend fun findStudentSessionRecord(
        sessionId: String,
        studentId: String,
    ): AttendanceRecordResponse? = mutex.withLock { records.values.firstOrNull { it.sessionId == sessionId && it.studentId == studentId } }

    override suspend fun recordQrNonceUse(
        sessionId: String,
        nonce: String,
        studentId: String,
        expiresAtEpochSeconds: Long,
        nowEpochSeconds: Long,
    ): Long =
        mutex.withLock {
            val key = sessionId to nonce
            val students = qrNonceUses.getOrPut(key) { linkedMapOf() }
            students.entries.removeIf { it.value <= nowEpochSeconds }
            if (expiresAtEpochSeconds > nowEpochSeconds) students.putIfAbsent(studentId, expiresAtEpochSeconds)
            if (students.isEmpty()) qrNonceUses.remove(key)
            students.size.toLong()
        }
}

const val MAX_OPEN_SESSION_RESULTS = 100
