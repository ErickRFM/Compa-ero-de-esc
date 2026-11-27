package org.companerodeescuela.api.security

import kotlin.test.*

class MfaSecretCipherTest {
    private val key = ByteArray(32) { (it + 3).toByte() }

    @Test fun `factor is encrypted independently with actor and factor binding`() {
        val cipher = MfaSecretCipher(key)
        val secret = ByteArray(20) { (it + 10).toByte() }
        val a = cipher.encrypt("actor", "factor", secret)
        val b = cipher.encrypt("actor", "factor", secret)
        assertNotEquals(a, b)
        assertContentEquals(secret, cipher.decrypt("actor", "factor", a))
        assertFalse(a.contains(Totp.encodeSecret(secret)))
    }

    @Test fun `substituted actor factor ciphertext or key fails authentication`() {
        val encrypted = MfaSecretCipher(key).encrypt("actor", "factor", ByteArray(20) { 12 })
        assertFails { MfaSecretCipher(key).decrypt("other", "factor", encrypted) }
        assertFails { MfaSecretCipher(key).decrypt("actor", "other", encrypted) }
        assertFails { MfaSecretCipher(ByteArray(32) { 21 }).decrypt("actor", "factor", encrypted) }
        assertFails { MfaSecretCipher(key).decrypt("actor", "factor", encrypted.dropLast(2) + "AB") }
    }

    @Test fun `key material is bounded and never shown by cipher`() {
        assertFailsWith<IllegalArgumentException> { MfaSecretCipher(ByteArray(16)) }
        assertFailsWith<IllegalArgumentException> { MfaSecretCipher(key).encrypt("actor", "factor", ByteArray(19)) }
        assertFailsWith<IllegalArgumentException> { MfaSecretCipher(key).encrypt("actor", "factor", ByteArray(65)) }
        assertEquals("MfaSecretCipher([redacted])", MfaSecretCipher(key).toString())
    }

    @Test fun `bounded cipher encrypts and decrypts short and long outbox recipients`() {
        val cipher = MfaSecretCipher(key)
        val samples =
            listOf(
                "a@b.cd",
                "erik55509@mail.test",
                "coordinacion.ingenieria.posgrado@universidad-politecnica-tlaxcala.edu.test",
            )
        for (sample in samples) {
            val bytes = sample.toByteArray(Charsets.UTF_8)
            val encrypted =
                cipher.encryptBounded("inv-1", "platform-invitation-outbox-recipient", bytes, minBytes = 1, maxBytes = 256)
            val decrypted =
                cipher.decryptBounded("inv-1", "platform-invitation-outbox-recipient", encrypted, minBytes = 1, maxBytes = 256)
            assertContentEquals(bytes, decrypted)
            assertFails {
                cipher.decryptBounded("other-inv", "platform-invitation-outbox-recipient", encrypted, minBytes = 1, maxBytes = 256)
            }
        }
    }
}
