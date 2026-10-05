package org.companerodeescuela.core.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal data class EncryptedValue(
    val ciphertext: String,
    val initializationVector: String,
)

internal interface TokenCipher {
    fun encrypt(plainText: String): EncryptedValue
    fun decrypt(value: EncryptedValue): String

    /**
     * Discards cipher material after a recoverable platform-keystore failure.
     * Test ciphers and implementations that do not own key material can keep
     * the default no-op.
     */
    fun reset() = Unit
}

/**
 * DataStore contains only ciphertext and its IV. The AES key never leaves the
 * Android Keystore.
 *
 * Corrupt/stale encrypted state is treated as "no session", never as an app
 * crash. A write gets one recovery attempt after resetting the keystore key.
 */
internal class EncryptedSessionTokenStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: TokenCipher,
) : SessionTokenStore {

    override suspend fun readAccessToken(): String? =
        try {
            decodeToken(dataStore.data.first())
        } catch (_: Exception) {
            // A corrupt preference file, stale IV or invalid keystore key must
            // never trap the app on startup. Best-effort cleanup is enough.
            runCatching { clear() }
            null
        }

    override suspend fun readRefreshSession(): RefreshSessionCredentials? =
        try {
            val preferences = dataStore.data.first()
            val sessionId = decodeValue(preferences, SESSION_ID_CIPHERTEXT_KEY, SESSION_ID_IV_KEY)
                ?.takeIf(String::isNotBlank)
                ?: return null
            val refreshToken = decodeValue(preferences, REFRESH_CIPHERTEXT_KEY, REFRESH_IV_KEY)
                ?.takeIf(String::isNotBlank)
                ?: return null
            RefreshSessionCredentials(sessionId, refreshToken)
        } catch (_: Exception) {
            runCatching { clear() }
            null
        }

    override fun observeAccessToken(): Flow<String?> =
        dataStore.data
            .map { preferences ->
                runCatching { decodeToken(preferences) }.getOrNull()
            }
            .catch {
                // DataStore itself can fail while reading a corrupt file.
                // Emit signed-out state rather than cancelling session observation.
                emit(null)
            }

    override suspend fun writeAccessToken(token: String) {
        require(token.isNotBlank()) { "access token must not be blank" }
        val encrypted = encryptRecovering(listOf(token)).single()

        dataStore.edit { preferences ->
            preferences[CIPHERTEXT_KEY] = encrypted.ciphertext
            preferences[IV_KEY] = encrypted.initializationVector
        }
    }

    override suspend fun writeSession(
        accessToken: String,
        sessionId: String,
        refreshToken: String,
    ) {
        require(accessToken.isNotBlank()) { "access token must not be blank" }
        require(sessionId.isNotBlank()) { "session id must not be blank" }
        require(refreshToken.isNotBlank()) { "refresh token must not be blank" }
        val encrypted = encryptRecovering(listOf(accessToken, sessionId, refreshToken))

        dataStore.edit { preferences ->
            preferences[CIPHERTEXT_KEY] = encrypted[0].ciphertext
            preferences[IV_KEY] = encrypted[0].initializationVector
            preferences[SESSION_ID_CIPHERTEXT_KEY] = encrypted[1].ciphertext
            preferences[SESSION_ID_IV_KEY] = encrypted[1].initializationVector
            preferences[REFRESH_CIPHERTEXT_KEY] = encrypted[2].ciphertext
            preferences[REFRESH_IV_KEY] = encrypted[2].initializationVector
        }
    }

    override suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(CIPHERTEXT_KEY)
            preferences.remove(IV_KEY)
            preferences.remove(SESSION_ID_CIPHERTEXT_KEY)
            preferences.remove(SESSION_ID_IV_KEY)
            preferences.remove(REFRESH_CIPHERTEXT_KEY)
            preferences.remove(REFRESH_IV_KEY)
        }
    }

    private fun encryptRecovering(values: List<String>): List<EncryptedValue> = try {
        values.map(cipher::encrypt)
    } catch (first: Exception) {
        cipher.reset()
        try {
            values.map(cipher::encrypt)
        } catch (second: Exception) {
            second.addSuppressed(first)
            throw second
        }
    }

    private fun decodeToken(preferences: Preferences): String? {
        return decodeValue(preferences, CIPHERTEXT_KEY, IV_KEY)
    }

    private fun decodeValue(
        preferences: Preferences,
        ciphertextKey: androidx.datastore.preferences.core.Preferences.Key<String>,
        ivKey: androidx.datastore.preferences.core.Preferences.Key<String>,
    ): String? {
        val ciphertext = preferences[ciphertextKey] ?: return null
        val iv = preferences[ivKey] ?: return null
        return cipher.decrypt(EncryptedValue(ciphertext, iv))
    }

    private companion object {
        val CIPHERTEXT_KEY = stringPreferencesKey("access_token_ciphertext")
        val IV_KEY = stringPreferencesKey("access_token_iv")
        val SESSION_ID_CIPHERTEXT_KEY = stringPreferencesKey("session_id_ciphertext")
        val SESSION_ID_IV_KEY = stringPreferencesKey("session_id_iv")
        val REFRESH_CIPHERTEXT_KEY = stringPreferencesKey("refresh_token_ciphertext")
        val REFRESH_IV_KEY = stringPreferencesKey("refresh_token_iv")
    }
}
