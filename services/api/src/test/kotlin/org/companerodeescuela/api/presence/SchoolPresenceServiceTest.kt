package org.companerodeescuela.api.presence

import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.CreateSchoolEntryQrRequest
import org.companerodeescuela.shared.contracts.NetworkVerificationMethod
import org.companerodeescuela.shared.contracts.SchoolNetworkEvidence
import org.companerodeescuela.shared.contracts.SchoolPresenceStatus
import org.companerodeescuela.shared.contracts.StartSchoolPresenceRequest

class SchoolPresenceServiceTest {
    private val instant = Instant.parse("2026-10-06T13:00:00Z")
    private val qrToken = "wall-entry-token-for-test"
    private val qrHash = sha256(qrToken)

    @Test
    fun `records QR and declared network without claiming independent verification`() = runTest {
        val service = service()

        val result = service.start(
            studentId = "2020-10455",
            request = request(),
        )

        assertEquals("2020-10455", result.studentId)
        assertEquals(SchoolPresenceStatus.ACTIVE, result.status)
        assertEquals(true, result.qrVerified)
        assertEquals(false, result.networkVerified)
        assertEquals(NetworkVerificationMethod.SSID_BSSID, result.networkVerificationMethod)
        assertEquals(instant.epochSecond, result.serverTimeEpochSeconds)
        assertEquals(instant.epochSecond, service.activeFor("2020-10455")?.serverTimeEpochSeconds)
    }

    @Test
    fun `rejects copied or invalid institutional QR`() = runTest {
        val service = service()

        assertFailsWith<ApiException.Forbidden> {
            service.start(
                studentId = "2020-10455",
                request = request().copy(qrToken = "wrong-token"),
            )
        }
    }

    @Test
    fun `rejects non school WiFi even with valid QR`() = runTest {
        val service = service()

        assertFailsWith<ApiException.Forbidden> {
            service.start(
                studentId = "2020-10455",
                request = request().copy(
                    network = SchoolNetworkEvidence(
                        ssid = "Casa",
                        bssid = "ff:ee:dd:cc:bb:aa",
                    ),
                ),
            )
        }
    }

    @Test
    fun `admin managed QR starts school day without legacy fixed hash`() = runTest {
        val qrService = SchoolEntryQrService(
            repository = InMemorySchoolEntryQrRepository(),
            clock = Clock.fixed(instant, ZoneOffset.UTC),
            newId = { "managed-qr-1" },
            tokenGenerator = { "managed-entry-token-" + "x".repeat(32) },
        )
        val managedQr = qrService.create(
            actorId = "admin-1",
            request = CreateSchoolEntryQrRequest(
                name = "Entrada principal",
                validFromEpochSeconds = instant.minusSeconds(60).epochSecond,
                expiresAtEpochSeconds = instant.plusSeconds(3600).epochSecond,
            ),
        )
        val service = SchoolPresenceService(
            repository = InMemorySchoolPresenceRepository(),
            policy = SchoolPresencePolicy(
                entryQrSha256 = "",
                allowedSsids = setOf("Escuela-Alumnos"),
                allowedBssids = setOf("aa:bb:cc:dd:ee:ff"),
            ),
            qrVerifier = SchoolEntryQrVerifier(
                managedQrService = qrService,
            ),
            networkVerifier = SchoolNetworkVerifier(
                allowedSsids = setOf("Escuela-Alumnos"),
                allowedBssids = setOf("aa:bb:cc:dd:ee:ff"),
            ),
            clock = Clock.fixed(instant, ZoneOffset.UTC),
            newId = { "presence-managed" },
        )

        val result = service.start(
            "2020-10455",
            request().copy(qrToken = managedQr.token!!),
        )

        assertEquals(SchoolPresenceStatus.ACTIVE, result.status)
        assertEquals(1, qrService.list().single().usageCount)
    }

    @Test
    fun `starting twice reuses the active school day`() = runTest {
        var ids = 0
        val service = service(newId = { "presence-" + (++ids) })

        val first = service.start("2020-10455", request())
        val second = service.start("2020-10455", request().copy(operationId = "op-2"))

        assertEquals(first.id, second.id)
        assertEquals(1, ids)
    }

    @Test
    fun `closed school day is no longer active`() = runTest {
        val service = service()
        service.start("2020-10455", request())
        val closed = service.close("2020-10455")

        assertEquals(SchoolPresenceStatus.CLOSED, closed.status)
        assertEquals(null, service.activeFor("2020-10455"))
    }

    @Test
    fun `legacy active entry network flags are not effective verification`() = runTest {
        val repository = InMemorySchoolPresenceRepository()
        val service = SchoolPresenceService(repository, SchoolPresencePolicy(qrHash, setOf("Escuela-Alumnos"), emptySet()), clock = Clock.fixed(instant, ZoneOffset.UTC))
        val legacy = org.companerodeescuela.shared.contracts.SchoolPresenceResponse(
            "legacy", "student-1", instant.epochSecond, instant.plusSeconds(3600).epochSecond,
            status = SchoolPresenceStatus.ACTIVE, qrVerified = true, networkVerified = true,
            networkVerificationMethod = NetworkVerificationMethod.SSID,
        )
        repository.save(legacy)
        assertEquals(false, service.activeFor("student-1")!!.networkVerified)
        assertEquals(true, repository.findById("legacy")!!.networkVerified, "Do not erase original captured evidence")
    }

    @Test
    fun `closing legacy entry preserves raw evidence and returns honest closed projection`() = runTest {
        val repository = InMemorySchoolPresenceRepository()
        val service = SchoolPresenceService(repository, SchoolPresencePolicy(qrHash, setOf("Escuela-Alumnos"), emptySet()), clock = Clock.fixed(instant, ZoneOffset.UTC))
        val legacy = org.companerodeescuela.shared.contracts.SchoolPresenceResponse(
            "legacy", "student-1", instant.epochSecond, instant.plusSeconds(3600).epochSecond,
            status = SchoolPresenceStatus.ACTIVE, qrVerified = true, networkVerified = true,
            networkVerificationMethod = NetworkVerificationMethod.SSID,
        )
        repository.save(legacy)
        val response = service.close("student-1")
        assertEquals(SchoolPresenceStatus.CLOSED, response.status)
        assertEquals(false, response.networkVerified)
        val stored = repository.findById("legacy")!!
        assertEquals(true, stored.networkVerified, "Closing must not rewrite captured evidence")
        assertEquals(legacy.copy(status = SchoolPresenceStatus.CLOSED, closedAtEpochSeconds = instant.epochSecond), stored)
        assertEquals(null, service.activeFor("student-1"))
    }

    private fun service(
        newId: () -> String = { "presence-1" },
    ) = SchoolPresenceService(
        repository = InMemorySchoolPresenceRepository(),
        policy = SchoolPresencePolicy(
            entryQrSha256 = qrHash,
            allowedSsids = setOf("Escuela-Alumnos"),
            allowedBssids = setOf("aa:bb:cc:dd:ee:ff"),
        ),
        clock = Clock.fixed(instant, ZoneOffset.UTC),
        newId = newId,
    )

    private fun request() = StartSchoolPresenceRequest(
        operationId = "op-1",
        qrToken = qrToken,
        network = SchoolNetworkEvidence(
            ssid = "Escuela-Alumnos",
            bssid = "AA:BB:CC:DD:EE:FF",
        ),
        deviceTimestampEpochSeconds = instant.epochSecond,
    )

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
}
