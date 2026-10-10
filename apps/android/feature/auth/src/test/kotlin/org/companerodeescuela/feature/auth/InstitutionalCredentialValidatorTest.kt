package org.companerodeescuela.feature.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InstitutionalCredentialValidatorTest {

    @Test
    fun `identifier sanitizer removes whitespace and caps length`() {
        assertEquals(
            "alumno@uptlax.edu.mx",
            InstitutionalCredentialValidator.sanitizeIdentifier(" alumno @uptlax.edu.mx "),
        )
        assertEquals(
            InstitutionalCredentialValidator.MAX_IDENTIFIER_LENGTH,
            InstitutionalCredentialValidator
                .sanitizeIdentifier("a".repeat(300))
                .length,
        )
    }

    @Test
    fun `accepts institutional user matrícula and email formats`() {
        assertNull(InstitutionalCredentialValidator.identifierError("ana.lopez"))
        assertNull(InstitutionalCredentialValidator.identifierError("202612345"))
        assertNull(InstitutionalCredentialValidator.identifierError("ana.lopez@uptlax.edu.mx"))
    }

    @Test fun `native registered email with percent can log in`() {
        assertNull(InstitutionalCredentialValidator.identifierIssue("ana%school@example.test"))
    }

    @Test
    fun `rejects malformed identifiers`() {
        assertEquals(
            "Ingresa tu correo, matrícula o usuario.",
            InstitutionalCredentialValidator.identifierError(""),
        )
        assertEquals(
            "Ingresa un correo institucional válido.",
            InstitutionalCredentialValidator.identifierError("ana@"),
        )
        assertEquals(
            "Usa solo letras, números y los caracteres . _ - @ +",
            InstitutionalCredentialValidator.identifierError("ana/lopez"),
        )
    }

    @Test
    fun `password sanitizer preserves spaces but removes control separators`() {
        assertEquals(
            "Mi clavesegura",
            InstitutionalCredentialValidator.sanitizePassword("Mi clave\tsegura\n"),
        )
    }

    @Test
    fun `password validation only requires a non empty credential`() {
        assertEquals(
            "Ingresa tu contraseña.",
            InstitutionalCredentialValidator.passwordError(""),
        )
        assertNull(InstitutionalCredentialValidator.passwordError("Mi clave segura"))
    }
}
