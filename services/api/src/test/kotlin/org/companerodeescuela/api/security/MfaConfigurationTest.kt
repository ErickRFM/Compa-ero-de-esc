package org.companerodeescuela.api.security

import java.util.Base64
import kotlin.test.*
import org.companerodeescuela.api.config.*

class MfaConfigurationTest {
    private fun load(key:String?=null):ApiSettings {
        val values=mapOf("APP_ENV" to "local", "API_PORT" to "8080","MONGODB_DATABASE" to "v12_test", "MFA_ENCRYPTION_KEY" to key)
        return SettingsLoader { values[it] }.load()
    }
    @Test fun `optional absent key disables MFA while configured canonical AES256 key is decoded privately`() {
        assertNull(load().mfaEncryptionKey)
        val bytes=ByteArray(32){it.toByte()};val encoded=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val configured=load(encoded)
        assertContentEquals(bytes,assertNotNull(configured.mfaEncryptionKey))
        assertFalse(configured.toString().contains(encoded))
        assertNotEquals(load(),configured)
    }
    @Test fun `malformed partial padded or wrong size MFA key aborts startup without exposing value`() {
        for(key in listOf(""," ","secret-do-not-log", "a".repeat(43),Base64.getUrlEncoder().encodeToString(ByteArray(32)),Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(31)))) {
            val error=assertFailsWith<ConfigurationException> { load(key) }
            if(key.isNotBlank()) assertFalse(error.message.orEmpty().contains(key))
        }
    }
}
