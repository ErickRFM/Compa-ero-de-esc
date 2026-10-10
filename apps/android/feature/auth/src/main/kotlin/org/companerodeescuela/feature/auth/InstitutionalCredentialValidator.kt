package org.companerodeescuela.feature.auth

enum class CredentialIssue(val legacyMessage: String) {
    IDENTIFIER_REQUIRED("Ingresa tu correo, matrícula o usuario."),
    IDENTIFIER_SHORT("El usuario debe tener al menos 3 caracteres."),
    IDENTIFIER_INVALID("Usa solo letras, números y los caracteres . _ - @ +"),
    IDENTIFIER_EMAIL("Ingresa un correo institucional válido."),
    PASSWORD_REQUIRED("Ingresa tu contraseña."),
    PASSWORD_INVALID("La contraseña contiene caracteres no válidos."),
}

object InstitutionalCredentialValidator {
    const val MAX_IDENTIFIER_LENGTH = 128
    const val MAX_PASSWORD_LENGTH = 256
    private val identifierPattern = Regex("^[A-Za-z0-9._@+\\-]+$")
    private val emailPattern = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")
    fun sanitizeIdentifier(value: String): String = value.filterNot(Char::isWhitespace).take(MAX_IDENTIFIER_LENGTH)
    fun sanitizePassword(value: String): String = value.filterNot { it == '\n' || it == '\r' || it == '\t' }.take(MAX_PASSWORD_LENGTH)
    fun identifierIssue(value: String): CredentialIssue? = when {
        value.isBlank() -> CredentialIssue.IDENTIFIER_REQUIRED
        value.length < 3 -> CredentialIssue.IDENTIFIER_SHORT
        '@' !in value && !identifierPattern.matches(value) -> CredentialIssue.IDENTIFIER_INVALID
        '@' in value && !emailPattern.matches(value) -> CredentialIssue.IDENTIFIER_EMAIL
        else -> null
    }
    fun passwordIssue(value: String): CredentialIssue? = when {
        value.isEmpty() -> CredentialIssue.PASSWORD_REQUIRED
        value.any { it.code in 0..31 || it.code == 127 } -> CredentialIssue.PASSWORD_INVALID
        else -> null
    }
    fun identifierError(value: String): String? = identifierIssue(value)?.legacyMessage
    fun passwordError(value: String): String? = passwordIssue(value)?.legacyMessage
}
