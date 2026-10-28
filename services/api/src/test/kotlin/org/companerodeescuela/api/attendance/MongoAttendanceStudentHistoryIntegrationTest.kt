package org.companerodeescuela.api.attendance

import com.mongodb.kotlin.client.coroutine.MongoClient
import kotlinx.coroutines.runBlocking
import org.companerodeescuela.shared.contracts.AttendanceReasonCode
import org.companerodeescuela.shared.contracts.AttendanceRecordResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionStatus
import org.companerodeescuela.shared.contracts.AttendanceStatus
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

@EnabledIfEnvironmentVariable(named = "V12_TEST_MONGODB_URI", matches = ".+")
@Timeout(120)
class MongoAttendanceStudentHistoryIntegrationTest {
    @Test
    fun `independent Mongo readers retain closed student history with stable ordering and a hard limit`() =
        runBlocking {
            val uri = System.getenv("V12_TEST_MONGODB_URI")
            require(uri == "mongodb://127.0.0.1:27017")
            MongoClient.create(uri).use { client ->
                val database = client.getDatabase("v20_history_" + UUID.randomUUID().toString().replace("-", ""))
                try {
                    val writer = MongoAttendanceRepository(database)
                    val closed =
                        AttendanceSessionResponse(
                            id = "closed",
                            occurrenceId = "occurrence-closed",
                            courseId = "course",
                            groupName = "9A",
                            occurrenceDate = "2026-10-28",
                            scheduledStartsAt = "07:00",
                            scheduledEndsAt = "08:00",
                            openedBy = "teacher",
                            openedAtEpochSeconds = 1,
                            closesAtEpochSeconds = 2,
                            closedAtEpochSeconds = 2,
                            status = AttendanceSessionStatus.CLOSED,
                        )
                    writer.createSession(closed)
                    for (index in 0..104) {
                        writer.writeAttempt(
                            AttendanceRecordResponse(
                                id = "record-${index.toString().padStart(3, '0')}",
                                operationId = "operation-$index",
                                sessionId = if (index == 104) closed.id else "session-$index",
                                occurrenceId = "occurrence-$index",
                                studentId = "student",
                                status = AttendanceStatus.REVIEW_REQUIRED,
                                reasonCode = AttendanceReasonCode.CLASS_CALL_CONFIRMED,
                                attemptedAtEpochSeconds = index.toLong(),
                                receivedAtEpochSeconds = index.toLong(),
                            ),
                        )
                    }
                    val reader = MongoAttendanceRepository(database)
                    val history = reader.recordsForStudent("student", 500)
                    assertEquals(100, history.size)
                    assertEquals("record-104", history.first().id)
                    assertEquals("record-005", history.last().id)
                    assertEquals(closed, reader.findSession(history.first().sessionId))
                    assertEquals(emptyList(), reader.recordsForStudent("another-student"))
                } finally {
                    database.drop()
                }
            }
        }
}
