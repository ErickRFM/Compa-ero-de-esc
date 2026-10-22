package org.companerodeescuela.api.institutions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SiiiPilotInstitutionBootstrapTest {
    private val vars =
        mapOf(
            "SIII_PILOT_TENANT_BOOTSTRAP_ENABLED" to "true",
            "SIII_PILOT_TENANT_ID" to "institution-pilot",
            "SIII_PILOT_TENANT_DISPLAY_NAME" to "Institucion Piloto",
        )

    @Test
    fun `pilot institution bootstrap is disabled by default`() {
        assertNull(SiiiPilotInstitutionBootstrap.parse { null })
        assertNull(
            SiiiPilotInstitutionBootstrap.parse {
                vars.plus("SIII_PILOT_TENANT_BOOTSTRAP_ENABLED" to "false")[it]
            },
        )
    }

    @Test
    fun `pilot institution requires explicit identity and rejects control characters`() {
        assertEquals(
            PilotInstitutionSpec("institution-pilot", "Institucion Piloto"),
            SiiiPilotInstitutionBootstrap.parse(vars::get),
        )
        assertFailsWith<IllegalArgumentException> {
            SiiiPilotInstitutionBootstrap.parse { vars.plus("SIII_PILOT_TENANT_DISPLAY_NAME" to "Bad\nName")[it] }
        }
        assertFailsWith<IllegalArgumentException> {
            SiiiPilotInstitutionBootstrap.parse { vars.minus("SIII_PILOT_TENANT_ID")[it] }
        }
        assertFailsWith<IllegalArgumentException> {
            SiiiPilotInstitutionBootstrap.parse { vars.plus("SIII_PILOT_TENANT_BOOTSTRAP_ENABLED" to "yes")[it] }
        }
    }
}
