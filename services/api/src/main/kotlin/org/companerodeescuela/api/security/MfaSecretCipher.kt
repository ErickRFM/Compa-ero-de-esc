package org.companerodeescuela.api.security

import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Random-IV AES256-GCM; a factor cannot be transplanted to another identity or enrollment. */
class MfaSecretCipher(key: ByteArray) {
    private val secretKey=SecretKeySpec(key.copyOf().also { require(it.size==32) },"AES")
    private val random=SecureRandom()
    fun encrypt(actorId: String, factorId: String, secret: ByteArray): String {
        require(secret.size in 20..64)
        val iv=ByteArray(12).also(random::nextBytes)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,secretKey,GCMParameterSpec(128,iv))
        cipher.updateAAD(binding(actorId,factorId))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(iv+cipher.doFinal(secret))
    }
    fun decrypt(actorId: String, factorId: String, encrypted: String): ByteArray {
        require(encrypted.length in 64..123 && Regex("[A-Za-z0-9_-]+").matches(encrypted))
        val packed=Base64.getUrlDecoder().decode(encrypted)
        require(packed.size in 48..92 && Base64.getUrlEncoder().withoutPadding().encodeToString(packed)==encrypted)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,secretKey,GCMParameterSpec(128,packed.copyOfRange(0,12)))
        cipher.updateAAD(binding(actorId,factorId))
        return cipher.doFinal(packed.copyOfRange(12,packed.size)).also { require(it.size in 20..64) }
    }
    private fun binding(actorId: String,factorId: String):ByteArray {
        require(actorId.isNotBlank() && actorId.length<=128 && factorId.isNotBlank() && factorId.length<=128)
        val actor=actorId.toByteArray(Charsets.UTF_8);val factor=factorId.toByteArray(Charsets.UTF_8)
        return ByteBuffer.allocate(12+actor.size+factor.size).putInt(1).putInt(actor.size).put(actor).putInt(factor.size).put(factor).array()
    }
    override fun toString(): String = "MfaSecretCipher([redacted])"
}
