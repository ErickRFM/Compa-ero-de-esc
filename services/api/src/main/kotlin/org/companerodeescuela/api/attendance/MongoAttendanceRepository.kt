package org.companerodeescuela.api.attendance

import com.mongodb.MongoWriteException
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bson.Document
import org.companerodeescuela.shared.contracts.AttendanceRecordResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionStatus
import org.companerodeescuela.shared.contracts.AttendanceStatus

/**
 * Durable attendance storage.
 *
 * Mongo's built-in unique _id protects one record per student/session. A
 * second unique index protects client operationId, which is the idempotency
 * key used by offline retries.
 */
class MongoAttendanceRepository(
    database: MongoDatabase,
) : AttendanceRepository {
    private val sessions: MongoCollection<Document> =
        database.getCollection(SESSIONS_COLLECTION)
    private val records: MongoCollection<Document> =
        database.getCollection(RECORDS_COLLECTION)
    private val indexMutex = Mutex()
    @Volatile
    private var indexesReady = false

    override suspend fun createSession(
        session: AttendanceSessionResponse,
    ): AttendanceSessionResponse {
        ensureIndexes()
        sessions.insertOne(session.toDocument())
        return session
    }

    override suspend fun findSession(id: String): AttendanceSessionResponse? {
        ensureIndexes()
        return sessions.find(eq("_id", id)).firstOrNull()?.toSession()
    }

    override suspend fun findOpenSessions(): List<AttendanceSessionResponse> {
        ensureIndexes()
        return sessions
            .find(eq("status", AttendanceSessionStatus.OPEN.name))
            .toList()
            .map(Document::toSession)
    }

    override suspend fun replaceSession(session: AttendanceSessionResponse) {
        ensureIndexes()
        sessions.replaceOne(
            eq("_id", session.id),
            session.toDocument(),
            ReplaceOptions().upsert(true),
        )
    }

    override suspend fun writeAttempt(record: AttendanceRecordResponse): AttemptWriteResult {
        ensureIndexes()

        val byOperation = records.find(eq("operationId", record.operationId))
            .firstOrNull()
            ?.toRecord()
        if (byOperation != null) {
            return if (
                byOperation.sessionId == record.sessionId &&
                byOperation.studentId == record.studentId
            ) {
                AttemptWriteResult.Existing(byOperation)
            } else {
                AttemptWriteResult.OperationConflict
            }
        }

        val byRecordId = records.find(eq("_id", record.id)).firstOrNull()?.toRecord()
        if (byRecordId != null) {
            return AttemptWriteResult.Existing(byRecordId)
        }

        return try {
            records.insertOne(record.toDocument())
            AttemptWriteResult.Created(record)
        } catch (_: MongoWriteException) {
            val afterRace = records.find(eq("_id", record.id))
                .firstOrNull()
                ?.toRecord()
                ?: records.find(eq("operationId", record.operationId))
                    .firstOrNull()
                    ?.toRecord()
                ?: throw

            if (
                afterRace.sessionId == record.sessionId &&
                afterRace.studentId == record.studentId
            ) {
                AttemptWriteResult.Existing(afterRace)
            } else {
                AttemptWriteResult.OperationConflict
            }
        }
    }

    override suspend fun recordsForSession(sessionId: String): List<AttendanceRecordResponse> {
        ensureIndexes()
        return records.find(eq("sessionId", sessionId)).toList().map(Document::toRecord)
    }

    override suspend fun replaceRecord(record: AttendanceRecordResponse) {
        ensureIndexes()
        records.replaceOne(
            eq("_id", record.id),
            record.toDocument(),
            ReplaceOptions().upsert(false),
        )
    }

    override suspend fun findRecord(id: String): AttendanceRecordResponse? {
        ensureIndexes()
        return records.find(eq("_id", id)).firstOrNull()?.toRecord()
    }

    private suspend fun ensureIndexes() {
        if (indexesReady) return
        indexMutex.withLock {
            if (indexesReady) return@withLock
            records.createIndex(
                Indexes.ascending("operationId"),
                IndexOptions().unique(true).name("uq_attendance_operation_id"),
            )
            sessions.createIndex(
                Indexes.ascending("status"),
                IndexOptions().name("ix_attendance_session_status"),
            )
            records.createIndex(
                Indexes.ascending("sessionId"),
                IndexOptions().name("ix_attendance_record_session"),
            )
            indexesReady = true
        }
    }

    private fun AttendanceSessionResponse.toDocument(): Document = Document()
        .append("_id", id)
        .append("courseId", courseId)
        .append("groupName", groupName)
        .append("openedBy", openedBy)
        .append("openedAtEpochSeconds", openedAtEpochSeconds)
        .append("closesAtEpochSeconds", closesAtEpochSeconds)
        .append("closedAtEpochSeconds", closedAtEpochSeconds)
        .append("status", status.name)

    private fun Document.toSession(): AttendanceSessionResponse =
        AttendanceSessionResponse(
            id = getString("_id"),
            courseId = getString("courseId"),
            groupName = getString("groupName"),
            openedBy = getString("openedBy"),
            openedAtEpochSeconds = requireLong("openedAtEpochSeconds"),
            closesAtEpochSeconds = requireLong("closesAtEpochSeconds"),
            closedAtEpochSeconds = numberAsLong("closedAtEpochSeconds"),
            status = AttendanceSessionStatus.valueOf(getString("status")),
        )

    private fun AttendanceRecordResponse.toDocument(): Document = Document()
        .append("_id", id)
        .append("operationId", operationId)
        .append("sessionId", sessionId)
        .append("studentId", studentId)
        .append("status", status.name)
        .append("reasonCode", reasonCode)
        .append("attemptedAtEpochSeconds", attemptedAtEpochSeconds)
        .append("receivedAtEpochSeconds", receivedAtEpochSeconds)
        .append("reviewedBy", reviewedBy)
        .append("reviewedAtEpochSeconds", reviewedAtEpochSeconds)

    private fun Document.toRecord(): AttendanceRecordResponse =
        AttendanceRecordResponse(
            id = getString("_id"),
            operationId = getString("operationId"),
            sessionId = getString("sessionId"),
            studentId = getString("studentId"),
            status = AttendanceStatus.valueOf(getString("status")),
            reasonCode = getString("reasonCode"),
            attemptedAtEpochSeconds = requireLong("attemptedAtEpochSeconds"),
            receivedAtEpochSeconds = requireLong("receivedAtEpochSeconds"),
            reviewedBy = getString("reviewedBy"),
            reviewedAtEpochSeconds = numberAsLong("reviewedAtEpochSeconds"),
        )

    private fun Document.requireLong(name: String): Long =
        numberAsLong(name) ?: error("Missing numeric field: " + name)

    private fun Document.numberAsLong(name: String): Long? =
        (get(name) as? Number)?.toLong()

    private companion object {
        const val SESSIONS_COLLECTION = "attendance_sessions"
        const val RECORDS_COLLECTION = "attendance_records"
    }
}
