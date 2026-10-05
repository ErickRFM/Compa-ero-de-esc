package org.companerodeescuela.core.security

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
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

    @Test
    fun `refresh session credentials round trip encrypted and clear atomically`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { tempDir.resolve("refresh-session.preferences_pb").toFile() },
        )
        val store = EncryptedSessionTokenStore(dataStore, FakeCipher)
        val credentials = RefreshSessionCredentials("session-42", "refresh-secret-value")

        store.writeSession("access-secret-value", credentials.sessionId, credentials.refreshToken)

        assertEquals("access-secret-value", store.readAccessToken())
        assertEquals(credentials, store.readRefreshSession())
        val storedValues = dataStore.data.first().asMap().values.map(Any::toString)
        assertFalse(storedValues.contains("access-secret-value"))
        assertFalse(storedValues.contains("session-42"))
        assertFalse(storedValues.contains("refresh-secret-value"))

        store.clear()

        assertNull(store.readAccessToken())
        assertNull(store.readRefreshSession())
    }

    @Test
    fun `stale encrypted value is treated as signed out instead of crashing`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { tempDir.resolve("stale.preferences_pb").toFile() },
        )
        val writable = EncryptedSessionTokenStore(
            dataStore = dataStore,
            cipher = FakeCipher,
        )
        writable.writeAccessToken("token-before-key-change")

        val brokenReader = EncryptedSessionTokenStore(
            dataStore = dataStore,
            cipher = object : TokenCipher {
                override fun encrypt(plainText: String): EncryptedValue = FakeCipher.encrypt(plainText)

                override fun decrypt(value: EncryptedValue): String {
                    throw IllegalStateException("simulated stale keystore key")
                }
            },
        )

        assertNull(brokenReader.readAccessToken())
        assertNull(brokenReader.readAccessToken(), "failed encrypted state should be cleared")
    }

    @Test
    fun `write resets cipher and retries once after keystore failure`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { tempDir.resolve("recover.preferences_pb").toFile() },
        )
        val recoveringCipher = RecoveringCipher()
        val store = EncryptedSessionTokenStore(
            dataStore = dataStore,
            cipher = recoveringCipher,
        )

        store.writeAccessToken("fresh-token")

        assertEquals(1, recoveringCipher.resetCount)
        assertEquals(2, recoveringCipher.encryptAttempts)
        assertEquals("fresh-token", store.readAccessToken())
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

    private class RecoveringCipher : TokenCipher {
        var encryptAttempts: Int = 0
        var resetCount: Int = 0

        override fun encrypt(plainText: String): EncryptedValue {
            encryptAttempts += 1
            if (encryptAttempts == 1) {
                throw IllegalStateException("simulated invalid keystore key")
            }
            return EncryptedValue(
                ciphertext = plainText.reversed(),
                initializationVector = "new-iv",
            )
        }

        override fun decrypt(value: EncryptedValue): String = value.ciphertext.reversed()

        override fun reset() {
            resetCount += 1
        }
    }
}
