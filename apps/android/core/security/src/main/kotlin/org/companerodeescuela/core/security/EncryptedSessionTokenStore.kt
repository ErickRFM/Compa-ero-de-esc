package org.companerodeescuela.core.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
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

    override suspend fun readAccessToken(): String? {
        val preferences = dataStore.data.first()
        return try {
            decodeToken(preferences)
        } catch (_: Exception) {
            clear()
            null
        }
    }

    override fun observeAccessToken(): Flow<String?> =
        dataStore.data.map { preferences ->
            runCatching { decodeToken(preferences) }.getOrNull()
        }

    override suspend fun writeAccessToken(token: String) {
        require(token.isNotBlank()) { "access token must not be blank" }

        val encrypted = try {
            cipher.encrypt(token)
        } catch (first: Exception) {
            // Android Keystore keys can become invalid after lock-screen,
            // restore or device-security changes. Recover once with a fresh key.
            cipher.reset()
            try {
                cipher.encrypt(token)
            } catch (second: Exception) {
                second.addSuppressed(first)
                throw second
            }
        }

        dataStore.edit { preferences ->
            preferences[CIPHERTEXT_KEY] = encrypted.ciphertext
            preferences[IV_KEY] = encrypted.initializationVector
        }
    }

    override suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(CIPHERTEXT_KEY)
            preferences.remove(IV_KEY)
        }
    }

    private fun decodeToken(preferences: Preferences): String? {
        val ciphertext = preferences[CIPHERTEXT_KEY] ?: return null
        val iv = preferences[IV_KEY] ?: return null
        return cipher.decrypt(
            EncryptedValue(
                ciphertext = ciphertext,
                initializationVector = iv,
            ),
        )
    }

    private companion object {
        val CIPHERTEXT_KEY = stringPreferencesKey("access_token_ciphertext")
        val IV_KEY = stringPreferencesKey("access_token_iv")
    }
}
