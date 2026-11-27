package org.companerodeescuela.feature.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InvitationCredentialParserTest {
    private val sampleId = "dZdh8PeXUl9mtyYdQTNvw5CwivQpXs4i3sCepopjKKk"
    private val sampleCode = "BcmXYugjVh9yvYAkikbMkDUWN-kd6f3HvFRx442B44s"

    @Test
    fun `parses unified activation key separated by dot`() {
        val parsed = InvitationCredentialParser.parse("$sampleId.$sampleCode")
        assertEquals(ParsedInvitationCredentials(sampleId, sampleCode), parsed)
    }

    @Test
    fun `parses legacy email snippet where code appears before labeled ID`() {
        val legacyEmailSnippet =
            """
            Tu invitación está lista
            Regístrate en la aplicación con este mismo correo y verifica tu cuenta.
            CÓDIGO DE INVITACIÓN
            $sampleCode
            ID: $sampleId
            Válida hasta 2026-12-04T22:39:36.957567971Z UTC.
            """.trimIndent()

        val parsed = InvitationCredentialParser.parse(legacyEmailSnippet)
        assertEquals(ParsedInvitationCredentials(sampleId, sampleCode), parsed)
    }

    @Test
    fun `parses new email body with combined key and labeled fields`() {
        val newEmailBody =
            """
            CLAVE DE ACTIVACIÓN (COPIAR Y PEGAR EN LA APP)
            $sampleId.$sampleCode
            Código: $sampleCode
            ID: $sampleId
            """.trimIndent()

        val parsed = InvitationCredentialParser.parse(newEmailBody)
        assertEquals(ParsedInvitationCredentials(sampleId, sampleCode), parsed)
    }

    @Test
    fun `returns null for incomplete or malformed input`() {
        assertNull(InvitationCredentialParser.parse(""))
        assertNull(InvitationCredentialParser.parse("short.token"))
        assertNull(InvitationCredentialParser.parse(sampleId))
    }
}
