package org.companerodeescuela.api.integrations.push

import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SiiiPilotCredentialBootstrapTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-21T10:00:00Z"), ZoneOffset.UTC)
    private val pilotExpiry = clock.instant().epochSecond + 86_400

    private fun settings(): Map<String, String> =
        mapOf(
            "SIII_INBOUND_BOOTSTRAP_ENABLED" to "true",
            "SIII_INBOUND_KEY_ID" to "pilot-uptlax-1",
            "SIII_INBOUND_TENANT_ID" to "institution-pilot",
            "SIII_INBOUND_SECRET_BASE64" to
                Base64.getEncoder().encodeToString(ByteArray(32) { (it + 19).toByte() }),
            "SIII_INBOUND_EXPIRES_AT_EPOCH_SECONDS" to pilotExpiry.toString(),
        )

    @Test
    fun `bootstrap is off by default and cannot silently enable`() {
        assertNull(SiiiPilotCredentialBootstrap.parse({ null }, clock))
        assertNull(SiiiPilotCredentialBootstrap.parse({ settings().plus("SIII_INBOUND_BOOTSTRAP_ENABLED" to "false")[it] }, clock))
        assertFailsWith<IllegalArgumentException> {
            SiiiPilotCredentialBootstrap.parse({ settings().plus("SIII_INBOUND_BOOTSTRAP_ENABLED" to "yes")[it] }, clock)
        }
    }

    @Test
    fun `bootstrap grants only scoped group write with explicit tenant and expiry`() {
        val vars = settings()
        val credential = requireNotNull(SiiiPilotCredentialBootstrap.parse(vars::get, clock))
        assertEquals("pilot-uptlax-1", credential.keyId)
        assertEquals("institution-pilot", credential.tenantId)
        assertEquals(setOf("group:write"), credential.scopes)
        assertEquals(true, credential.enabled)
        assertEquals(32, credential.secret.size)
        assertEquals(pilotExpiry, credential.expiresAtEpochSeconds)
    }

    @Test
    fun `bootstrap refuses credentials for unknown or disabled institution`() =
        runBlocking {
            val credential = requireNotNull(SiiiPilotCredentialBootstrap.parse(settings()::get, clock))
            SiiiPilotCredentialBootstrap.requireEnabledTenant(credential.tenantId) { it == "institution-pilot" }
            assertFailsWith<IllegalArgumentException> {
                SiiiPilotCredentialBootstrap.requireEnabledTenant(credential.tenantId) { false }
            }
            assertFailsWith<IllegalArgumentException> {
                SiiiPilotCredentialBootstrap.requireEnabledTenant(credential.tenantId) { it == "other-tenant" }
            }
        }

    @Test
    fun `bootstrap rejects incomplete secrets and excessive lifetimes`() {
        val original = settings()
        for (key in listOf("SIII_INBOUND_SECRET_BASE64", "SIII_INBOUND_KEY_ID", "SIII_INBOUND_TENANT_ID")) {
            val vars = original - key
            assertFailsWith<IllegalArgumentException> {
                SiiiPilotCredentialBootstrap.parse(vars::get, clock)
            }
        }
        val lateExpiry = clock.instant().epochSecond + 91L * 86_400
        val invalid = original + ("SIII_INBOUND_EXPIRES_AT_EPOCH_SECONDS" to lateExpiry.toString())
        assertFailsWith<IllegalArgumentException> {
            SiiiPilotCredentialBootstrap.parse(invalid::get, clock)
        }
    }
}
