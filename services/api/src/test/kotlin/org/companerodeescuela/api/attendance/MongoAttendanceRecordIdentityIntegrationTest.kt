package org.companerodeescuela.api.attendance

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.companerodeescuela.shared.contracts.AttendanceReasonCode
import org.companerodeescuela.shared.contracts.AttendanceRecordResponse
import org.companerodeescuela.shared.contracts.AttendanceStatus
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@EnabledIfEnvironmentVariable(named = "V12_TEST_MONGODB_URI", matches = ".+")
@Timeout(120)
class MongoAttendanceRecordIdentityIntegrationTest {
    @Test
    fun `hashed retry reuses legacy session student record`() =
        withDatabase { database ->
            val repository = MongoAttendanceRepository(database)
            val legacy =
                record(
                    id = "session:student:entry",
                    operationId = "legacy-operation",
                )
            assertIs<AttemptWriteResult.Created>(repository.writeAttempt(legacy))

            val migrated =
                legacy.copy(
                    id = AttendanceRecordIds.forStudentSession(legacy.sessionId, legacy.studentId),
                    operationId = "migrated-operation",
                )
            val result = assertIs<AttemptWriteResult.Existing>(repository.writeAttempt(migrated))

            assertEquals(legacy.id, result.record.id)
            assertEquals(listOf(legacy), repository.recordsForSession(legacy.sessionId))
        }

    @Test
    fun `concurrent hashed attempts keep one canonical student session record`() =
        withDatabase { database ->
            val repository = MongoAttendanceRepository(database)
            val sessionId = "session-concurrent"
            val studentId = "student-1"
            repository.recordsForSession(sessionId)

            val results =
                coroutineScope {
                    (1..24)
                        .map { index ->
                            async {
                                repository.writeAttempt(
                                    record(
                                        id = AttendanceRecordIds.forStudentSession(sessionId, studentId),
                                        operationId = "operation-$index",
                                        sessionId = sessionId,
                                        studentId = studentId,
                                    ),
                                )
                            }
                        }.awaitAll()
                }

            assertEquals(1, results.count { it is AttemptWriteResult.Created })
            assertEquals(23, results.count { it is AttemptWriteResult.Existing })
            assertEquals(1, repository.recordsForSession(sessionId).size)
        }

    private fun record(
        id: String,
        operationId: String,
        sessionId: String = "session:student",
        studentId: String = "entry",
    ) = AttendanceRecordResponse(
        id = id,
        operationId = operationId,
        sessionId = sessionId,
        occurrenceId = "occurrence-1",
        studentId = studentId,
        status = AttendanceStatus.LIKELY,
        reasonCode = AttendanceReasonCode.IDENTITY_SESSION_TIME,
        attemptedAtEpochSeconds = 100,
        receivedAtEpochSeconds = 101,
    )

    private fun withDatabase(block: suspend (MongoDatabase) -> Unit) =
        runBlocking {
            val uri = System.getenv("V12_TEST_MONGODB_URI")
            require(uri == "mongodb://127.0.0.1:27017")
            MongoClient.create(uri).use { client ->
                val database =
                    client.getDatabase("attendance_identity_${UUID.randomUUID().toString().replace("-", "")}")
                try {
                    block(database)
                } finally {
                    database.drop()
                }
            }
        }
}
