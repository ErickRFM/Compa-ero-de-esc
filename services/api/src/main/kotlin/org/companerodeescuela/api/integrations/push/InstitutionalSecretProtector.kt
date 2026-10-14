package org.companerodeescuela.api.integrations.push

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Encrypts institution HMAC keys at rest using a deployment-managed AES-256 key. */
class InstitutionalSecretProtector(
    masterKey: ByteArray,
) {
    private val key = masterKey.copyOf()

    init {
        require(key.size == AES_256_KEY_BYTES) { "Institution integration encryption key must contain 32 bytes" }
    }

    fun encrypt(secret: ByteArray): String {
        require(secret.size >= MIN_SECRET_BYTES)
        val iv = ByteArray(GCM_IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(secret)
        return "v1." + Base64.getUrlEncoder().withoutPadding().encodeToString(iv + ciphertext)
    }

    fun decrypt(encoded: String): ByteArray {
        require(encoded.startsWith("v1.")) { "Unsupported institution secret format" }
        val packed = Base64.getUrlDecoder().decode(encoded.removePrefix("v1."))
        require(packed.size > GCM_IV_BYTES + GCM_TAG_BITS / 8)
        val iv = packed.copyOfRange(0, GCM_IV_BYTES)
        val ciphertext = packed.copyOfRange(GCM_IV_BYTES, packed.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private companion object {
        const val AES_256_KEY_BYTES = 32
        const val MIN_SECRET_BYTES = 32
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
        val random = SecureRandom()
    }
}
