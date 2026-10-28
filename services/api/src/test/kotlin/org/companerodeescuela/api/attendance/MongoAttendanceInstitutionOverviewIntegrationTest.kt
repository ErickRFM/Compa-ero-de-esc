package org.companerodeescuela.api.attendance

import com.mongodb.kotlin.client.coroutine.MongoClient
import kotlinx.coroutines.runBlocking
import org.companerodeescuela.shared.contracts.AttendanceIdentitySource
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
class MongoAttendanceInstitutionOverviewIntegrationTest {
    @Test
    fun `Mongo aggregate keeps exact tenant boundary expiry and pending closed reviews with explicit sample limits`() =
        runBlocking {
            val uri = System.getenv("V12_TEST_MONGODB_URI")
            require(uri == "mongodb://127.0.0.1:27017")
            MongoClient.create(uri).use { client ->
                val database = client.getDatabase("v20_counts_" + UUID.randomUUID().toString().replace("-", ""))
                try {
                    val repository = MongoAttendanceRepository(database)
                    val pass =
                        AttendanceSessionResponse(
                            "open",
                            "open-occurrence",
                            "course",
                            "9A",
                            "2026-10-28",
                            "07:00",
                            "08:00",
                            "teacher",
                            1,
                            200,
                            status = AttendanceSessionStatus.OPEN,
                            identitySource = AttendanceIdentitySource.NATIVE,
                            institutionId = "campus",
                            groupId = "group",
                            groupScopeRevision = 1,
                        )
                    repository.createSession(pass)
                    repository.createSession(
                        pass.copy(
                            id = "closed",
                            occurrenceId = "closed-occurrence",
                            status = AttendanceSessionStatus.CLOSED,
                            closedAtEpochSeconds = 90,
                        ),
                    )
                    repository.createSession(pass.copy(id = "expired", occurrenceId = "expired-occurrence", closesAtEpochSeconds = 100))
                    repository.createSession(pass.copy(id = "foreign", occurrenceId = "foreign-occurrence", institutionId = "other"))
                    for (id in listOf("open", "closed", "expired", "foreign")) {
                        repository.writeAttempt(
                            AttendanceRecordResponse(
                                "record-$id",
                                "operation-$id",
                                id,
                                "$id-occurrence",
                                "student",
                                AttendanceStatus.REVIEW_REQUIRED,
                                AttendanceReasonCode.CLASS_CALL_CONFIRMED,
                                50,
                                50,
                            ),
                        )
                    }
                    val reader = MongoAttendanceRepository(database)
                    assertEquals(AttendanceInstitutionOverview(1, 3, 1000, false), reader.institutionOverview("campus", 100))
                    assertEquals(AttendanceInstitutionOverview(0, 2, 2, true), reader.institutionOverview("campus", 100, 2))
                    assertEquals(AttendanceInstitutionOverview(0, 0, 1000, false), reader.institutionOverview("unknown", 100))
                } finally {
                    database.drop()
                }
            }
        }
}
