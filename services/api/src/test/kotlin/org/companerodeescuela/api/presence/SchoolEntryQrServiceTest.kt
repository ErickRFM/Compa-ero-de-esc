package org.companerodeescuela.api.presence

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.shared.contracts.CreateSchoolEntryQrRequest
import org.companerodeescuela.shared.contracts.SchoolEntryQrStatus

class SchoolEntryQrServiceTest {
    private val instant = Instant.parse("2026-10-07T12:00:00Z")
    private val clock = Clock.fixed(instant, ZoneOffset.UTC)

    @Test
    fun `admin creates active QR and token is returned only at creation`() = runTest {
        val service = service()

        val created = service.create("admin-1", request())

        assertEquals(SchoolEntryQrStatus.ACTIVE, created.status)
        assertNotNull(created.token)
        assertEquals("admin-1", created.createdBy)
        assertNull(service.list().single().token)
    }

    @Test
    fun `active QR can be verified and usage is audited`() = runTest {
        val service = service()
        val created = service.create("admin-1", request())

        val verified = service.verifyAndRecordUse(created.token!!)

        assertNotNull(verified)
        assertEquals(1, service.list().single().usageCount)
        assertEquals(instant.epochSecond, service.list().single().lastUsedAtEpochSeconds)
    }

    @Test
    fun `revoked QR cannot be used again`() = runTest {
        val service = service()
        val created = service.create("admin-1", request())

        service.revoke(created.id)

        assertNull(service.verifyAndRecordUse(created.token!!))
        assertEquals(SchoolEntryQrStatus.REVOKED, service.list().single().status)
    }

    @Test
    fun `regenerate revokes previous QR and returns a new secret`() = runTest {
        var token = 0
        val service = SchoolEntryQrService(
            repository = InMemorySchoolEntryQrRepository(),
            clock = clock,
            newId = { "qr-" + (++token) },
            tokenGenerator = { "token-$token-" + "x".repeat(32) },
        )
        val first = service.create("admin-1", request())

        val result = service.regenerate("admin-2", first.id)

        assertEquals(SchoolEntryQrStatus.REVOKED, result.revoked.status)
        assertEquals(SchoolEntryQrStatus.ACTIVE, result.replacement.status)
        assertEquals("admin-2", result.replacement.createdBy)
        assertNull(service.verifyAndRecordUse(first.token!!))
        assertNotNull(service.verifyAndRecordUse(result.replacement.token!!))
    }

    private fun service() = SchoolEntryQrService(
        repository = InMemorySchoolEntryQrRepository(),
        clock = clock,
        newId = { "qr-1" },
        tokenGenerator = { "managed-school-entry-token-" + "x".repeat(32) },
    )

    private fun request() = CreateSchoolEntryQrRequest(
        name = "Entrada principal",
        location = "Acceso norte",
        validFromEpochSeconds = instant.minusSeconds(60).epochSecond,
        expiresAtEpochSeconds = instant.plusSeconds(86_400).epochSecond,
    )
}
