package org.companerodeescuela.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-GCM cipher whose key is generated and retained by AndroidKeyStore.
 */
internal class AndroidKeystoreTokenCipher : TokenCipher {

    override fun encrypt(plainText: String): EncryptedValue {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        return EncryptedValue(
            ciphertext = Base64.encodeToString(
                cipher.doFinal(plainText.toByteArray(Charsets.UTF_8)),
                Base64.NO_WRAP,
            ),
            initializationVector = Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
        )
    }

    override fun decrypt(value: EncryptedValue): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = Base64.decode(value.initializationVector, Base64.NO_WRAP)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv),
        )
        val encrypted = Base64.decode(value.ciphertext, Base64.NO_WRAP)
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        return KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEY_STORE,
        ).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE_BITS)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "companero.session.aes.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        const val KEY_SIZE_BITS = 256
    }
}
