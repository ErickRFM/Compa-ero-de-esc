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
import org.companerodeescuela.shared.contracts.NetworkVerificationMethod
import org.companerodeescuela.shared.contracts.SchoolNetworkEvidence
import org.companerodeescuela.shared.contracts.SchoolPresenceStatus
import org.companerodeescuela.shared.contracts.StartSchoolPresenceRequest

class SchoolPresenceServiceTest {
    private val instant = Instant.parse("2026-10-06T13:00:00Z")
    private val qrToken = "wall-entry-token-for-test"
    private val qrHash = sha256(qrToken)

    @Test
    fun `starts school day only with valid QR and school WiFi`() = runTest {
        val service = service()

        val result = service.start(
            studentId = "2020-10455",
            request = request(),
        )

        assertEquals("2020-10455", result.studentId)
        assertEquals(SchoolPresenceStatus.ACTIVE, result.status)
        assertEquals(true, result.qrVerified)
        assertEquals(true, result.networkVerified)
        assertEquals(NetworkVerificationMethod.SSID_BSSID, result.networkVerificationMethod)
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
