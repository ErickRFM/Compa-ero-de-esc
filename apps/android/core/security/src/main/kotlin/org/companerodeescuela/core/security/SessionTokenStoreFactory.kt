package org.companerodeescuela.core.security

import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory

/**
 * Creates the single application-scoped encrypted session store.
 *
 * Callers should retain one instance for the application lifetime. Hilt does
 * this in the app module.
 */
object SessionTokenStoreFactory {
    fun create(context: Context): SessionTokenStore {
        val appContext = context.applicationContext
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { appContext.preferencesDataStoreFile(FILE_NAME) },
        )
        return EncryptedSessionTokenStore(
            dataStore = dataStore,
            cipher = AndroidKeystoreTokenCipher(),
        )
    }

    private const val FILE_NAME = "secure_session"
}
