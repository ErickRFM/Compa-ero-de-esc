package org.companerodeescuela.api.auth

import kotlin.test.Test
import kotlin.test.assertFalse
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class PasswordHasherTest {
    @Test
    fun `hash is salted and verifies the original password`() {
        val hasher = PasswordHasher()
        val first = hasher.hash("Correct horse battery staple")
        val second = hasher.hash("Correct horse battery staple")

        assertNotEquals(first, second)
        assertTrue(hasher.verify("Correct horse battery staple", first))
        assertFalse(hasher.verify("wrong", first))
    }

    @Test
    fun `new hashes use six hundred thousand iterations`() {
        val encoded = PasswordHasher().hash("Correct horse battery staple")

        assertEquals(600_000, encoded.split(":")[1].toInt())
    }

    @Test
    fun `verification rejects iteration counts below the supported range`() {
        val hasher = PasswordHasher()
        val encoded = hasher.hash("Correct horse battery staple")
        val unsupported = encoded.replace(":600000:", ":0:")

        assertFalse(hasher.verify("Correct horse battery staple", unsupported))
    }

    @Test
    fun `verification rejects iteration counts above the supported range`() {
        val hasher = PasswordHasher()
        val encoded = hasher.hash("Correct horse battery staple")
        val unsupported = encoded.replace(":600000:", ":2000001:")

        assertFalse(hasher.verify("Correct horse battery staple", unsupported))
    }

    @Test
    fun `legacy hash verifies and requests a current hash`() = runTest {
        val password = "Correct horse battery staple"
        val oldHash = legacyHash(password, 210_000)

        val result = PasswordHasher().verifyAndUpgradeAsync(password, oldHash)

        assertTrue(result.verified)
        val upgraded = assertNotNull(result.upgradedHash)
        assertEquals(600_000, upgraded.split(":")[1].toInt())
        assertTrue(PasswordHasher().verifyAsync(password, upgraded))
    }

    @Test
    fun `fixed unknown account dummy hash is a valid current hash`() {
        val hash = "pbkdf2-sha256:600000:AAECAwQFBgcICQoLDA0ODw:yq8_JTOd2RABK-PjQsVxm5wklWj_j4ZQ2eTaDf-qxDI"
        assertTrue(PasswordHasher().verify("not-a-real-companero-account-password", hash))
    }

    @Test
    fun `asynchronous hashing and verification run on the default dispatcher`() = runTest {
        val hasher = ThreadCapturingPasswordHasher()
        val caller = Thread.currentThread().name
        val encoded = hasher.hashAsync("Correct horse battery staple")

        assertNotEquals(caller, hasher.lastHashThread)
        assertTrue(hasher.verifyAsync("Correct horse battery staple", encoded))
        assertNotEquals(caller, hasher.lastVerifyThread)
    }

    private fun legacyHash(password: String, iterations: Int): String {
        val salt = ByteArray(16) { it.toByte() }
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        val digest = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        val encoder = Base64.getUrlEncoder().withoutPadding()
        return listOf("pbkdf2-sha256", iterations.toString(), encoder.encodeToString(salt), encoder.encodeToString(digest)).joinToString(":")
    }

    private class ThreadCapturingPasswordHasher : PasswordHasher() {
        var lastHashThread: String? = null
        var lastVerifyThread: String? = null

        override fun hash(password: String): String {
            lastHashThread = Thread.currentThread().name
            return super.hash(password)
        }

        override fun verify(password: String, encoded: String): Boolean {
            lastVerifyThread = Thread.currentThread().name
            return super.verify(password, encoded)
        }
    }
}
