package org.companerodeescuela.api.presence

import java.security.MessageDigest
import org.companerodeescuela.api.errors.ApiException

class SchoolEntryQrVerifier(
    private val managedQrService: SchoolEntryQrService? = null,
    private val legacyQrSha256: String = "",
) {
    suspend fun verify(rawToken: String) {
        val token = rawToken.trim()
        if (token.isBlank() || token.length > MAX_QR_TOKEN_LENGTH) {
            throw ApiException.Validation("Institutional QR is invalid")
        }

        if (managedQrService?.verifyAndRecordUse(token) != null) return

        val fallbackHash = legacyQrSha256.lowercase()
        if (fallbackHash.isBlank()) {
            throw ApiException.Forbidden("Institutional QR could not be verified")
        }

        val actual = sha256(token)
        if (!MessageDigest.isEqual(actual.toByteArray(), fallbackHash.toByteArray())) {
            throw ApiException.Forbidden("Institutional QR could not be verified")
        }
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val MAX_QR_TOKEN_LENGTH = 2_048
    }
}
