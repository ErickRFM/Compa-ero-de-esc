package org.companerodeescuela.api.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

data class PasswordVerificationResult(
    val verified: Boolean,
    val upgradedHash: String? = null,
)

open class PasswordHasher(
    private val random: SecureRandom = SecureRandom(),
) {
    open fun hash(password: String): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val digest = derive(password, salt, ITERATIONS)
        return listOf(
            PREFIX,
            ITERATIONS.toString(),
            Base64.getUrlEncoder().withoutPadding().encodeToString(salt),
            Base64.getUrlEncoder().withoutPadding().encodeToString(digest),
        ).joinToString(":")
    }

    open fun verify(password: String, encoded: String): Boolean {
        val parts = encoded.split(":")
        val iterations = encodedIterationCount(parts) ?: return false
        val salt = runCatching { Base64.getUrlDecoder().decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { Base64.getUrlDecoder().decode(parts[3]) }.getOrNull() ?: return false
        val actual = derive(password, salt, iterations)
        return MessageDigest.isEqual(expected, actual)
    }

    suspend fun hashAsync(password: String): String = limited { hash(password) }

    suspend fun verifyAsync(password: String, encoded: String): Boolean = limited { verify(password, encoded) }

    suspend fun verifyAndUpgradeAsync(password: String, encoded: String): PasswordVerificationResult = limited {
        val iterations = encodedIterationCount(encoded.split(":"))
            ?: return@limited PasswordVerificationResult(verified = false)
        if (!verify(password, encoded)) return@limited PasswordVerificationResult(verified = false)
        PasswordVerificationResult(
            verified = true,
            upgradedHash = if (iterations < ITERATIONS) hash(password) else null,
        )
    }

    private suspend fun <T> limited(block: () -> T): T = HASH_SEMAPHORE.withPermit {
        withContext(Dispatchers.Default) { block() }
    }

    private fun encodedIterationCount(parts: List<String>): Int? {
        if (parts.size != 4 || parts[0] != PREFIX) return null
        val iterations = parts[1].toIntOrNull() ?: return null
        return iterations.takeIf { it in MIN_ITERATIONS..MAX_ITERATIONS }
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private companion object {
        const val PREFIX = "pbkdf2-sha256"
        const val ITERATIONS = 600_000
        const val MIN_ITERATIONS = 1
        const val MAX_ITERATIONS = 2_000_000
        const val KEY_BITS = 256
        const val SALT_BYTES = 16
        val HASH_SEMAPHORE = Semaphore(4)
    }
}
