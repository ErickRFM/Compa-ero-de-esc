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
}

/**
 * DataStore contains only ciphertext and its IV. The AES key never leaves the
 * Android Keystore.
 */
internal class EncryptedSessionTokenStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: TokenCipher,
) : SessionTokenStore {

    override suspend fun readAccessToken(): String? =
        decodeToken(dataStore.data.first())

    override fun observeAccessToken(): Flow<String?> =
        dataStore.data.map(::decodeToken)

    override suspend fun writeAccessToken(token: String) {
        require(token.isNotBlank()) { "access token must not be blank" }
        val encrypted = cipher.encrypt(token)
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
