package org.companerodeescuela.api.config

import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class SettingsLoaderTest {

    private fun loader(vararg values: Pair<String, String>): SettingsLoader {
        val map = values.toMap()
        return SettingsLoader(env = { map[it] })
    }

    @Test
    @DisplayName("Local defaults apply when only the port is set")
    fun loadsWithMinimalEnvironment() {
        val settings = loader("API_PORT" to "8080", "MONGODB_DATABASE" to "companero").load()

        assertEquals(Environment.LOCAL, settings.environment)
        assertEquals("0.0.0.0", settings.host)
        assertEquals(8080, settings.port)
        assertEquals("companero-api", settings.serviceName)
        assertEquals("v1", settings.apiVersion)
        assertFalse(settings.mongo.isConfigured)
        assertFalse(settings.hasAuthentication)
    }

    @Test
    @DisplayName("An invalid port aborts startup instead of defaulting")
    fun rejectsInvalidPort() {
        val error = assertFailsWith<ConfigurationException> {
            loader("API_PORT" to "not-a-port", "MONGODB_DATABASE" to "companero").load()
        }
        assertContains(error.message!!, "API_PORT")
    }

    @Test
    @DisplayName("A missing database name aborts startup")
    fun rejectsMissingDatabaseName() {
        val error = assertFailsWith<ConfigurationException> {
            loader("API_PORT" to "8080").load()
        }
        assertContains(error.message!!, "MONGODB_DATABASE")
    }

    @Test
    @DisplayName("An unknown APP_ENV is rejected rather than silently downgraded")
    fun rejectsUnknownEnvironment() {
        val error = assertFailsWith<ConfigurationException> {
            loader("APP_ENV" to "prodution", "API_PORT" to "8080", "MONGODB_DATABASE" to "c").load()
        }
        assertContains(error.message!!, "APP_ENV")
    }

    @Test
    @DisplayName("Production requires a JWT secret")
    fun productionRequiresSecret() {
        val error = assertFailsWith<ConfigurationException> {
            loader("APP_ENV" to "production", "API_PORT" to "8080", "MONGODB_DATABASE" to "c").load()
        }
        assertContains(error.message!!, "JWT_SECRET")
    }

    @Test
    @DisplayName("A short JWT secret is rejected in every environment")
    fun rejectsShortSecret() {
        val error = assertFailsWith<ConfigurationException> {
            loader(
                "API_PORT" to "8080",
                "MONGODB_DATABASE" to "c",
                "JWT_SECRET" to "too-short",
            ).load()
        }
        assertContains(error.message!!, "JWT_SECRET")
    }

    @Test
    @DisplayName("A valid secret enables authentication and is not exposed in toString")
    fun secretIsNotLogged() {
        val secret = "s".repeat(40)
        val settings = loader(
            "API_PORT" to "8080",
            "MONGODB_DATABASE" to "companero",
            "MONGODB_URI" to "mongodb+srv://user:pass@cluster.example/?retryWrites=true",
            "JWT_SECRET" to secret,
        ).load()

        assertTrue(settings.hasAuthentication)
        assertEquals(secret, settings.jwtSecret?.concatToString())

        val rendered = settings.toString()
        assertFalse(rendered.contains(secret), "toString must not contain the secret")
        assertFalse(rendered.contains("mongodb+srv"), "toString must not contain the URI")
        assertContains(rendered, "authenticationConfigured=true")
        assertContains(rendered, "mongoConfigured=true")
    }

    @Test
    @DisplayName("Absent JWT_SECRET yields null rather than an empty array")
    fun absentSecretIsNull() {
        val settings = loader("API_PORT" to "8080", "MONGODB_DATABASE" to "c").load()
        assertNull(settings.jwtSecret)
    }

    @Test
    @DisplayName("Mongo timeouts fall back to safe defaults")
    fun mongoTimeoutsDefault() {
        val settings = loader("API_PORT" to "8080", "MONGODB_DATABASE" to "c").load()

        assertEquals(MongoSettings.DEFAULT_CONNECT_TIMEOUT_MS, settings.mongo.connectTimeoutMs)
        assertEquals(
            MongoSettings.DEFAULT_SERVER_SELECTION_TIMEOUT_MS,
            settings.mongo.serverSelectionTimeoutMs,
        )
    }

    @Test
    @DisplayName("Settings with identical values are equal without comparing the secret")
    fun equalsIgnoresSecretContent() {
        val base = loader("API_PORT" to "8080", "MONGODB_DATABASE" to "c")
        val first = base.load()
        val second = base.load()

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }
}

class EnvironmentTest {

    @Test
    @DisplayName("Missing APP_ENV defaults to local")
    fun defaultsToLocal() {
        assertEquals(Environment.LOCAL, Environment.parse(null))
        assertEquals(Environment.LOCAL, Environment.parse("  "))
    }

    @Test
    @DisplayName("Common aliases are accepted")
    fun acceptsAliases() {
        assertEquals(Environment.LOCAL, Environment.parse("local"))
        assertEquals(Environment.DEVELOPMENT, Environment.parse("dev"))
        assertEquals(Environment.DEVELOPMENT, Environment.parse("development"))
        assertEquals(Environment.STAGING, Environment.parse("staging"))
        assertEquals(Environment.PRODUCTION, Environment.parse("PROD"))
    }

    @Test
    @DisplayName("Only production reports isProduction")
    fun isProduction() {
        assertTrue(Environment.PRODUCTION.isProduction)
        assertFalse(Environment.STAGING.isProduction)
        assertFalse(Environment.LOCAL.isProduction)
    }
}
