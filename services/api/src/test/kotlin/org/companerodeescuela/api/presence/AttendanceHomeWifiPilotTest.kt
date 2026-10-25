package org.companerodeescuela.api.presence

import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.auth.InMemoryPlatformAccountRepository
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.test.ScopedAcademicFixtures
import org.companerodeescuela.shared.contracts.SchoolNetworkEvidence
import org.companerodeescuela.shared.contracts.SchoolPresenceStatus
import org.companerodeescuela.shared.contracts.StartSchoolPresenceRequest
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/** Isolated home-Wi-Fi rehearsal. This fixture exists only in the test source set and uses memory storage. */
class AttendanceHomeWifiPilotTest {
    private val now = Instant.parse("2026-10-06T13:00:00Z")
    private val syntheticStudent = "pilot-student-0001"
    private val homeWifi = "Compa-ero-Hogar-Piloto"
    private val qr = "pilot-only-institutional-entry-qr"
    private val qrHash =
        MessageDigest
            .getInstance("SHA-256")
            .digest(qr.toByteArray())
            .joinToString("") { "%02x".format(it) }

    @Test fun `synthetic home Wi-Fi pilot still needs its synthetic campus QR and never proves physical presence`() =
        runTest {
            val actor =
                UserSummary(
                    id = syntheticStudent,
                    displayName = "Synthetic Pilot Student",
                    roles = setOf(UserRole.STUDENT),
                    institutionId = "pilot-only-institution",
                )
            val accounts = InMemoryPlatformAccountRepository().also { it.create(ScopedAcademicFixtures.account(actor)) }
            val service =
                SchoolPresenceService(
                    repository = InMemorySchoolPresenceRepository(),
                    policy = SchoolPresencePolicy(qrHash, setOf(homeWifi), emptySet(), institutionId = "pilot-only-institution"),
                    clock = Clock.fixed(now, ZoneOffset.UTC),
                    newId = { "pilot-presence" },
                    accounts = accounts,
                )
            val request =
                StartSchoolPresenceRequest(
                    operationId = "pilot-operation",
                    qrToken = qr,
                    network = SchoolNetworkEvidence(ssid = homeWifi),
                    deviceTimestampEpochSeconds = now.epochSecond,
                )
            assertFailsWith<ApiException.Validation> {
                service.start(syntheticStudent, request.copy(qrToken = ""), actor)
            }
            val result = service.start(syntheticStudent, request, actor)
            assertEquals(syntheticStudent, result.studentId)
            assertEquals("pilot-only-institution", result.institutionId)
            assertEquals(SchoolPresenceStatus.ACTIVE, result.status)
            assertFalse(result.networkVerified)
            assertFalse(service.activeFor(syntheticStudent, actor)?.networkVerified ?: true)
        }
}
