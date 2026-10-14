package org.companerodeescuela.api.config

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class InstitutionalIntegrationSettingsTest {
    @Test
    fun encryptionKeyRequiresCanonicalBase64UrlAnd32DistinctBytes() {
        val key = ByteArray(32) { it.toByte() }
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(key)
        val settings =
            loader(
                "API_PORT" to "8080",
                "MONGODB_DATABASE" to "c",
                "INSTITUTION_INTEGRATION_ENCRYPTION_KEY" to encoded,
            ).load()

        assertContentEquals(key, settings.institutionIntegrationEncryptionKey)
        assertFailsWith<ConfigurationException> {
            loader(
                "API_PORT" to "8080",
                "MONGODB_DATABASE" to "c",
                "INSTITUTION_INTEGRATION_ENCRYPTION_KEY" to "short",
            ).load()
        }
        assertFailsWith<ConfigurationException> {
            loader(
                "API_PORT" to "8080",
                "MONGODB_DATABASE" to "c",
                "MFA_ENCRYPTION_KEY" to encoded,
                "INSTITUTION_INTEGRATION_ENCRYPTION_KEY" to encoded,
            ).load()
        }
    }

    private fun loader(vararg values: Pair<String, String>): SettingsLoader {
        val provided = values.toMap()
        val map = provided + ("APP_ENV" to "local")
        return SettingsLoader(env = { map[it] })
    }
}
