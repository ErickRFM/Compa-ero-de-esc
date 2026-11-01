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

/** Private durable continuation of an already-authorized teacher closure. Never an API DTO. */
data class AttendanceClosureWork(
    val session: AttendanceSessionResponse,
    val studentIds: List<String>,
)

interface AttendanceRepository {
    suspend fun institutionOverview(
        institutionId: String,
        atEpochSeconds: Long,
        sessionLimit: Int = MAX_INSTITUTION_ATTENDANCE_SESSIONS,
    ): AttendanceInstitutionOverview

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

    suspend fun closeSessionIfOpen(
        sessionId: String,
        actorId: String,
        closedByTeacher: Boolean,
        atEpochSeconds: Long,
        authorizedStudentIds: List<String>? = null,
    ): AttendanceSessionResponse?

    suspend fun pendingClosures(limit: Int = 100): List<AttendanceClosureWork>

    suspend fun closureWork(sessionId: String): AttendanceClosureWork?

    suspend fun deferClosure(sessionId: String)

    suspend fun completeClosure(sessionId: String)

    suspend fun teacherHistoryPage(
        teacherId: String,
        page: Int,
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

    /** Bounded candidate history; callers must authorize each stored session's current scope. */
    suspend fun recordsForStudent(
        studentId: String,
        limit: Int = MAX_STUDENT_HISTORY_RESULTS,
    ): List<AttendanceRecordResponse>

    suspend fun replaceRecord(record: AttendanceRecordResponse)

    /** Stable identity order allows complete repeated scans, including old corrected records. */
    suspend fun studentHistoryPage(
        studentId: String,
        page: Int,
    ): List<AttendanceRecordResponse>

    /** Atomic across server instances. A stale reviewer must reload before deciding again. */
    suspend fun compareAndSetRecord(
        record: AttendanceRecordResponse,
        expectedRevision: Long,
    ): Boolean

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
    private val closureRosters = linkedMapOf<String, List<String>>()
    private val occurrenceToSession = linkedMapOf<String, String>()
    private val records = linkedMapOf<String, AttendanceRecordResponse>()
    private val operationToRecord = linkedMapOf<String, String>()
    private val qrNonceUses = linkedMapOf<Pair<String, String>, MutableMap<String, Long>>()
    private val attemptsByStudent = linkedMapOf<Pair<String, String>, MutableSet<String>>()
    private val attemptOperationOwners = linkedMapOf<String, Pair<Pair<String, String>, String>>()

    override suspend fun institutionOverview(
        institutionId: String,
        atEpochSeconds: Long,
        sessionLimit: Int,
    ): AttendanceInstitutionOverview =
        mutex.withLock {
            val limit = sessionLimit.coerceIn(1, MAX_INSTITUTION_ATTENDANCE_SESSIONS)
            val candidates =
                sessions.values
                    .filter { it.belongsToInstitutionOverview(institutionId) }
                    .sortedWith(compareByDescending<AttendanceSessionResponse> { it.openedAtEpochSeconds }.thenBy { it.id })
                    .take(limit + 1)
            val sampled = candidates.take(limit)
            val ids = sampled.map { it.id }.toSet()
            AttendanceInstitutionOverview(
                openPasses = sampled.count { it.isOpenAt(atEpochSeconds) },
                pendingReviews = records.values.count { it.sessionId in ids && it.isPendingInstitutionalReview() }.toLong(),
                sessionSampleLimit = limit,
                sampleLimited = candidates.size > limit,
            )
        }

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

    override suspend fun closeSessionIfOpen(
        sessionId: String,
        actorId: String,
        closedByTeacher: Boolean,
        atEpochSeconds: Long,
        authorizedStudentIds: List<String>?,
    ): AttendanceSessionResponse? =
        mutex.withLock {
            val current = sessions[sessionId] ?: return@withLock null
            if (current.status == AttendanceSessionStatus.CLOSED) return@withLock current
            val closed =
                current.copy(
                    status = AttendanceSessionStatus.CLOSED,
                    closedAtEpochSeconds = atEpochSeconds,
                    closedByTeacher = closedByTeacher,
                )
            sessions[sessionId] = closed
            if (authorizedStudentIds != null) closureRosters[sessionId] = authorizedStudentIds.distinct()
            closed
        }

    override suspend fun pendingClosures(limit: Int): List<AttendanceClosureWork> =
        mutex.withLock {
            closureRosters.entries.take(limit.coerceIn(1, 100)).mapNotNull { (id, roster) ->
                sessions[id]?.let { AttendanceClosureWork(it, roster) }
            }
        }

    override suspend fun closureWork(sessionId: String): AttendanceClosureWork? =
        mutex.withLock {
            closureRosters[sessionId]?.let { roster -> sessions[sessionId]?.let { AttendanceClosureWork(it, roster) } }
        }

    override suspend fun deferClosure(sessionId: String) {
        mutex.withLock {
            closureRosters.remove(sessionId)?.let { closureRosters[sessionId] = it }
        }
    }

    override suspend fun completeClosure(sessionId: String) {
        mutex.withLock { closureRosters.remove(sessionId) }
    }

    override suspend fun teacherHistoryPage(
        teacherId: String,
        page: Int,
    ): List<AttendanceSessionResponse> =
        mutex.withLock {
            require(page in 1..10_000)
            sessions.values
                .filter { it.openedBy == teacherId }
                .sortedBy { it.id }
                .drop((page - 1) * 100)
                .take(101)
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
                val next = record.copy(recordRevision = current.recordRevision + 1)
                records[record.id] = next
                operationToRecord[record.operationId] = record.id
                next
            } else {
                current
            }
        }

    override suspend fun recordsForSession(sessionId: String): List<AttendanceRecordResponse> =
        mutex.withLock {
            records.values.filter { it.sessionId == sessionId }
        }

    override suspend fun recordsForStudent(
        studentId: String,
        limit: Int,
    ): List<AttendanceRecordResponse> =
        mutex.withLock {
            records.values
                .asSequence()
                .filter { it.studentId == studentId }
                .sortedWith(compareByDescending<AttendanceRecordResponse> { it.receivedAtEpochSeconds }.thenBy { it.id })
                .take(limit.coerceIn(1, MAX_STUDENT_HISTORY_RESULTS))
                .toList()
        }

    override suspend fun replaceRecord(record: AttendanceRecordResponse) {
        mutex.withLock {
            records[record.id] = record
            operationToRecord[record.operationId] = record.id
        }
    }

    override suspend fun studentHistoryPage(
        studentId: String,
        page: Int,
    ): List<AttendanceRecordResponse> =
        mutex.withLock {
            require(page in 1..10_000)
            records.values
                .filter { it.studentId == studentId }
                .sortedBy { it.id }
                .drop((page - 1) * 100)
                .take(101)
        }

    override suspend fun compareAndSetRecord(
        record: AttendanceRecordResponse,
        expectedRevision: Long,
    ): Boolean =
        mutex.withLock {
            if (record.recordRevision != expectedRevision + 1 || records[record.id]?.recordRevision != expectedRevision) {
                return@withLock false
            }
            records[record.id] = record
            operationToRecord[record.operationId] = record.id
            true
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
const val MAX_STUDENT_HISTORY_RESULTS = 100
