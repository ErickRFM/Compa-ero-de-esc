package org.companerodeescuela.core.security

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir

class EncryptedSessionTokenStoreTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `token round trips and clear removes the session`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { tempDir.resolve("session.preferences_pb").toFile() },
        )
        val store = EncryptedSessionTokenStore(
            dataStore = dataStore,
            cipher = FakeCipher,
        )

        assertNull(store.readAccessToken())

        store.writeAccessToken("token-one")
        assertEquals("token-one", store.readAccessToken())

        store.writeAccessToken("token-two")
        assertEquals("token-two", store.readAccessToken())

        store.clear()
        assertNull(store.readAccessToken())
    }

    private object FakeCipher : TokenCipher {
        override fun encrypt(plainText: String): EncryptedValue =
            EncryptedValue(
                ciphertext = plainText.reversed(),
                initializationVector = "test-iv",
            )

        override fun decrypt(value: EncryptedValue): String =
            value.ciphertext.reversed()
    }
}
