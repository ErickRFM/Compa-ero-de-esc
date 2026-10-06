package org.companerodeescuela.feature.auth

object InstitutionalCredentialValidator {
    const val MAX_IDENTIFIER_LENGTH = 128
    const val MAX_PASSWORD_LENGTH = 256

    private val identifierPattern = Regex("^[A-Za-z0-9._@+\\-]+$")
    private val emailPattern =
        Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")

    fun sanitizeIdentifier(value: String): String =
        value
            .filterNot(Char::isWhitespace)
            .take(MAX_IDENTIFIER_LENGTH)

    fun sanitizePassword(value: String): String =
        value
            .filterNot { it == '\n' || it == '\r' || it == '\t' }
            .take(MAX_PASSWORD_LENGTH)

    fun identifierError(value: String): String? {
        if (value.isBlank()) return "Ingresa tu correo, matrícula o usuario."
        if (value.length < 3) return "El usuario debe tener al menos 3 caracteres."
        if (!identifierPattern.matches(value)) {
            return "Usa solo letras, números y los caracteres . _ - @ +"
        }
        if ('@' in value && !emailPattern.matches(value)) {
            return "Ingresa un correo institucional válido."
        }
        return null
    }

    fun passwordError(value: String): String? {
        if (value.isEmpty()) return "Ingresa tu contraseña."
        if (value.any { it.isISOControl() && !it.isWhitespace() }) {
            return "La contraseña contiene caracteres no válidos."
        }
        return null
    }
}
